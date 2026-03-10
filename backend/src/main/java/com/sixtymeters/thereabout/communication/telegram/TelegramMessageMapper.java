package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.*;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps TDLib messages to our MessageEntity. Uses same sourceIdentifier and identity pattern as TelegramChatImporter.
 * Does not call any TDLib method that would mark messages as read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramMessageMapper {

    private final IdentityInApplicationRepository identityInApplicationRepository;
    private final IdentityRepository identityRepository;
    private final MessageRepository messageRepository;

    /** Cache for this batch to avoid repeated DB lookups. */
    private final Map<String, IdentityInApplicationEntity> identityCache = new ConcurrentHashMap<>();

    /**
     * Build a MessageEntity from a TDLib message. Returns null if message should be skipped.
     * senderIdentifier and receiverIdentifier must be in the form used by TelegramChatImporter (e.g. "Name|userId" for sender).
     */
    public MessageEntity toMessageEntity(
            TdApi.Message msg,
            String chatIdStr,
            String receiverIdentifier,
            String senderIdentifier
    ) {
        String sourceIdentifier = "telegram-" + chatIdStr + "-" + msg.id;
        if (messageRepository.existsBySourceIdentifier(sourceIdentifier)) {
            return null;
        }
        IdentityInApplicationEntity sender = getOrCreateIdentity(senderIdentifier);
        IdentityInApplicationEntity receiver = getOrCreateReceiver(receiverIdentifier);
        LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(msg.date), ZoneOffset.UTC);
        String body = extractBody(msg.content);
        if (body == null) {
            return null;
        }
        return MessageEntity.builder()
                .type("text")
                .source(CommunicationApplication.TELEGRAM)
                .sourceIdentifier(sourceIdentifier)
                .sender(sender)
                .receiver(receiver)
                .body(body)
                .timestamp(timestamp)
                .build();
    }

    private String extractBody(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText text) {
            return text.text != null && text.text.text != null ? text.text.text : "";
        }
        if (content == null) {
            return "[message]";
        }
        if (content instanceof TdApi.MessagePhoto) {
            return "[photo]";
        }
        if (content instanceof TdApi.MessageVoiceNote) {
            return "[voice_message]";
        }
        if (content instanceof TdApi.MessageSticker) {
            return "[sticker]";
        }
        if (content instanceof TdApi.MessageDocument doc) {
            return doc.document != null && doc.document.fileName != null ? "[file: " + doc.document.fileName + "]" : "[file]";
        }
        if (content instanceof TdApi.MessageVideo) {
            return "[video]";
        }
        if (content instanceof TdApi.MessageAudio) {
            return "[audio]";
        }
        if (content instanceof TdApi.MessageAnimation) {
            return "[animation]";
        }
        if (content instanceof TdApi.MessageVideoNote) {
            return "[video_note]";
        }
        if (content instanceof TdApi.MessageCall call) {
            return "[call " + (call.duration > 0 ? call.duration + "s" : "") + "]";
        }
        if (content instanceof TdApi.MessageBasicGroupChatCreate
                || content instanceof TdApi.MessageSupergroupChatCreate
                || content instanceof TdApi.MessageChatChangeTitle
                || content instanceof TdApi.MessageChatAddMembers
                || content instanceof TdApi.MessageChatDeleteMember
                || content instanceof TdApi.MessageChatJoinByLink
                || content instanceof TdApi.MessageChatJoinByRequest) {
            return "[service]";
        }
        return "[media]";
    }

    private IdentityInApplicationEntity getOrCreateReceiver(String receiverName) {
        return identityCache.computeIfAbsent("receiver:" + receiverName, k ->
                identityRepository.findByShortName(receiverName)
                        .map(identity -> identityInApplicationRepository
                                .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, receiverName)
                                .orElseGet(() -> saveNewIdentityInApp(CommunicationApplication.TELEGRAM, receiverName, identity)))
                        .orElseGet(() -> identityInApplicationRepository
                                .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, receiverName)
                                .orElseGet(() -> saveNewIdentityInApp(CommunicationApplication.TELEGRAM, receiverName, null))));
    }

    private IdentityInApplicationEntity getOrCreateIdentity(String identifier) {
        return identityCache.computeIfAbsent("sender:" + identifier, k ->
                identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, identifier)
                        .orElseGet(() -> saveNewIdentityInApp(CommunicationApplication.TELEGRAM, identifier, null)));
    }

    private IdentityInApplicationEntity saveNewIdentityInApp(
            CommunicationApplication app,
            String identifier,
            IdentityEntity linkedIdentity
    ) {
        IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                .application(app)
                .identifier(identifier)
                .identity(linkedIdentity)
                .build();
        return identityInApplicationRepository.save(entity);
    }

    public void clearCache() {
        identityCache.clear();
    }
}
