package com.sixtymeters.thereabout.communication.service.importer;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WhatsAppChatImporterTest {

    @Autowired
    private WhatsAppChatImporter whatsAppChatImporter;

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
        Path source = Path.of(getClass().getClassLoader().getResource("whatsapp-chat-export.txt").getFile());
        Path tempDir = Files.createTempDirectory("whatsapp-test");
        Path tempFile = tempDir.resolve("whatsapp-chat-export.txt");
        Files.copy(source, tempFile);
        return tempFile.toFile();
    }

    @Test
    void testImportWhatsAppChat() throws IOException {
        // Given
        File testFile = copyTestFileToTemp();

        // When
        whatsAppChatImporter.importFile(testFile, "Bob Smith");

        // Then
        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(12);

        // Verify application identities were created
        var aliceIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, "Alice Miller");
        assertThat(aliceIdentity).isPresent();
        assertThat(aliceIdentity.get().getIdentity()).isNull(); // orphan, not linked to identity yet

        var bobIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, "Bob Smith");
        assertThat(bobIdentity).isPresent();
        assertThat(bobIdentity.get().getIdentity()).isNull();

        // Verify message properties
        assertThat(messages).allSatisfy(msg -> {
            assertThat(msg.getType()).isEqualTo("text");
            assertThat(msg.getSource()).isEqualTo(CommunicationApplication.WHATSAPP);
            assertThat(msg.getTimestamp()).isNotNull();
            assertThat(msg.getSourceIdentifier()).isNotBlank();
        });

        // Verify first message
        MessageEntity firstMessage = messages.stream()
                .filter(m -> m.getBody().equals("Good morning!"))
                .findFirst()
                .orElseThrow();
        assertThat(firstMessage.getSender().getIdentifier()).isEqualTo("Alice Miller");
        assertThat(firstMessage.getTimestamp()).isEqualTo(LocalDateTime.of(2024, 3, 15, 9, 12, 30));

        // Verify multiline message (shopping list)
        MessageEntity multilineMessage = messages.stream()
                .filter(m -> m.getBody().startsWith("Shopping list:"))
                .findFirst()
                .orElseThrow();
        assertThat(multilineMessage.getBody()).isEqualTo("Shopping list:\n- bread\n- butter\n- milk");
        assertThat(multilineMessage.getSender().getIdentifier()).isEqualTo("Bob Smith");

        // Verify URL message
        MessageEntity urlMessage = messages.stream()
                .filter(m -> m.getBody().contains("https://"))
                .findFirst()
                .orElseThrow();
        assertThat(urlMessage.getBody()).isEqualTo("Check this out: https://example.com/path?foo=bar&baz=qux");

        // Verify all messages have the specified receiver
        messages.forEach(msg -> {
            assertThat(msg.getReceiver().getIdentifier()).isEqualTo("Bob Smith");
            assertThat(msg.getSender().getIdentifier()).isIn("Alice Miller", "Bob Smith");
        });

        // Verify emoji message
        MessageEntity emojiMessage = messages.stream()
                .filter(m -> m.getBody().contains("Let's do it"))
                .findFirst()
                .orElseThrow();
        assertThat(emojiMessage.getBody()).contains("\uD83C\uDF89"); // 🎉

        // Verify LRM-prefixed lines are parsed as separate messages (not merged into previous)
        long lrmImageMessages = messages.stream()
                .filter(m -> m.getBody().contains("image omitted"))
                .count();
        assertThat(lrmImageMessages).isEqualTo(3); // 1 normal + 2 LRM-prefixed

        // Verify the LRM-prefixed messages have correct timestamps (not merged)
        MessageEntity lrmMessage = messages.stream()
                .filter(m -> m.getTimestamp().equals(LocalDateTime.of(2024, 3, 15, 9, 19, 30)))
                .findFirst()
                .orElseThrow();
        assertThat(lrmMessage.getSender().getIdentifier()).isEqualTo("Alice Miller");
        assertThat(lrmMessage.getBody()).contains("image omitted");

        // Verify empty-body message (no space after colon) is parsed as its own message
        MessageEntity emptyBodyMessage = messages.stream()
                .filter(m -> m.getTimestamp().equals(LocalDateTime.of(2024, 3, 15, 9, 19, 45)))
                .findFirst()
                .orElseThrow();
        assertThat(emptyBodyMessage.getSender().getIdentifier()).isEqualTo("Bob Smith");
        assertThat(emptyBodyMessage.getBody()).isEmpty();

        // Verify LRM-prefixed document omitted message after empty-body message
        MessageEntity documentMessage = messages.stream()
                .filter(m -> m.getBody().contains("document omitted"))
                .findFirst()
                .orElseThrow();
        assertThat(documentMessage.getSender().getIdentifier()).isEqualTo("Bob Smith");
        assertThat(documentMessage.getTimestamp()).isEqualTo(LocalDateTime.of(2024, 3, 15, 9, 19, 46));
    }

    @Test
    void testImportGroupChat() throws IOException {
        // Given - create a group identity first (as user would in identity UI)
        IdentityEntity groupIdentity = IdentityEntity.builder()
                .shortName("Family Group")
                .isGroup(true)
                .build();
        groupIdentity = identityRepository.save(groupIdentity);

        File testFile = copyTestFileToTemp();

        // When - import with receiver name matching the group identity
        whatsAppChatImporter.importFile(testFile, "Family Group");

        // Then
        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(12);

        // Verify receiver is linked to the group identity
        var groupAppIdentity = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, "Family Group");
        assertThat(groupAppIdentity).isPresent();
        assertThat(groupAppIdentity.get().getIdentity()).isNotNull();
        assertThat(groupAppIdentity.get().getIdentity().getId()).isEqualTo(groupIdentity.getId());
        assertThat(groupAppIdentity.get().getIdentity().isGroup()).isTrue();

        // Verify all messages have the group as receiver
        messages.forEach(msg -> {
            assertThat(msg.getReceiver().getIdentifier()).isEqualTo("Family Group");
            assertThat(msg.getReceiver().getIdentity()).isNotNull();
        });

        // Verify senders are the individual participants
        var senderNames = messages.stream()
                .map(m -> m.getSender().getIdentifier())
                .distinct()
                .toList();
        assertThat(senderNames).containsExactlyInAnyOrder("Alice Miller", "Bob Smith");
    }

    @Test
    void testImportDoesNotDuplicateMessages() throws IOException {
        // Given & When - import the same file twice
        whatsAppChatImporter.importFile(copyTestFileToTemp(), "Bob Smith");
        whatsAppChatImporter.importFile(copyTestFileToTemp(), "Bob Smith");

        // Then - identities should not be duplicated
        var aliceIdentities = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, "Alice Miller");
        assertThat(aliceIdentities).isPresent();

        var bobIdentities = identityInApplicationRepository.findByApplicationAndIdentifier(CommunicationApplication.WHATSAPP, "Bob Smith");
        assertThat(bobIdentities).isPresent();

        // Messages should also not be duplicated (dedup via sourceIdentifier hash)
        List<MessageEntity> messages = messageRepository.findAll();
        assertThat(messages).hasSize(12);
    }
}
