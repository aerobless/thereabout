package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramMessageMapperTest {

    @Mock
    private IdentityInApplicationRepository identityInApplicationRepository;

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private MessageRepository messageRepository;

    private TelegramMessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TelegramMessageMapper(identityInApplicationRepository, identityRepository, messageRepository);
    }

    @Test
    void returnsExistingMessageWithUpdatedBodyForBackfillReconciliation() {
        String sourceIdentifier = "telegram-100-500";
        MessageEntity existing = MessageEntity.builder()
                .id(1L)
                .sourceIdentifier(sourceIdentifier)
                .body("🐾 Nullpaw is working…")
                .timestamp(LocalDateTime.of(2026, 8, 13, 12, 1))
                .build();
        when(messageRepository.findFirstBySourceIdentifierOrderByIdAsc(sourceIdentifier))
                .thenReturn(Optional.of(existing));

        MessageEntity mapped = mapper.toMessageEntity(
                message(100L, 500L, "Final response"),
                "100", "100", "Nullpaw", false, "42", "Nullpaw");

        assertThat(mapped).isSameAs(existing);
        assertThat(mapped.getBody()).isEqualTo("Final response");
        assertThat(mapped.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 8, 13, 12, 1));
        verifyNoInteractions(identityInApplicationRepository, identityRepository);
    }

    @Test
    void skipsUnchangedExistingMessageDuringBackfill() {
        String sourceIdentifier = "telegram-100-500";
        MessageEntity existing = MessageEntity.builder()
                .id(1L)
                .sourceIdentifier(sourceIdentifier)
                .body("Final response")
                .build();
        when(messageRepository.findFirstBySourceIdentifierOrderByIdAsc(sourceIdentifier))
                .thenReturn(Optional.of(existing));

        MessageEntity mapped = mapper.toMessageEntity(
                message(100L, 500L, "Final response"),
                "100", "100", "Nullpaw", false, "42", "Nullpaw");

        assertThat(mapped).isNull();
        verifyNoInteractions(identityInApplicationRepository, identityRepository);
    }

    private TdApi.Message message(long chatId, long messageId, String body) {
        TdApi.Message message = new TdApi.Message();
        message.chatId = chatId;
        message.id = messageId;
        message.date = 1;
        message.senderId = new TdApi.MessageSenderUser(42L);
        message.content = new TdApi.MessageText(
                new TdApi.FormattedText(body, new TdApi.TextEntity[0]), null, null);
        return message;
    }
}
