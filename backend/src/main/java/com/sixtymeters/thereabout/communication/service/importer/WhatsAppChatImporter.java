package com.sixtymeters.thereabout.communication.service.importer;

import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppChatImporter implements FileImporter {

    private static final Pattern MESSAGE_PATTERN = Pattern.compile(
            "^\\[(\\d{2}\\.\\d{2}\\.\\d{4}), (\\d{2}:\\d{2}:\\d{2})] ([^:]+): ?(.*)$"
    );
    // Unicode formatting characters that WhatsApp prepends to some lines (e.g. LRM before "image omitted")
    private static final Pattern LEADING_UNICODE_FORMATTING = Pattern.compile(
            "^[\\u200E\\u200F\\u200B\\u200C\\u200D\\uFEFF]+"
    );
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss");
    private static final int BATCH_SIZE = 1000;

    private final IdentityInApplicationRepository identityInApplicationRepository;
    private final IdentityRepository identityRepository;
    private final MessageRepository messageRepository;
    private final ImportProgressService importProgressService;

    @Override
    public GenImportType getSupportedImportType() {
        return GenImportType.WHATSAPP_CHAT;
    }

    @Override
    public void importFile(File file, String receiver) {
        log.info("Starting WhatsApp chat import from file: {}, receiver: {}", file.getName(), receiver);
        importProgressService.setProgress(1);

        try {
            long totalMessages = countMessages(file);
            log.info("Counted {} messages in WhatsApp chat file: {}", totalMessages, file.getName());

            IdentityInApplicationEntity receiverEntity = getOrCreateReceiver(receiver);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                processFile(reader, totalMessages, receiverEntity);
            }
        } catch (IOException e) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400),
                    "Failed to read WhatsApp chat file '%s': %s".formatted(file.getName(), e.getMessage()));
        } finally {
            importProgressService.reset();
            cleanupTempFile(file);
        }

        log.info("Finished WhatsApp chat import from file: {}", file.getName());
    }

    /**
     * Fast first pass: count the number of message lines (lines matching the message pattern)
     * to use as the total for percentage calculation.
     */
    private long countMessages(File file) throws IOException {
        long count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = LEADING_UNICODE_FORMATTING.matcher(line).replaceFirst("");
                if (MESSAGE_PATTERN.matcher(line).matches()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Resolve the receiver: if an Identity with that shortName exists, use or create an IdentityInApplication
     * linked to it; otherwise use or create an orphan IdentityInApplication.
     */
    private IdentityInApplicationEntity getOrCreateReceiver(String receiverName) {
        return identityRepository.findByShortName(receiverName)
                .map(identity -> identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, receiverName)
                        .orElseGet(() -> {
                            log.info("Creating WhatsApp app identity linked to identity: {}", receiverName);
                            IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                                    .application(CommunicationApplication.WHATSAPP)
                                    .identifier(receiverName)
                                    .identity(identity)
                                    .build();
                            return identityInApplicationRepository.save(entity);
                        }))
                .orElseGet(() -> identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, receiverName)
                        .orElseGet(() -> {
                            log.info("Creating new orphan application identity for WhatsApp receiver: {}", receiverName);
                            IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                                    .application(CommunicationApplication.WHATSAPP)
                                    .identifier(receiverName)
                                    .build();
                            return identityInApplicationRepository.save(entity);
                        }));
    }

    private void processFile(BufferedReader reader, long totalMessages, IdentityInApplicationEntity receiverEntity) throws IOException {
        Map<String, IdentityInApplicationEntity> identityCache = new HashMap<>();
        Set<String> knownHashes = new HashSet<>();
        List<MessageEntity> batch = new ArrayList<>(BATCH_SIZE);

        String line;
        String currentSenderName = null;
        StringBuilder currentBody = null;
        LocalDateTime currentTimestamp = null;
        long totalImported = 0;
        long skippedDuplicates = 0;
        long messagesProcessed = 0;

        while ((line = reader.readLine()) != null) {
            // Strip leading Unicode formatting characters (e.g. LRM \u200E) that WhatsApp prepends to some lines
            line = LEADING_UNICODE_FORMATTING.matcher(line).replaceFirst("");
            Matcher matcher = MESSAGE_PATTERN.matcher(line);

            if (matcher.matches()) {
                // Flush the previous message if there is one
                if (currentSenderName != null && currentBody != null) {
                    MessageEntity message = buildMessage(currentSenderName, currentBody.toString(), currentTimestamp, identityCache, receiverEntity);

                    if (!isDuplicate(message.getSourceIdentifier(), knownHashes)) {
                        knownHashes.add(message.getSourceIdentifier());
                        batch.add(message);
                    } else {
                        skippedDuplicates++;
                    }

                    messagesProcessed++;
                    updateProgress(totalMessages, messagesProcessed);

                    if (batch.size() >= BATCH_SIZE) {
                        totalImported += flushBatch(batch);
                    }
                }

                // Start a new message
                String dateStr = matcher.group(1);
                String timeStr = matcher.group(2);
                currentSenderName = matcher.group(3);
                String bodyStart = matcher.group(4);

                currentTimestamp = LocalDateTime.parse(dateStr + ", " + timeStr, TIMESTAMP_FORMATTER);
                currentBody = new StringBuilder(bodyStart);
            } else {
                // Continuation of the previous message (multiline)
                if (currentBody != null) {
                    currentBody.append("\n").append(line);
                }
            }
        }

        // Flush the last message
        if (currentSenderName != null && currentBody != null) {
            MessageEntity message = buildMessage(currentSenderName, currentBody.toString(), currentTimestamp, identityCache, receiverEntity);
            if (!isDuplicate(message.getSourceIdentifier(), knownHashes)) {
                batch.add(message);
            } else {
                skippedDuplicates++;
            }
            messagesProcessed++;
        }

        // Flush any remaining messages in the batch
        if (!batch.isEmpty()) {
            totalImported += flushBatch(batch);
            updateProgress(totalMessages, messagesProcessed);
        }

        log.info("Imported {} WhatsApp messages, skipped {} duplicates, {} participants.",
                totalImported, skippedDuplicates, identityCache.size());
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
            log.info("Imported {}% of WhatsApp messages.", percentage);
        }
    }

    /**
     * Check if a message with this hash already exists in the current batch or in the database.
     */
    private boolean isDuplicate(String hash, Set<String> knownHashes) {
        return knownHashes.contains(hash) || messageRepository.existsBySourceIdentifier(hash);
    }

    private MessageEntity buildMessage(String senderName, String body, LocalDateTime timestamp,
                                       Map<String, IdentityInApplicationEntity> identityCache,
                                       IdentityInApplicationEntity receiverEntity) {
        IdentityInApplicationEntity sender = getOrCreateIdentity(senderName, identityCache);
        String hash = computeMessageHash(senderName, timestamp, body);

        return MessageEntity.builder()
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sourceIdentifier(hash)
                .sender(sender)
                .receiver(receiverEntity)
                .body(body)
                .timestamp(timestamp)
                .build();
    }

    /**
     * Compute a SHA-256 hash of the message content (sender name + timestamp + body)
     * to use as a unique identifier for deduplication.
     */
    private String computeMessageHash(String senderName, LocalDateTime timestamp, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = senderName + "|" + timestamp + "|" + body;
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private IdentityInApplicationEntity getOrCreateIdentity(String name, Map<String, IdentityInApplicationEntity> cache) {
        return cache.computeIfAbsent(name, n -> {
            return identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, n)
                    .orElseGet(() -> {
                        log.info("Creating new application identity for WhatsApp user: {}", n);
                        IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                                .application(CommunicationApplication.WHATSAPP)
                                .identifier(n)
                                .build();
                        return identityInApplicationRepository.save(entity);
                    });
        });
    }

    private long flushBatch(List<MessageEntity> batch) {
        messageRepository.saveAll(batch);
        messageRepository.flush();
        long size = batch.size();
        log.info("Flushed batch of {} WhatsApp messages.", size);
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
