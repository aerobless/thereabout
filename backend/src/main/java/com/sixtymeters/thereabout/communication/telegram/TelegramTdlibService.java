package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.*;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleAuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages TDLib client lifecycle, authorization, and message sync. Does not call openChat/viewMessages (no read receipts).
 */
@Slf4j
@Service
public class TelegramTdlibService {

    private static final int CHAT_LIST_LIMIT = 500;
    private static final int HISTORY_BATCH = 100;

    private final TelegramProperties properties;
    private final TelegramConnectionRepository connectionRepository;
    private final TelegramSyncCheckpointRepository checkpointRepository;
    private final TelegramMessageMapper messageMapper;
    private final MessageRepository messageRepository;

    private final AtomicReference<SimpleTelegramClient> clientRef = new AtomicReference<>();
    private final AtomicReference<SimpleTelegramClientFactory> factoryRef = new AtomicReference<>();
    private final ThereaboutClientInteraction clientInteraction = new ThereaboutClientInteraction();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "telegram-tdlib");
        t.setDaemon(false);
        return t;
    });
    private final Map<Long, String> chatTitleCache = new ConcurrentHashMap<>();
    private final Map<Long, String> userDisplayNameCache = new ConcurrentHashMap<>();

    public TelegramTdlibService(
            TelegramProperties properties,
            TelegramConnectionRepository connectionRepository,
            TelegramSyncCheckpointRepository checkpointRepository,
            TelegramMessageMapper messageMapper,
            MessageRepository messageRepository
    ) {
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.checkpointRepository = checkpointRepository;
        this.messageMapper = messageMapper;
        this.messageRepository = messageRepository;
    }

    public ThereaboutClientInteraction getClientInteraction() {
        return clientInteraction;
    }

    /** Start connecting with the given phone number. Session/token stored by TDLib only; password never stored. */
    public void connect(String phoneNumber) {
        if (!properties.isConfigured()) {
            log.warn("Telegram sync skipped: api_id/api_hash not set");
            return;
        }
        executor.execute(() -> doConnect(phoneNumber, false));
    }

    /** Resume an existing session (e.g. after app restart). Uses same DB path so TDLib restores session from disk. */
    public void resume(String phoneNumber) {
        if (!properties.isConfigured()) {
            log.warn("Telegram sync skipped: api_id/api_hash not set");
            return;
        }
        executor.execute(() -> doConnect(phoneNumber, true));
    }

    private void doConnect(String phoneNumber, boolean resuming) {
        try {
            updateConnectionStatus(resuming ? "CONNECTING" : "WAIT_CODE", phoneNumber);
            it.tdlight.Init.init();
            APIToken token = new APIToken(properties.getApiId(), properties.getApiHash());
            TDLibSettings settings = TDLibSettings.create(token);
            Path dbPath = properties.getDatabaseDirectory().toAbsolutePath();
            settings.setDatabaseDirectoryPath(dbPath.resolve("data"));
            settings.setDownloadedFilesDirectoryPath(dbPath.resolve("downloads"));
            settings.setMessageDatabaseEnabled(true);
            settings.setFileDatabaseEnabled(false);

            SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory();
            factoryRef.set(factory);
            SimpleTelegramClientBuilder builder = factory.builder(settings);
            builder.setClientInteraction(clientInteraction);

            builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::onAuthorizationState);
            builder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onNewMessage);

            SimpleAuthenticationSupplier<?> authSupplier = AuthenticationSupplier.user(phoneNumber);
            SimpleTelegramClient client = builder.build(authSupplier);
            clientRef.set(client);
            log.info("Telegram client built and running for phone {}", phoneNumber);
        } catch (Exception e) {
            log.error("Telegram connect failed", e);
            updateConnectionStatus("ERROR", phoneNumber);
        }
    }

    private void onAuthorizationState(TdApi.UpdateAuthorizationState update) {
        TdApi.AuthorizationState state = update.authorizationState;
        if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            // Only show WAIT_PHONE when user has not yet started connect. If we already have a phone
            // (user clicked Connect), we are about to get WaitCode; do not overwrite so the UI keeps showing the code input.
            connectionRepository.findFirstByOrderByIdAsc().ifPresent(conn -> {
                if (conn.getPhoneNumber() == null || conn.getPhoneNumber().isBlank()) {
                    updateConnectionStatus("WAIT_PHONE", null);
                }
            });
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            updateConnectionStatus("WAIT_CODE", null);
        } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            updateConnectionStatus("WAIT_PASSWORD", null);
        } else if (state instanceof TdApi.AuthorizationStateReady) {
            updateConnectionStatus("READY", null);
            executor.execute(this::runFullBackfill);
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
            updateConnectionStatus("DISCONNECTED", null);
            clientRef.set(null);
        }
    }

    /** Called from TDLib thread; enqueue to our executor so we don't block the callback thread (avoid deadlock on client.send().get()). */
    private void onNewMessage(TdApi.UpdateNewMessage update) {
        TdApi.Message msg = update.message;
        if (msg == null) return;
        executor.execute(() -> processNewMessage(update));
    }

    private void processNewMessage(TdApi.UpdateNewMessage update) {
        try {
            TdApi.Message msg = update.message;
            if (msg == null) return;
            SimpleTelegramClient client = clientRef.get();
            if (client == null) return;
            String chatIdStr = String.valueOf(msg.chatId);
            String receiverId = chatTitleCache.get(msg.chatId);
            if (receiverId == null) {
                resolveAndCacheChatTitle(client, msg.chatId);
                receiverId = chatTitleCache.get(msg.chatId);
            }
            if (receiverId == null) receiverId = "chat-" + msg.chatId;
            if (msg.senderId instanceof TdApi.MessageSenderUser userSender) {
                resolveAndCacheUser(client, userSender.userId);
            }
            String senderId = resolveSenderIdentifier(msg);
            MessageEntity entity = messageMapper.toMessageEntity(msg, chatIdStr, receiverId, senderId);
            if (entity != null) {
                messageRepository.save(entity);
                updateLastSyncTime();
            }
        } catch (Exception e) {
            log.warn("Failed to persist new Telegram message", e);
        }
    }

    private String resolveSenderIdentifier(TdApi.Message msg) {
        if (msg.senderId instanceof TdApi.MessageSenderUser userSender) {
            String name = userDisplayNameCache.get(userSender.userId);
            return (name != null ? name : "user") + "|" + userSender.userId;
        }
        return "unknown|0";
    }

    /** Synchronously resolve and cache user name (used during backfill and real-time so we persist with correct name). */
    private void resolveAndCacheUser(SimpleTelegramClient client, long userId) {
        if (userDisplayNameCache.containsKey(userId)) return;
        try {
            TdApi.User user = client.send(new TdApi.GetUser(userId)).get(30, TimeUnit.SECONDS);
            if (user != null) {
                String name = user.firstName != null ? user.firstName : "";
                if (user.lastName != null && !user.lastName.isEmpty()) name += " " + user.lastName;
                userDisplayNameCache.put(userId, name.trim().isEmpty() ? "user" : name.trim());
            }
        } catch (Exception e) {
            log.trace("Could not resolve user {}: {}", userId, e.getMessage());
            userDisplayNameCache.put(userId, "user");
        }
    }

    /** Synchronously resolve and cache chat title (so real-time messages have correct receiver name). */
    private void resolveAndCacheChatTitle(SimpleTelegramClient client, long chatId) {
        if (chatTitleCache.containsKey(chatId)) return;
        try {
            TdApi.Chat chat = client.send(new TdApi.GetChat(chatId)).get(30, TimeUnit.SECONDS);
            if (chat != null) {
                chatTitleCache.put(chatId, chat.title != null ? chat.title : ("chat-" + chatId));
            }
        } catch (Exception e) {
            log.trace("Could not resolve chat {}: {}", chatId, e.getMessage());
            chatTitleCache.put(chatId, "chat-" + chatId);
        }
    }

    private void fetchChatTitle(long chatId) {
        SimpleTelegramClient client = clientRef.get();
        if (client == null) return;
        client.send(new TdApi.GetChat(chatId))
                .whenComplete((chat, err) -> {
                    if (err == null && chat != null) {
                        chatTitleCache.put(chatId, chat.title != null ? chat.title : "chat-" + chatId);
                    }
                });
    }

    private void fetchUserName(long userId) {
        SimpleTelegramClient client = clientRef.get();
        if (client == null) return;
        client.send(new TdApi.GetUser(userId))
                .whenComplete((user, err) -> {
                    if (err == null && user != null) {
                        String name = user.firstName != null ? user.firstName : "";
                        if (user.lastName != null && !user.lastName.isEmpty()) name += " " + user.lastName;
                        if (name.isEmpty()) name = "user";
                        userDisplayNameCache.put(userId, name.trim().isEmpty() ? "user" : name.trim());
                    }
                });
    }

    /** Schedule a full backfill on the worker thread (e.g. for manual resync). */
    public void triggerResyncAsync() {
        executor.execute(this::runFullBackfill);
    }

    /** Full history backfill for all chats. Also used for manual resync. Does not mark messages as read. */
    public void runFullBackfill() {
        SimpleTelegramClient client = clientRef.get();
        if (client == null) return;
        TelegramConnectionEntity connection = connectionRepository.findFirstByOrderByIdAsc().orElse(null);
        if (connection == null) return;
        messageMapper.clearCache();
        try {
            TdApi.Chats chats = client.send(new TdApi.GetChats(new TdApi.ChatListMain(), CHAT_LIST_LIMIT)).get(2, TimeUnit.MINUTES);
            if (chats == null || chats.chatIds == null) return;
            for (long chatId : chats.chatIds) {
                backfillChat(client, connection, chatId);
            }
            updateLastSyncTime();
        } catch (Exception e) {
            log.error("Full backfill failed", e);
        }
    }

    private void backfillChat(SimpleTelegramClient client, TelegramConnectionEntity connection, long chatId) {
        try {
            TdApi.Chat chat = client.send(new TdApi.GetChat(chatId)).get(1, TimeUnit.MINUTES);
            String chatTitle = chat != null && chat.title != null ? chat.title : ("chat-" + chatId);
            chatTitleCache.put(chatId, chatTitle);
            long fromMessageId = 0;
            int total = 0;
            while (true) {
                TdApi.Messages messages = client.send(
                        new TdApi.GetChatHistory(chatId, fromMessageId, 0, HISTORY_BATCH, false)
                ).get(1, TimeUnit.MINUTES);
                if (messages == null || messages.messages == null || messages.messages.length == 0) break;
                for (TdApi.Message msg : messages.messages) {
                    if (msg.senderId instanceof TdApi.MessageSenderUser u) resolveAndCacheUser(client, u.userId);
                }
                List<MessageEntity> batch = new ArrayList<>();
                for (TdApi.Message msg : messages.messages) {
                    String senderId = resolveSenderIdentifier(msg);
                    MessageEntity entity = messageMapper.toMessageEntity(msg, String.valueOf(chatId), chatTitle, senderId);
                    if (entity != null) batch.add(entity);
                }
                if (!batch.isEmpty()) {
                    messageRepository.saveAll(batch);
                    total += batch.size();
                }
                long lastId = messages.messages[messages.messages.length - 1].id;
                fromMessageId = lastId;
                saveCheckpoint(connection, chatId, lastId, messages.messages.length < HISTORY_BATCH);
                if (messages.messages.length < HISTORY_BATCH) break;
            }
            if (total > 0) log.info("Backfilled {} messages for chat {}", total, chatTitle);
        } catch (Exception e) {
            log.warn("Backfill failed for chat {}: {}", chatId, e.getMessage());
        }
    }

    private void saveCheckpoint(TelegramConnectionEntity connection, long chatId, long lastMessageId, boolean complete) {
        TelegramSyncCheckpointEntity checkpoint = checkpointRepository.findByConnectionAndChatId(connection, chatId)
                .orElse(TelegramSyncCheckpointEntity.builder()
                        .connection(connection)
                        .chatId(chatId)
                        .build());
        checkpoint.setLastMessageId(lastMessageId);
        checkpoint.setBackfillComplete(complete);
        checkpointRepository.save(checkpoint);
    }

    private void updateConnectionStatus(String authStatus, String phoneNumber) {
        connectionRepository.findFirstByOrderByIdAsc().ifPresent(conn -> {
            conn.setAuthStatus(authStatus);
            if (phoneNumber != null) {
                conn.setPhoneNumber(phoneNumber);
            } else if ("DISCONNECTED".equals(authStatus)) {
                conn.setPhoneNumber(null);
            }
            conn.setEnabled("READY".equals(authStatus));
            connectionRepository.save(conn);
        });
    }

    private void updateLastSyncTime() {
        connectionRepository.findFirstByOrderByIdAsc().ifPresent(conn -> {
            conn.setLastSyncAt(Instant.now());
            connectionRepository.save(conn);
        });
    }

    public void provideCode(String code) {
        clientInteraction.provideCode(code);
    }

    public void providePassword(String password) {
        clientInteraction.providePassword(password);
    }

    public void disconnect() {
        SimpleTelegramClient client = clientRef.getAndSet(null);
        if (client != null) {
            try {
                client.sendClose();
            } catch (Exception e) {
                log.warn("Error closing Telegram client", e);
            }
        }
        updateConnectionStatus("DISCONNECTED", null);
    }

    @PreDestroy
    public void destroy() {
        disconnect();
        executor.shutdown();
    }
}
