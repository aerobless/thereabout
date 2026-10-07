package com.sixtymeters.thereabout.communication.transport;

import tools.jackson.databind.json.JsonMapper;
import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import com.sixtymeters.thereabout.generated.model.GenMessage;
import com.sixtymeters.thereabout.generated.model.GenMessagePage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageControllerTest {

    @Test void searchesAndDisplaysCompleteNamesInMixedMessageLists() throws Exception {
        senderApplication.getIdentity().setFirstName("Theo");
        senderApplication.getIdentity().setLastName("Winter");
        senderApplication.getIdentity().setRole(com.sixtymeters.thereabout.communication.data.UserRole.USER);
        receiverApplication.getIdentity().setFirstName("Anna");
        receiverApplication.getIdentity().setLastName("van der Meer");
        messageRepository.save(MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L))).type("text").source(CommunicationApplication.WHATSAPP)
                .sender(senderApplication).receiver(receiverApplication).body("Hello")
                .timestamp(LocalDate.of(2026,2,10).atTime(10,0)).build());
        for (String name : java.util.List.of("Theo", "Winter", "Theo Winter")) {
            var json = mockMvc.perform(get("/backend/api/v1/message/list").param("sender",name)
                    .param("receiver","van der Meer")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var page = objectMapper.readValue(json, GenMessagePage.class);
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().getFirst().getSender().getName()).isEqualTo("Theo Winter");
            assertThat(page.getContent().getFirst().getReceiver().getName()).isEqualTo("Anna van der Meer");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private IdentityInApplicationRepository identityInApplicationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private IdentityInApplicationEntity senderApplication;
    private IdentityInApplicationEntity receiverApplication;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate db;
    @BeforeEach
    void setUp() {
        com.sixtymeters.thereabout.testing.TestUsers.owner(db);
        messageRepository.deleteAll();

        IdentityEntity senderIdentity = IdentityEntity.builder()
                .firstName("sender")
                .relationship("friend")
                .build();
        senderApplication = IdentityInApplicationEntity.builder()
                .identity(senderIdentity)
                .application(CommunicationApplication.WHATSAPP)
                .identifier("+4100000001")
                .build();
        senderIdentity.getIdentityInApplications().add(senderApplication);
        IdentityEntity persistedSender = identityRepository.save(senderIdentity);
        senderApplication = persistedSender.getIdentityInApplications().getFirst();

        IdentityEntity receiverIdentity = IdentityEntity.builder()
                .firstName("receiver")
                .relationship("family")
                .build();
        receiverApplication = IdentityInApplicationEntity.builder()
                .identity(receiverIdentity)
                .application(CommunicationApplication.WHATSAPP)
                .identifier("+4100000002")
                .build();
        receiverIdentity.getIdentityInApplications().add(receiverApplication);
        IdentityEntity persistedReceiver = identityRepository.save(receiverIdentity);
        receiverApplication = persistedReceiver.getIdentityInApplications().getFirst();
    }

    @Test
    void testGetMessagesByDate_linkedIdentities() throws Exception {
        LocalDate queryDate = LocalDate.of(2026, 2, 10);

        MessageEntity messageOnDate = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sourceIdentifier("chat-1")
                .sender(senderApplication)
                .receiver(receiverApplication)
                .subject("Morning")
                .body("Hello there")
                .timestamp(queryDate.atTime(9, 30))
                .build();
        messageRepository.save(messageOnDate);

        MessageEntity messageDifferentDay = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
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
        // Linked identities resolve to identity firstName
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
                .application(CommunicationApplication.TELEGRAM)
                .identifier("@unknown_user")
                .build();
        unlinkedSender = identityInApplicationRepository.save(unlinkedSender);

        MessageEntity message = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.TELEGRAM)
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
        // Linked receiver still resolves to identity firstName
        assertThat(response[0].getReceiver().getName()).isEqualTo("receiver");
        assertThat(response[0].getReceiver().getIdentityId()).isNotNull();
    }

    @Test
    void testGetMessageList_paginationAndDefaultSort() throws Exception {
        MessageEntity older = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sender(senderApplication)
                .receiver(receiverApplication)
                .body("First")
                .timestamp(LocalDate.of(2026, 2, 1).atTime(10, 0))
                .build();
        MessageEntity newer = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sender(senderApplication)
                .receiver(receiverApplication)
                .body("Second")
                .timestamp(LocalDate.of(2026, 2, 15).atTime(12, 0))
                .build();
        messageRepository.save(older);
        messageRepository.save(newer);

        String responseContent = mockMvc.perform(get("/backend/api/v1/message/list")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenMessagePage page = objectMapper.readValue(responseContent, GenMessagePage.class);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        // Default sort timestamp,desc: most recent first
        assertThat(page.getContent().get(0).getBody()).isEqualTo("Second");
        assertThat(page.getContent().get(1).getBody()).isEqualTo("First");
    }

    @Test
    void testGetMessageList_searchFiltersByBodyOrSubject() throws Exception {
        MessageEntity matchBody = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sender(senderApplication)
                .receiver(receiverApplication)
                .body("UniqueWordInBody")
                .timestamp(LocalDate.of(2026, 2, 10).atTime(9, 0))
                .build();
        MessageEntity matchSubject = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sender(senderApplication)
                .receiver(receiverApplication)
                .subject("UniqueWordInSubject")
                .body("Other")
                .timestamp(LocalDate.of(2026, 2, 10).atTime(10, 0))
                .build();
        MessageEntity noMatch = MessageEntity.builder().archiveUserIds(new java.util.HashSet<>(java.util.Set.of(1L)))
                .type("text")
                .source(CommunicationApplication.WHATSAPP)
                .sender(senderApplication)
                .receiver(receiverApplication)
                .body("Nothing special")
                .timestamp(LocalDate.of(2026, 2, 10).atTime(11, 0))
                .build();
        messageRepository.save(matchBody);
        messageRepository.save(matchSubject);
        messageRepository.save(noMatch);

        String responseContent = mockMvc.perform(get("/backend/api/v1/message/list")
                        .param("search", "UniqueWord"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenMessagePage page = objectMapper.readValue(responseContent, GenMessagePage.class);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(GenMessage::getBody, GenMessage::getSubject)
                .containsExactlyInAnyOrder(
                        tuple("UniqueWordInBody", null),
                        tuple("Other", "UniqueWordInSubject"));
    }
}
