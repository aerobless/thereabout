package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.*;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Runs on the owning TDLib worker; cancellation and cached identities share its lifecycle. */
@Slf4j
@RequiredArgsConstructor
final class TelegramBackfill {
    private final TelegramSyncCheckpointRepository checkpointRepository;
    private final TelegramMessageMapper messageMapper;
    private final MessageRepository messageRepository;
    private final AtomicBoolean resyncCancelRequested;
    private final Map<Long, String> chatTitleCache;
    private final BiConsumer<SimpleTelegramClient, Long> resolveUser;
    private final Function<TdApi.Message, String> senderId;
    private final Function<TdApi.Message, String> senderHint;

    void backfillChat(SimpleTelegramClient client, TelegramConnectionEntity connection, long chatId, long throttleMs, int historyBatchSize) {
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
                            backfillChatHistory(client, connection, basicChat.id, throttleMs, historyBatchSize,
                                    chatTitle, String.valueOf(chatId), chatTitle, true, true);
                        }
                    }
                } catch (Exception e) {
                    if (resyncCancelRequested.get()) return;
                    log.debug("Could not backfill basic group history for supergroup {}: {}", chatId, e.getMessage());
                }
            }
            backfillChatHistory(client, connection, chatId, throttleMs, historyBatchSize, chatTitle, String.valueOf(chatId), chatTitle, receiverIsGroup, false);
        } catch (Exception e) {
            if (resyncCancelRequested.get()) return;
            log.warn("Backfill failed for chat {}: {}", chatId, e.getMessage());
        }
    }

    /** Backfill one chat's history; receiver id/title used for DB and mapper (same as chat when no override). */
    private void backfillChatHistory(SimpleTelegramClient client, TelegramConnectionEntity connection, long chatId,
                                     long throttleMs, int historyBatchSize, String chatTitle, String receiverIdForDb, String receiverTitleForDb, boolean receiverIsGroup, boolean upgraded) {
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
                    if (msg.senderId instanceof TdApi.MessageSenderUser u) resolveUser.accept(client, u.userId);
                }
                List<MessageEntity> batch = new ArrayList<>();
                for (TdApi.Message msg : messages.messages) {
                    String senderUserId = senderId.apply(msg);
                    String senderUsernameHint = senderHint.apply(msg);
                    MessageEntity entity = upgraded
                            ? messageMapper.toMessageEntityWithSourcePrefix(msg, "telegram-" + receiverIdForDb + "-b-", receiverIdForDb, receiverTitleForDb, true, senderUserId, senderUsernameHint)
                            : messageMapper.toMessageEntity(msg, receiverIdForDb, receiverIdForDb, receiverTitleForDb, receiverIsGroup, senderUserId, senderUsernameHint);
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

}
