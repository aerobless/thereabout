package com.sixtymeters.thereabout.communication.service.importer;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import com.sixtymeters.thereabout.config.ThereaboutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TelegramChatImporterTest {

    @Autowired
    private TelegramChatImporter telegramChatImporter;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private IdentityInApplicationRepository identityInApplicationRepository;

    @Autowired
    private IdentityRepository identityRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        identityInApplicationRepository.deleteAll();
        identityRepository.deleteAll();
    }

    private File copyTestFileToTemp() throws IOException {
        return copyTestResourceToTemp("telegram-chat-export.json");
    }

    private File copyTestResourceToTemp(String resourceName) throws IOException {
        Path source = Path.of(getClass().getClassLoader().getResource(resourceName).getFile());
        Path tempDir = Files.createTempDirectory("telegram-test");
        Path tempFile = tempDir.resolve(resourceName);
        Files.copy(source, tempFile);
        return tempFile.toFile();
    }

    @Test
    void testImportTelegramChat() throws IOException {
        // Given
        File testFile = copyTestFileToTemp();

        // When
        telegramChatImporter.importFile(testFile, "Some Contact");

        // Then
        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(7);

        // Verify application identities use composite id: from|from_id
        var aliceIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, "Alice Test|user111");
        assertThat(aliceIdentity).isPresent();
        assertThat(aliceIdentity.get().getIdentity()).isNull();

        var bobIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, "Bob Test|user222");
        assertThat(bobIdentity).isPresent();
        assertThat(bobIdentity.get().getIdentity()).isNull();

        var receiverIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, "Some Contact");
        assertThat(receiverIdentity).isPresent();

        // Verify message properties
        assertThat(messages).allSatisfy(msg -> {
            assertThat(msg.getType()).isEqualTo("text");
            assertThat(msg.getSource()).isEqualTo(CommunicationApplication.TELEGRAM);
            assertThat(msg.getTimestamp()).isNotNull();
            assertThat(msg.getSourceIdentifier()).startsWith("telegram-");
        });

        // Verify all messages have the specified receiver
        messages.forEach(msg -> assertThat(msg.getReceiver().getIdentifier()).isEqualTo("Some Contact"));

        // Verify plain text message
        MessageEntity plainMessage = messages.stream()
                .filter(m -> m.getBody().equals("Hello, this is a plain text message."))
                .findFirst()
                .orElseThrow();
        assertThat(plainMessage.getSender().getIdentifier()).isEqualTo("Alice Test|user111");
        assertThat(plainMessage.getTimestamp()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 0, 0));

        // Verify flattened link + plain text message
        MessageEntity linkMessage = messages.stream()
                .filter(m -> m.getBody().contains("https://example.com/page"))
                .findFirst()
                .orElseThrow();
        assertThat(linkMessage.getBody()).isEqualTo("https://example.com/page - check this out");
        assertThat(linkMessage.getSender().getIdentifier()).isEqualTo("Bob Test|user222");

        // Verify media placeholders
        assertThat(messages.stream().filter(m -> m.getBody().equals("[photo]")).count()).isEqualTo(1);
        assertThat(messages.stream().filter(m -> m.getBody().equals("[voice_message]")).count()).isEqualTo(1);
        assertThat(messages.stream().filter(m -> m.getBody().equals("[sticker]")).count()).isEqualTo(1);

        // Verify service message (phone_call)
        MessageEntity serviceMessage = messages.stream()
                .filter(m -> m.getBody().startsWith("[phone_call"))
                .findFirst()
                .orElseThrow();
        assertThat(serviceMessage.getBody()).isEqualTo("[phone_call 120s]");
        assertThat(serviceMessage.getSender().getIdentifier()).isEqualTo("Alice Test|user111");

        // Verify last text message
        MessageEntity byeMessage = messages.stream()
                .filter(m -> m.getBody().equals("Bye!"))
                .findFirst()
                .orElseThrow();
        assertThat(byeMessage.getSender().getIdentifier()).isEqualTo("Bob Test|user222");
    }

    @Test
    void testImportTelegramGroupChat() throws IOException {
        // Given - create a group identity first (as user would in identity UI)
        IdentityEntity groupIdentity = IdentityEntity.builder()
                .shortName("Test Group")
                .isGroup(true)
                .build();
        groupIdentity = identityRepository.save(groupIdentity);

        File testFile = copyTestFileToTemp();

        // When - import with receiver name matching the group identity
        telegramChatImporter.importFile(testFile, "Test Group");

        // Then
        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(7);

        // Verify receiver is linked to the group identity
        var groupAppIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, "Test Group");
        assertThat(groupAppIdentity).isPresent();
        assertThat(groupAppIdentity.get().getIdentity()).isNotNull();
        assertThat(groupAppIdentity.get().getIdentity().getId()).isEqualTo(groupIdentity.getId());
        assertThat(groupAppIdentity.get().getIdentity().isGroup()).isTrue();

        // Verify all messages have the group as receiver
        messages.forEach(msg -> {
            assertThat(msg.getReceiver().getIdentifier()).isEqualTo("Test Group");
            assertThat(msg.getReceiver().getIdentity()).isNotNull();
        });

        // Verify senders are the individual participants (composite id)
        var senderIdentifiers = messages.stream()
                .map(m -> m.getSender().getIdentifier())
                .distinct()
                .toList();
        assertThat(senderIdentifiers).containsExactlyInAnyOrder("Alice Test|user111", "Bob Test|user222");
    }

    @Test
    void testImportDoesNotDuplicateMessages() throws IOException {
        // Given & When - import the same file twice
        telegramChatImporter.importFile(copyTestFileToTemp(), "Some Contact");
        telegramChatImporter.importFile(copyTestFileToTemp(), "Some Contact");

        // Then - messages should not be duplicated (dedup via sourceIdentifier)
        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(7);

        var aliceIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, "Alice Test|user111");
        assertThat(aliceIdentity).isPresent();

        var bobIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.TELEGRAM, "Bob Test|user222");
        assertThat(bobIdentity).isPresent();
    }

    @Test
    void testImportMultiChatTelegram() throws IOException {
        File testFile = copyTestResourceToTemp("telegram-multi-chat-export.json");

        telegramChatImporter.importFile(testFile, "Provided Contact");

        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(5);

        List<MessageEntity> groupMessages = messages.stream()
                .filter(m -> m.getReceiver().getIdentifier().equals("Test Group"))
                .toList();
        assertThat(groupMessages).hasSize(3);
        assertThat(groupMessages).allSatisfy(m -> assertThat(m.getSourceIdentifier()).startsWith("telegram-1001-"));

        List<MessageEntity> personalMessages = messages.stream()
                .filter(m -> m.getReceiver().getIdentifier().equals("Provided Contact"))
                .toList();
        assertThat(personalMessages).hasSize(2);
        assertThat(personalMessages).allSatisfy(m -> assertThat(m.getSourceIdentifier()).startsWith("telegram-1002-"));

        assertThat(messages.stream().filter(m -> m.getBody().equals("Group chat message one.")).findFirst()).isPresent();
        assertThat(messages.stream().filter(m -> m.getBody().equals("Personal chat hi.")).findFirst()).isPresent();
    }

    @Test
    void testImportSingleChatGroupUsesChatName() throws IOException {
        File testFile = copyTestResourceToTemp("telegram-single-group-export.json");

        telegramChatImporter.importFile(testFile, "User Entered Name");

        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getReceiver().getIdentifier()).isEqualTo("My Group");
        assertThat(messages.get(0).getBody()).isEqualTo("Message in group.");
    }

    @Test
    void testImportSingleChatPersonalRequiresReceiver() throws IOException {
        assertThatThrownBy(() -> telegramChatImporter.importFile(copyTestFileToTemp(), ""))
                .isInstanceOf(ThereaboutException.class)
                .hasMessageContaining("receiver");

        assertThatThrownBy(() -> telegramChatImporter.importFile(copyTestFileToTemp(), null))
                .isInstanceOf(ThereaboutException.class)
                .hasMessageContaining("receiver");
    }
}
