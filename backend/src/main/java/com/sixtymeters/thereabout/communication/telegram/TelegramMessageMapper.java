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
 * Maps TDLib messages to our MessageEntity. Uses same sourceIdentifier and identity pattern as TelegramChatImporter:
 * identifier = user ID only, usernameHint = display name; cache key = ID only.
 * Does not call any TDLib method that would mark messages as read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramMessageMapper {

    private final IdentityInApplicationRepository identityInApplicationRepository;
    private final IdentityRepository identityRepository;
    private final MessageRepository messageRepository;

    /** Cache for this batch to avoid repeated DB lookups. Key = "sender:" + userId (ID only). */
    private final Map<String, IdentityInApplicationEntity> identityCache = new ConcurrentHashMap<>();

    /**
     * Build a MessageEntity from a TDLib message. Returns null if message should be skipped.
     *
     * @param senderUserId   Telegram user ID as string (e.g. "66361726"); used as identifier and cache key.
     * @param senderUsernameHint optional display name (e.g. "Markus"); stored as username_hint, used for display when unlinked.
     * @param receiverId     Chat ID as string; used as identifier and for lookup (only ID, not hint).
     * @param receiverUsernameHint optional chat title; stored as username_hint for display when unlinked.
     * @param receiverIsGroup true if the receiver is a group/supergroup chat (sets is_group on the app identity when created).
     */
    public MessageEntity toMessageEntity(
            TdApi.Message msg,
            String chatIdStr,
            String receiverId,
            String receiverUsernameHint,
            boolean receiverIsGroup,
            String senderUserId,
            String senderUsernameHint
    ) {
        String sourceIdentifier = "telegram-" + chatIdStr + "-" + msg.id;
        if (messageRepository.existsBySourceIdentifier(sourceIdentifier)) {
            return null;
        }
        IdentityInApplicationEntity sender = getOrCreateIdentity(senderUserId, senderUsernameHint);
        IdentityInApplicationEntity receiver = getOrCreateReceiver(receiverId, receiverUsernameHint, receiverIsGroup);
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

    /**
     * Like toMessageEntity but with explicit sourceIdentifier = sourceIdPrefix + msg.id.
     * Used for basic-group messages stored under supergroup receiver (telegram-{supergroupChatId}-b-{msgId}).
     */
    public MessageEntity toMessageEntityWithSourcePrefix(
            TdApi.Message msg,
            String sourceIdPrefix,
            String receiverId,
            String receiverUsernameHint,
            boolean receiverIsGroup,
            String senderUserId,
            String senderUsernameHint
    ) {
        String sourceIdentifier = sourceIdPrefix + msg.id;
        if (messageRepository.existsBySourceIdentifier(sourceIdentifier)) {
            return null;
        }
        IdentityInApplicationEntity sender = getOrCreateIdentity(senderUserId, senderUsernameHint);
        IdentityInApplicationEntity receiver = getOrCreateReceiver(receiverId, receiverUsernameHint, receiverIsGroup);
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

    /** Lookup only by receiverId (e.g. chat ID); usernameHint is stored for display, not used for finding existing. */
    private IdentityInApplicationEntity getOrCreateReceiver(String receiverId, String receiverUsernameHint, boolean isGroup) {
        return identityCache.computeIfAbsent("receiver:" + receiverId, k ->
                identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, receiverId)
                        .orElseGet(() -> {
                            var linked = receiverUsernameHint != null && !receiverUsernameHint.isBlank()
                                    ? identityRepository.findByShortName(receiverUsernameHint).orElse(null)
                                    : null;
                            return saveNewIdentityInApp(CommunicationApplication.TELEGRAM, receiverId, receiverUsernameHint, isGroup, linked);
                        }));
    }

    private IdentityInApplicationEntity getOrCreateIdentity(String userId, String usernameHint) {
        return identityCache.computeIfAbsent("sender:" + userId, k ->
                identityInApplicationRepository
                        .findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, userId)
                        .orElseGet(() -> saveNewIdentityInApp(CommunicationApplication.TELEGRAM, userId, usernameHint, false, null)));
    }

    private IdentityInApplicationEntity saveNewIdentityInApp(
            CommunicationApplication app,
            String identifier,
            String usernameHint,
            boolean isGroup,
            IdentityEntity linkedIdentity
    ) {
        IdentityInApplicationEntity entity = IdentityInApplicationEntity.builder()
                .application(app)
                .identifier(identifier)
                .usernameHint(usernameHint)
                .isGroup(isGroup)
                .identity(linkedIdentity)
                .build();
        return identityInApplicationRepository.save(entity);
    }

    public void clearCache() {
        identityCache.clear();
    }
}
