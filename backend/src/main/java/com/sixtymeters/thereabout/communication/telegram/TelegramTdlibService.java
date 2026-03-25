package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.*;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages TDLib client lifecycle, authorization, and message sync. Does not call openChat/viewMessages (no read receipts).
 */
@Slf4j
@Service
public class TelegramTdlibService {

    private static final int CHAT_LIST_LIMIT = 500;
    private static final int LOAD_CHATS_BATCH = 200;
    private static final int MAX_LOAD_CHATS_ITERATIONS = 50;
    private static final int LOAD_CHATS_WAIT_MS = 800;

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
    private final Map<Long, Boolean> chatIsGroupCache = new ConcurrentHashMap<>();
    private final Map<Long, String> userDisplayNameCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> mainListChatIds = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Long, Long>> folderChatIds = new ConcurrentHashMap<>();
    private final Set<Integer> knownFolderIds = ConcurrentHashMap.newKeySet();

    private volatile String resyncStatus = "IDLE";
    private volatile int resyncProgress = 0;
    private final AtomicBoolean resyncCancelRequested = new AtomicBoolean(false);

    enum BackfillMode {
        RESUME,
        FORCE_REBUILD
    }

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
            builder.addUpdateHandler(TdApi.UpdateChatPosition.class, this::onChatPosition);
            builder.addUpdateHandler(TdApi.UpdateChatFolders.class, this::onChatFolders);

            AuthenticationSupplier<?> authSupplier = AuthenticationSupplier.user(phoneNumber);
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
            log.info("Telegram client is ready; waiting for explicit resync request before starting backfill");
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
            updateConnectionStatus("DISCONNECTED", null);
            clientRef.set(null);
        }
    }

    private void onChatPosition(TdApi.UpdateChatPosition update) {
        if (update.position == null || update.position.list == null) return;
        if (update.position.list instanceof TdApi.ChatListMain) {
            updateTrackedChatId(mainListChatIds, update.chatId, update.position.order);
            return;
        }
        if (update.position.list instanceof TdApi.ChatListFolder folderList) {
            knownFolderIds.add(folderList.chatFolderId);
            Map<Long, Long> folderChats = folderChatIds.computeIfAbsent(folderList.chatFolderId, ignored -> new ConcurrentHashMap<>());
            updateTrackedChatId(folderChats, update.chatId, update.position.order);
        }
    }

    private void onChatFolders(TdApi.UpdateChatFolders update) {
        knownFolderIds.clear();
        if (update.chatFolders != null) {
            for (TdApi.ChatFolderInfo chatFolder : update.chatFolders) {
                knownFolderIds.add(chatFolder.id);
                folderChatIds.computeIfAbsent(chatFolder.id, ignored -> new ConcurrentHashMap<>());
            }
        }
        folderChatIds.keySet().retainAll(knownFolderIds);
        log.info("TDLib reported {} chat folder(s) for backfill", knownFolderIds.size());
    }

    private void updateTrackedChatId(Map<Long, Long> chatIds, long chatId, long order) {
        if (order == 0) {
            chatIds.remove(chatId);
        } else {
            chatIds.put(chatId, order);
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
            String chatTitle = chatTitleCache.get(msg.chatId);
            if (chatTitle == null) {
                resolveAndCacheChatTitle(client, msg.chatId);
                chatTitle = chatTitleCache.get(msg.chatId);
            }
            String receiverTitle = chatTitle != null ? chatTitle : ("chat-" + msg.chatId);
            boolean receiverIsGroup = Boolean.TRUE.equals(chatIsGroupCache.get(msg.chatId));
            if (msg.senderId instanceof TdApi.MessageSenderUser userSender) {
                resolveAndCacheUser(client, userSender.userId);
            }
            String senderUserId = resolveSenderUserId(msg);
            String senderUsernameHint = resolveSenderUsernameHint(msg);
            MessageEntity entity = messageMapper.toMessageEntity(msg, chatIdStr, chatIdStr, receiverTitle, receiverIsGroup, senderUserId, senderUsernameHint);
            if (entity != null) {
                messageRepository.save(entity);
                updateLastSyncTime();
            }
        } catch (Exception e) {
            log.warn("Failed to persist new Telegram message", e);
        }
    }

    /** Telegram user ID as string for use as identifier; "0" for non-user senders (e.g. channel). */
    private String resolveSenderUserId(TdApi.Message msg) {
        if (msg.senderId instanceof TdApi.MessageSenderUser userSender) {
            return String.valueOf(userSender.userId);
        }
        return "0";
    }

    /** Display name for username_hint; null for non-user senders. */
    private String resolveSenderUsernameHint(TdApi.Message msg) {
        if (msg.senderId instanceof TdApi.MessageSenderUser userSender) {
            String name = userDisplayNameCache.get(userSender.userId);
            return name != null ? name : "user";
        }
        return "unknown";
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

    /** Synchronously resolve and cache chat title and is-group (so real-time messages have correct receiver name and group flag). */
    private void resolveAndCacheChatTitle(SimpleTelegramClient client, long chatId) {
        if (chatTitleCache.containsKey(chatId)) return;
        try {
            TdApi.Chat chat = client.send(new TdApi.GetChat(chatId)).get(30, TimeUnit.SECONDS);
            if (chat != null) {
                chatTitleCache.put(chatId, chat.title != null ? chat.title : ("chat-" + chatId));
                chatIsGroupCache.put(chatId, chat.type instanceof TdApi.ChatTypeBasicGroup || chat.type instanceof TdApi.ChatTypeSupergroup);
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
                        chatIsGroupCache.put(chatId, chat.type instanceof TdApi.ChatTypeBasicGroup || chat.type instanceof TdApi.ChatTypeSupergroup);
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
        executor.execute(() -> runFullBackfill(BackfillMode.FORCE_REBUILD));
    }

    public String getResyncStatus() {
        return resyncStatus;
    }

    public int getResyncProgress() {
        return resyncProgress;
    }

    /** Request cancellation of the current resync. The running backfill will stop after the current batch. */
    public void requestCancelResync() {
        resyncCancelRequested.set(true);
    }

    public void runFullBackfill() {
        runFullBackfill(BackfillMode.RESUME);
    }

    /** Full history backfill for all chats. Does not mark messages as read. */
    void runFullBackfill(BackfillMode mode) {
        SimpleTelegramClient client = clientRef.get();
        if (client == null) return;
        TelegramConnectionEntity connection = connectionRepository.findFirstByOrderByIdAsc().orElse(null);
        if (connection == null) return;
        resyncCancelRequested.set(false);
        resyncStatus = "IN_PROGRESS";
        resyncProgress = 0;
        messageMapper.clearCache();
        long throttleMs = Math.max(0, properties.getResyncThrottleMs());
        int historyBatchSize = Math.max(1, properties.getHistoryBatchSize());
        try {
            if (mode == BackfillMode.FORCE_REBUILD) {
                long deletedCheckpoints = checkpointRepository.deleteByConnection(connection);
                log.info("Starting Telegram backfill in {} mode after deleting {} checkpoint(s)", mode, deletedCheckpoints);
            } else {
                log.info("Starting Telegram backfill in {} mode", mode);
            }
            long[] chatIds = collectBackfillChatIds(client);
            log.info("Backfilling {} deduplicated chat(s) in {} mode", chatIds.length, mode);
            int totalChats = chatIds.length;
            if (totalChats == 0) {
                resyncStatus = "IDLE";
                return;
            }
            for (int i = 0; i < totalChats; i++) {
                if (resyncCancelRequested.get()) {
                    resyncStatus = "CANCELLED";
                    log.info("Resync cancelled by user");
                    return;
                }
                backfillChat(client, connection, chatIds[i], throttleMs, historyBatchSize);
                resyncProgress = (i + 1) * 100 / totalChats;
                if (i < totalChats - 1 && throttleMs > 0) {
                    try {
                        Thread.sleep(throttleMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        resyncStatus = "CANCELLED";
                        return;
                    }
                }
            }
            resyncStatus = "COMPLETE";
            resyncProgress = 100;
            updateLastSyncTime();
        } catch (Exception e) {
            log.error("Full backfill failed", e);
            resyncStatus = "ERROR";
        }
    }

    private long[] collectBackfillChatIds(SimpleTelegramClient client) {
        LinkedHashSet<Long> deduplicatedChatIds = new LinkedHashSet<>();
        deduplicateChatIdsInto(deduplicatedChatIds, loadChatIdsForList(client, new TdApi.ChatListMain(), "main", mainListChatIds));
        if (knownFolderIds.isEmpty()) {
            log.info("No Telegram folders known to TDLib for this session; backfill will use main chat list only");
        } else {
            knownFolderIds.stream()
                    .sorted()
                    .forEach(folderId -> deduplicateChatIdsInto(
                            deduplicatedChatIds,
                            loadChatIdsForList(
                                    client,
                                    new TdApi.ChatListFolder(folderId),
                                    "folder-" + folderId,
                                    folderChatIds.computeIfAbsent(folderId, ignored -> new ConcurrentHashMap<>())
                            )
                    ));
        }
        return deduplicatedChatIds.stream().mapToLong(Long::longValue).toArray();
    }

    private void deduplicateChatIdsInto(Set<Long> deduplicatedChatIds, long[] chatIds) {
        for (long chatId : chatIds) {
            deduplicatedChatIds.add(chatId);
        }
    }

    private long[] loadChatIdsForList(SimpleTelegramClient client, TdApi.ChatList chatList, String listLabel, Map<Long, Long> trackedChatIds) {
        trackedChatIds.clear();
        for (int iter = 0; iter < MAX_LOAD_CHATS_ITERATIONS; iter++) {
            if (resyncCancelRequested.get()) {
                resyncStatus = "CANCELLED";
                log.info("Resync cancelled by user");
                return new long[0];
            }
            Object result;
            try {
                result = client.send(new TdApi.LoadChats(chatList, LOAD_CHATS_BATCH)).get(2, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("LoadChats failed for {} list, using collected chats so far: {}", listLabel, e.getMessage());
                break;
            }
            if (result instanceof TdApi.Error error) {
                if (error.code == 404) {
                    break;
                }
                log.warn("LoadChats returned error {} for {} list: {}", error.code, listLabel, error.message);
                break;
            }
            if (LOAD_CHATS_WAIT_MS > 0) {
                try {
                    Thread.sleep(LOAD_CHATS_WAIT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    resyncStatus = "CANCELLED";
                    return new long[0];
                }
            }
        }
        long[] chatIds = trackedChatIds.isEmpty()
                ? loadChatIdsFallback(client, chatList, listLabel)
                : trackedChatIds.entrySet().stream()
                        .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                        .mapToLong(Map.Entry::getKey)
                        .toArray();
        log.info("Collected {} chat(s) from {} list", chatIds.length, listLabel);
        return chatIds;
    }

    private long[] loadChatIdsFallback(SimpleTelegramClient client, TdApi.ChatList chatList, String listLabel) {
        try {
            TdApi.Chats chats = client.send(new TdApi.GetChats(chatList, CHAT_LIST_LIMIT)).get(2, TimeUnit.MINUTES);
            if (chats != null && chats.chatIds != null && chats.chatIds.length > 0) {
                log.info("Loaded {} chat(s) from {} list via GetChats fallback", chats.chatIds.length, listLabel);
                return chats.chatIds;
            }
        } catch (Exception e) {
            log.warn("GetChats fallback failed for {} list: {}", listLabel, e.getMessage());
        }
        return new long[0];
    }

    private void backfillChat(SimpleTelegramClient client, TelegramConnectionEntity connection, long chatId, long throttleMs, int historyBatchSize) {
        try {
            TdApi.Chat chat = client.send(new TdApi.GetChat(chatId)).get(1, TimeUnit.MINUTES);
            String chatTitle = chat != null && chat.title != null ? chat.title : ("chat-" + chatId);
            chatTitleCache.put(chatId, chatTitle);
            boolean receiverIsGroup = chat != null && (chat.type instanceof TdApi.ChatTypeBasicGroup || chat.type instanceof TdApi.ChatTypeSupergroup);
            if (chat != null && chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) {
                long supergroupId = supergroupType.supergroupId;
                try {
                    TdApi.SupergroupFullInfo fullInfo = client.send(new TdApi.GetSupergroupFullInfo(supergroupId)).get(1, TimeUnit.MINUTES);
                    if (fullInfo != null && fullInfo.upgradedFromBasicGroupId != 0) {
                        TdApi.Chat basicChat = client.send(new TdApi.CreateBasicGroupChat(fullInfo.upgradedFromBasicGroupId, true)).get(1, TimeUnit.MINUTES);
                        if (basicChat != null) {
                            backfillChatHistoryWithReceiverOverride(client, connection, basicChat.id, throttleMs, historyBatchSize,
                                    String.valueOf(chatId), chatTitle);
                        }
                    }
                } catch (Exception e) {
                    if (resyncCancelRequested.get()) return;
                    log.debug("Could not backfill basic group history for supergroup {}: {}", chatId, e.getMessage());
                }
            }
            backfillChatHistory(client, connection, chatId, throttleMs, historyBatchSize, chatTitle, String.valueOf(chatId), chatTitle, receiverIsGroup);
        } catch (Exception e) {
            if (resyncCancelRequested.get()) return;
            log.warn("Backfill failed for chat {}: {}", chatId, e.getMessage());
        }
    }

    /** Backfill one chat's history; receiver id/title used for DB and mapper (same as chat when no override). */
    private void backfillChatHistory(SimpleTelegramClient client, TelegramConnectionEntity connection, long chatId,
                                     long throttleMs, int historyBatchSize, String chatTitle, String receiverIdForDb, String receiverTitleForDb, boolean receiverIsGroup) {
        try {
            var checkpointOpt = checkpointRepository.findByConnectionAndChatId(connection, chatId);
            if (checkpointOpt.map(TelegramSyncCheckpointEntity::getBackfillComplete).orElse(false)) {
                log.debug("Skipping chat {} because checkpoint is already complete", chatId);
                return;
            }
            long fromMessageId = checkpointOpt.map(cp -> cp.getLastMessageId() != null ? cp.getLastMessageId() : 0L).orElse(0L);
            int total = 0;
            while (true) {
                if (resyncCancelRequested.get()) return;
                TdApi.Messages messages = client.send(
                        new TdApi.GetChatHistory(chatId, fromMessageId, 0, historyBatchSize, false)
                ).get(1, TimeUnit.MINUTES);
                if (messages == null || messages.messages == null || messages.messages.length == 0) {
                    saveCheckpoint(connection, chatId, fromMessageId, true);
                    log.info("Completed backfill for chat {} after reaching empty history response", chatTitle);
                    break;
                }
                for (TdApi.Message msg : messages.messages) {
                    if (msg.senderId instanceof TdApi.MessageSenderUser u) resolveAndCacheUser(client, u.userId);
                }
                List<MessageEntity> batch = new ArrayList<>();
                for (TdApi.Message msg : messages.messages) {
                    String senderUserId = resolveSenderUserId(msg);
                    String senderUsernameHint = resolveSenderUsernameHint(msg);
                    MessageEntity entity = messageMapper.toMessageEntity(msg, receiverIdForDb, receiverIdForDb, receiverTitleForDb, receiverIsGroup, senderUserId, senderUsernameHint);
                    if (entity != null) batch.add(entity);
                }
                if (!batch.isEmpty()) {
                    messageRepository.saveAll(batch);
                    total += batch.size();
                }
                long previousFromMessageId = fromMessageId;
                long lastId = messages.messages[messages.messages.length - 1].id;
                boolean progressed = previousFromMessageId == 0 || lastId < previousFromMessageId;
                saveCheckpoint(connection, chatId, lastId, !progressed);
                if (!progressed) {
                    log.info("Completed backfill for chat {} because history pagination stopped progressing at message {}", chatTitle, lastId);
                    break;
                }
                if (messages.messages.length < historyBatchSize) {
                    log.debug("Continuing backfill for chat {} after short batch of {} messages", chatTitle, messages.messages.length);
                }
                fromMessageId = lastId;
                if (throttleMs > 0) {
                    try {
                        Thread.sleep(throttleMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Resync interrupted", e);
                    }
                }
            }
            if (total > 0) log.info("Backfilled {} messages for chat {}", total, chatTitle);
        } catch (Exception e) {
            if (resyncCancelRequested.get()) return;
            throw new RuntimeException(e);
        }
    }

    /** Backfill basic group history storing messages under supergroup receiver (sourceIdentifier: telegram-{receiverId}-b-{msgId}). */
    private void backfillChatHistoryWithReceiverOverride(SimpleTelegramClient client, TelegramConnectionEntity connection,
                                                          long basicChatId, long throttleMs, int historyBatchSize,
                                                          String receiverIdForDb, String receiverTitleForDb) {
        try {
            var checkpointOpt = checkpointRepository.findByConnectionAndChatId(connection, basicChatId);
            if (checkpointOpt.map(TelegramSyncCheckpointEntity::getBackfillComplete).orElse(false)) {
                log.debug("Skipping upgraded basic group {} because checkpoint is already complete", basicChatId);
                return;
            }
            long fromMessageId = checkpointOpt.map(cp -> cp.getLastMessageId() != null ? cp.getLastMessageId() : 0L).orElse(0L);
            int total = 0;
            while (true) {
                if (resyncCancelRequested.get()) return;
                TdApi.Messages messages = client.send(
                        new TdApi.GetChatHistory(basicChatId, fromMessageId, 0, historyBatchSize, false)
                ).get(1, TimeUnit.MINUTES);
                if (messages == null || messages.messages == null || messages.messages.length == 0) {
                    saveCheckpoint(connection, basicChatId, fromMessageId, true);
                    log.info("Completed upgraded basic-group backfill for receiver {} after reaching empty history response", receiverIdForDb);
                    break;
                }
                for (TdApi.Message msg : messages.messages) {
                    if (msg.senderId instanceof TdApi.MessageSenderUser u) resolveAndCacheUser(client, u.userId);
                }
                String sourceIdPrefix = "telegram-" + receiverIdForDb + "-b-";
                List<MessageEntity> batch = new ArrayList<>();
                for (TdApi.Message msg : messages.messages) {
                    String senderUserId = resolveSenderUserId(msg);
                    String senderUsernameHint = resolveSenderUsernameHint(msg);
                    MessageEntity entity = messageMapper.toMessageEntityWithSourcePrefix(msg, sourceIdPrefix, receiverIdForDb, receiverTitleForDb, true, senderUserId, senderUsernameHint);
                    if (entity != null) batch.add(entity);
                }
                if (!batch.isEmpty()) {
                    messageRepository.saveAll(batch);
                    total += batch.size();
                }
                long previousFromMessageId = fromMessageId;
                long lastId = messages.messages[messages.messages.length - 1].id;
                boolean progressed = previousFromMessageId == 0 || lastId < previousFromMessageId;
                saveCheckpoint(connection, basicChatId, lastId, !progressed);
                if (!progressed) {
                    log.info("Completed upgraded basic-group backfill for receiver {} because history pagination stopped progressing at message {}", receiverIdForDb, lastId);
                    break;
                }
                if (messages.messages.length < historyBatchSize) {
                    log.debug("Continuing upgraded basic-group backfill for receiver {} after short batch of {} messages", receiverIdForDb, messages.messages.length);
                }
                fromMessageId = lastId;
                if (throttleMs > 0) {
                    try {
                        Thread.sleep(throttleMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Resync interrupted", e);
                    }
                }
            }
            if (total > 0) log.info("Backfilled {} pre-upgrade messages for supergroup (receiver {})", total, receiverIdForDb);
        } catch (Exception e) {
            if (resyncCancelRequested.get()) return;
            throw new RuntimeException(e);
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
        closeClientQuietly();
        updateConnectionStatus("DISCONNECTED", null);
    }

    /**
     * Close the TDLib client without changing DB state. Used on JVM/container shutdown so startup can still
     * resume from {@link TelegramConnectionStartupRunner} when auth was READY.
     */
    private void closeClientQuietly() {
        SimpleTelegramClient client = clientRef.getAndSet(null);
        if (client != null) {
            try {
                client.sendClose();
            } catch (Exception e) {
                log.warn("Error closing Telegram client", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        closeClientQuietly();
        executor.shutdown();
    }
}
