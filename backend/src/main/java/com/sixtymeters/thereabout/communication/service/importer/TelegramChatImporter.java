package com.sixtymeters.thereabout.communication.service.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramChatImporter implements FileImporter {

    private static final DateTimeFormatter TELEGRAM_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int BATCH_SIZE = 1000;

    /** Chat types that use chat name as receiver; all others (e.g. personal_chat) use user-provided receiver. */
    private static final Set<String> GROUP_CHAT_TYPES = Set.of("private_group", "private_supergroup");

    private final ObjectMapper objectMapper;
    private final IdentityInApplicationRepository identityInApplicationRepository;
    private final IdentityRepository identityRepository;
    private final MessageRepository messageRepository;
    private final ImportProgressService importProgressService;

    @Override
    public GenImportType getSupportedImportType() {
        return GenImportType.TELEGRAM_CHAT;
    }

    @Override
    public void importFile(File file, String receiver) {
        log.info("Starting Telegram chat import from file: {}, receiver: {}", file.getName(), receiver);
        importProgressService.setProgress(1);

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(content);

            boolean multiChat = root.has("chats") && root.path("chats").path("list").isArray();
            Map<String, IdentityInApplicationEntity> identityCache = new HashMap<>();
            Set<String> knownSourceIds = new HashSet<>();
            List<MessageEntity> batch = new ArrayList<>(BATCH_SIZE);
            long totalImported = 0;
            long skippedDuplicates = 0;
            long totalMessages;
            long[] processedHolder = new long[]{0};

            if (multiChat) {
                JsonNode chatsList = root.path("chats").path("list");
                if (!chatsList.isArray() || chatsList.isEmpty()) {
                    log.info("Multi-chat export has empty chats.list, nothing to import.");
                    return;
                }
                totalMessages = countMessagesInChatsList(chatsList);
                log.info("Counted {} messages in {} chats (multi-chat file: {})", totalMessages, chatsList.size(), file.getName());

                boolean hasPersonalChat = hasAnyPersonalChat(chatsList);
                if (hasPersonalChat && (receiver == null || receiver.isBlank())) {
                    throw new ThereaboutException(HttpStatusCode.valueOf(400),
                            "Telegram export contains personal chats; please provide a receiver in the import form.");
                }

                for (JsonNode chatNode : chatsList) {
                    String effectiveReceiver = resolveReceiver(chatNode, receiver);
                    String chatId = chatIdFrom(chatNode);
                    long[] result = processOneChat(chatNode, effectiveReceiver, chatId, identityCache, knownSourceIds,
                            batch, totalMessages, processedHolder);
                    totalImported += result[0];
                    skippedDuplicates += result[1];
                }
            } else {
                JsonNode messagesNode = root.path("messages");
                if (!messagesNode.isArray()) {
                    throw new ThereaboutException(HttpStatusCode.valueOf(400),
                            "Telegram export JSON must contain a 'messages' array or a 'chats.list' array.");
                }
                totalMessages = messagesNode.size();
                log.info("Counted {} messages in Telegram chat file: {}", totalMessages, file.getName());

                boolean isPersonal = !isGroupChat(chatTypeFrom(root));
                if (isPersonal && (receiver == null || receiver.isBlank())) {
                    throw new ThereaboutException(HttpStatusCode.valueOf(400),
                            "Telegram personal chat import requires a receiver; please provide one in the import form.");
                }

                String effectiveReceiver = resolveReceiver(root, receiver);
                String chatId = chatIdFrom(root);
                long[] result = processOneChat(root, effectiveReceiver, chatId, identityCache, knownSourceIds,
                        batch, totalMessages, processedHolder);
                totalImported += result[0];
                skippedDuplicates += result[1];
            }

            log.info("Imported {} Telegram messages, skipped {} duplicates, {} participants.",
                    totalImported, skippedDuplicates, identityCache.size());
        } catch (IOException e) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400),
                    "Failed to read Telegram chat file '%s': %s".formatted(file.getName(), e.getMessage()));
        } finally {
            importProgressService.reset();
            cleanupTempFile(file);
        }

        log.info("Finished Telegram chat import from file: {}", file.getName());
    }

    private static long countMessagesInChatsList(JsonNode chatsList) {
        long total = 0;
        for (JsonNode chat : chatsList) {
            JsonNode messages = chat.path("messages");
            if (messages.isArray()) {
                total += messages.size();
            }
        }
        return total;
    }

    private static boolean hasAnyPersonalChat(JsonNode chatsList) {
        for (JsonNode chat : chatsList) {
            if (!isGroupChat(chatTypeFrom(chat))) {
                return true;
            }
        }
        return false;
    }

    private static String chatTypeFrom(JsonNode chatNode) {
        return chatNode.has("type") ? chatNode.path("type").asText() : "personal_chat";
    }

    private static boolean isGroupChat(String chatType) {
        return GROUP_CHAT_TYPES.contains(chatType);
    }

    /**
     * Resolve effective receiver for one chat: group chats use chat name, personal chats use user-provided receiver.
     */
    private static String resolveReceiver(JsonNode chatNode, String userProvidedReceiver) {
        String chatName = chatNode.has("name") ? chatNode.path("name").asText() : "";
        String chatType = chatTypeFrom(chatNode);
        if (isGroupChat(chatType)) {
            return chatName;
        }
        return userProvidedReceiver != null ? userProvidedReceiver : chatName;
    }

    private static String chatIdFrom(JsonNode chatNode) {
        JsonNode idNode = chatNode.path("id");
        return idNode.isNumber() ? String.valueOf(idNode.asLong()) : idNode.asText();
    }

    /**
     * Process one chat (root for single-chat or one element of chats.list for multi-chat).
     * Updates processedHolder and progress. Returns [imported count, skipped count] for this chat.
     */
    private long[] processOneChat(JsonNode chatNode, String effectiveReceiver, String chatId,
                                  Map<String, IdentityInApplicationEntity> identityCache,
                                  Set<String> knownSourceIds, List<MessageEntity> batch,
                                  long totalMessages, long[] processedHolder) {
        JsonNode messagesNode = chatNode.path("messages");
        if (!messagesNode.isArray()) {
            return new long[]{0, 0};
        }

        IdentityInApplicationEntity receiverEntity = getOrCreateReceiver(effectiveReceiver);
        long imported = 0;
        long skipped = 0;

        for (JsonNode msgNode : messagesNode) {
            String msgType = msgNode.has("type") ? msgNode.path("type").asText() : "message";

            if ("service".equals(msgType)) {
                MessageEntity serviceMessage = buildServiceMessage(msgNode, chatId, receiverEntity, identityCache);
                if (serviceMessage != null && !isDuplicate(serviceMessage.getSourceIdentifier(), knownSourceIds)) {
                    knownSourceIds.add(serviceMessage.getSourceIdentifier());
                    batch.add(serviceMessage);
                } else if (serviceMessage != null) {
                    skipped++;
                }
            } else {
                MessageEntity message = buildMessage(msgNode, chatId, receiverEntity, identityCache);
                if (message != null && !isDuplicate(message.getSourceIdentifier(), knownSourceIds)) {
                    knownSourceIds.add(message.getSourceIdentifier());
                    batch.add(message);
                } else if (message != null) {
                    skipped++;
                }
            }

            processedHolder[0]++;
            updateProgress(totalMessages, processedHolder[0]);

            if (batch.size() >= BATCH_SIZE) {
                imported += flushBatch(batch);
            }
        }

        if (!batch.isEmpty()) {
            imported += flushBatch(batch);
        }

        return new long[]{imported, skipped};
    }

    private MessageEntity buildMessage(JsonNode msg, String chatId, IdentityInApplicationEntity receiverEntity,
                                       Map<String, IdentityInApplicationEntity> identityCache) {
        if (!msg.has("from") || !msg.has("from_id")) {
            return null;
        }
        String from = msg.path("from").asText();
        String fromId = msg.path("from_id").asText();
        String senderIdentifier = from + "|" + fromId;

        LocalDateTime timestamp = parseDate(msg.path("date").asText());
        String body = extractText(msg);
        if (body.isEmpty() && msg.has("media_type")) {
            String mediaType = msg.path("media_type").asText();
            if ("photo".equals(mediaType)) {
                body = "[photo]";
            } else if ("voice_message".equals(mediaType)) {
                body = "[voice_message]";
            } else if ("sticker".equals(mediaType)) {
                body = "[sticker]";
            } else if (msg.has("file_name")) {
                body = "[file: " + msg.path("file_name").asText() + "]";
            } else {
                body = "[" + mediaType + "]";
            }
        }
        if (body.isEmpty() && msg.has("photo")) {
            body = "[photo]";
        }
        if (body.isEmpty() && msg.has("file_name")) {
            body = "[file: " + msg.path("file_name").asText() + "]";
        }
        if (body.isEmpty()) {
            body = "[media]";
        }

        String messageId = msg.has("id") ? String.valueOf(msg.path("id").asLong()) : "";
        String sourceIdentifier = "telegram-" + chatId + "-" + messageId;

        IdentityInApplicationEntity sender = getOrCreateIdentity(senderIdentifier, identityCache);

        return MessageEntity.builder()
                .type("text")
                .source(CommunicationApplication.TELEGRAM)
                .sourceIdentifier(sourceIdentifier)
                .sender(sender)
                .receiver(receiverEntity)
                .body(body)
                .timestamp(timestamp)
                .build();
    }

    private MessageEntity buildServiceMessage(JsonNode msg, String chatId, IdentityInApplicationEntity receiverEntity,
                                             Map<String, IdentityInApplicationEntity> identityCache) {
        if (!msg.has("actor") || !msg.has("actor_id")) {
            return null;
        }
        String actor = msg.path("actor").asText();
        String actorId = msg.path("actor_id").asText();
        String senderIdentifier = actor + "|" + actorId;

        LocalDateTime timestamp = parseDate(msg.path("date").asText());
        String action = msg.has("action") ? msg.path("action").asText() : "service";
        String body = "[" + action;
        if (msg.has("duration_seconds")) {
            body += " " + msg.path("duration_seconds").asInt() + "s";
        }
        body += "]";

        String messageId = msg.has("id") ? String.valueOf(msg.path("id").asLong()) : "";
        String sourceIdentifier = "telegram-" + chatId + "-" + messageId;

        IdentityInApplicationEntity sender = getOrCreateIdentity(senderIdentifier, identityCache);

        return MessageEntity.builder()
                .type("text")
                .source(CommunicationApplication.TELEGRAM)
                .sourceIdentifier(sourceIdentifier)
                .sender(sender)
                .receiver(receiverEntity)
                .body(body)
                .timestamp(timestamp)
                .build();
    }

    private String extractText(JsonNode msg) {
        JsonNode textNode = msg.path("text");
        if (textNode.isMissingNode()) {
            return "";
        }
        if (textNode.isTextual()) {
            return textNode.asText();
        }
        if (textNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : textNode) {
                if (part.has("text")) {
                    sb.append(part.path("text").asText());
                }
            }
            return sb.toString();
        }
        return "";
    }

    private LocalDateTime parseDate(String dateStr) {
        return LocalDateTime.parse(dateStr, TELEGRAM_DATE_FORMATTER);
    }

    private IdentityInApplicationEntity getOrCreateReceiver(String receiverName) {
        return identityRepository.findByShortName(receiverName)
                .map(identity -> identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, receiverName)
                        .orElseGet(() -> {
                            log.info("Creating Telegram app identity linked to identity: {}", receiverName);
                            IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                                    .application(CommunicationApplication.TELEGRAM)
                                    .identifier(receiverName)
                                    .identity(identity)
                                    .build();
                            return identityInApplicationRepository.save(entity);
                        }))
                .orElseGet(() -> identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, receiverName)
                        .orElseGet(() -> {
                            log.info("Creating new orphan application identity for Telegram receiver: {}", receiverName);
                            IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                                    .application(CommunicationApplication.TELEGRAM)
                                    .identifier(receiverName)
                                    .build();
                            return identityInApplicationRepository.save(entity);
                        }));
    }

    private IdentityInApplicationEntity getOrCreateIdentity(String identifier, Map<String, IdentityInApplicationEntity> cache) {
        return cache.computeIfAbsent(identifier, id -> identityInApplicationRepository
                .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, id)
                .orElseGet(() -> {
                    log.info("Creating new application identity for Telegram user: {}", id);
                    IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                            .application(CommunicationApplication.TELEGRAM)
                            .identifier(id)
                            .build();
                    return identityInApplicationRepository.save(entity);
                }));
    }

    private boolean isDuplicate(String sourceIdentifier, Set<String> knownSourceIds) {
        return knownSourceIds.contains(sourceIdentifier) || messageRepository.existsBySourceIdentifier(sourceIdentifier);
    }

    private void updateProgress(long totalMessages, long messagesProcessed) {
        if (totalMessages <= 0) {
            return;
        }
        int percentage = (int) ((messagesProcessed / (float) totalMessages) * 100);
        percentage = Math.max(percentage, 1);
        int previous = importProgressService.getProgress();
        importProgressService.setProgress(percentage);
        if (percentage != previous) {
            log.info("Imported {}% of Telegram messages.", percentage);
        }
    }

    private long flushBatch(List<MessageEntity> batch) {
        messageRepository.saveAll(batch);
        messageRepository.flush();
        long size = batch.size();
        log.info("Flushed batch of {} Telegram messages.", size);
        batch.clear();
        return size;
    }

    private void cleanupTempFile(File file) {
        try {
            java.nio.file.Files.deleteIfExists(file.toPath());
            java.nio.file.Files.deleteIfExists(file.toPath().getParent());
        } catch (IOException e) {
            log.warn("Failed to clean up temporary file: {}", file.getAbsolutePath(), e);
        }
    }
}
