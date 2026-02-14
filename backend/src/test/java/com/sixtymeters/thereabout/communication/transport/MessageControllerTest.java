package com.sixtymeters.thereabout.communication.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import com.sixtymeters.thereabout.generated.model.GenMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private IdentityInApplicationRepository identityInApplicationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private IdentityInApplicationEntity senderApplication;
    private IdentityInApplicationEntity receiverApplication;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();

        IdentityEntity senderIdentity = IdentityEntity.builder()
                .shortName("sender")
                .firstName("John")
                .lastName("Sender")
                .email("sender@example.com")
                .relationship("friend")
                .build();
        senderApplication = IdentityInApplicationEntity.builder()
                .identity(senderIdentity)
                .application("WhatsApp")
                .identifier("+4100000001")
                .build();
        senderIdentity.getIdentityInApplications().add(senderApplication);
        IdentityEntity persistedSender = identityRepository.save(senderIdentity);
        senderApplication = persistedSender.getIdentityInApplications().getFirst();

        IdentityEntity receiverIdentity = IdentityEntity.builder()
                .shortName("receiver")
                .firstName("Jane")
                .lastName("Receiver")
                .email("receiver@example.com")
                .relationship("family")
                .build();
        receiverApplication = IdentityInApplicationEntity.builder()
                .identity(receiverIdentity)
                .application("WhatsApp")
                .identifier("+4100000002")
                .build();
        receiverIdentity.getIdentityInApplications().add(receiverApplication);
        IdentityEntity persistedReceiver = identityRepository.save(receiverIdentity);
        receiverApplication = persistedReceiver.getIdentityInApplications().getFirst();
    }

    @Test
    void testGetMessagesByDate_linkedIdentities() throws Exception {
        LocalDate queryDate = LocalDate.of(2026, 2, 10);

        MessageEntity messageOnDate = MessageEntity.builder()
                .type("text")
                .source("WhatsApp")
                .sourceIdentifier("chat-1")
                .sender(senderApplication)
                .receiver(receiverApplication)
                .subject("Morning")
                .body("Hello there")
                .timestamp(queryDate.atTime(9, 30))
                .build();
        messageRepository.save(messageOnDate);

        MessageEntity messageDifferentDay = MessageEntity.builder()
                .type("text")
                .source("WhatsApp")
                .sourceIdentifier("chat-2")
                .sender(senderApplication)
                .receiver(receiverApplication)
                .subject("Yesterday")
                .body("Not on query date")
                .timestamp(queryDate.minusDays(1).atTime(22, 15))
                .build();
        messageRepository.save(messageDifferentDay);

        String responseContent = mockMvc.perform(get("/backend/api/v1/message")
                        .param("date", queryDate.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenMessage[] response = objectMapper.readValue(responseContent, GenMessage[].class);

        assertThat(response).hasSize(1);
        assertThat(response[0].getSubject()).isEqualTo("Morning");
        assertThat(response[0].getBody()).isEqualTo("Hello there");
        // Linked identities resolve to identity shortName
        assertThat(response[0].getSender().getName()).isEqualTo("sender");
        assertThat(response[0].getSender().getIdentityId()).isNotNull();
        assertThat(response[0].getReceiver().getName()).isEqualTo("receiver");
        assertThat(response[0].getReceiver().getIdentityId()).isNotNull();
    }

    @Test
    void testGetMessagesByDate_unlinkedSenderFallsBackToIdentifier() throws Exception {
        LocalDate queryDate = LocalDate.of(2026, 2, 10);

        // Create an unlinked app identity (no parent identity)
        IdentityInApplicationEntity unlinkedSender = IdentityInApplicationEntity.builder()
                .application("Telegram")
                .identifier("@unknown_user")
                .build();
        unlinkedSender = identityInApplicationRepository.save(unlinkedSender);

        MessageEntity message = MessageEntity.builder()
                .type("text")
                .source("Telegram")
                .sourceIdentifier("chat-3")
                .sender(unlinkedSender)
                .receiver(receiverApplication)
                .subject("Hi")
                .body("From an unlinked sender")
                .timestamp(queryDate.atTime(14, 0))
                .build();
        messageRepository.save(message);

        String responseContent = mockMvc.perform(get("/backend/api/v1/message")
                        .param("date", queryDate.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenMessage[] response = objectMapper.readValue(responseContent, GenMessage[].class);

        assertThat(response).hasSize(1);
        // Unlinked sender falls back to app identifier
        assertThat(response[0].getSender().getName()).isEqualTo("@unknown_user");
        assertThat(response[0].getSender().getIdentityId()).isNull();
        // Linked receiver still resolves to identity shortName
        assertThat(response[0].getReceiver().getName()).isEqualTo("receiver");
        assertThat(response[0].getReceiver().getIdentityId()).isNotNull();
    }
}
