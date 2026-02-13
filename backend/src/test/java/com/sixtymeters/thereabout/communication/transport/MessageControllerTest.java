package com.sixtymeters.thereabout.communication.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
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
    void testGetMessagesByDate() throws Exception {
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
        assertThat(response[0].getSender().getIdentifier()).isEqualTo("+4100000001");
        assertThat(response[0].getReceiver().getIdentifier()).isEqualTo("+4100000002");
    }
}
