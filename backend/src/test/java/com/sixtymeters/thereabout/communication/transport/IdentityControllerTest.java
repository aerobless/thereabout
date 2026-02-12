package com.sixtymeters.thereabout.communication.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.generated.model.GenIdentity;
import com.sixtymeters.thereabout.generated.model.GenIdentityInApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdentityRepository identityRepository;

    @BeforeEach
    void setUp() {
        IdentityEntity senderIdentity = IdentityEntity.builder()
                .shortName("sender")
                .firstName("John")
                .lastName("Sender")
                .email("sender@example.com")
                .relationship("friend")
                .build();
        IdentityInApplicationEntity senderApplication = IdentityInApplicationEntity.builder()
                .identity(senderIdentity)
                .application("WhatsApp")
                .identifier("+4100000001")
                .build();
        senderIdentity.getIdentityInApplications().add(senderApplication);
        identityRepository.save(senderIdentity);

        IdentityEntity receiverIdentity = IdentityEntity.builder()
                .shortName("receiver")
                .firstName("Jane")
                .lastName("Receiver")
                .email("receiver@example.com")
                .relationship("family")
                .build();
        IdentityInApplicationEntity receiverApplication = IdentityInApplicationEntity.builder()
                .identity(receiverIdentity)
                .application("WhatsApp")
                .identifier("+4100000002")
                .build();
        receiverIdentity.getIdentityInApplications().add(receiverApplication);
        identityRepository.save(receiverIdentity);
    }

    @Test
    void testCreateIdentity() throws Exception {
        GenIdentity request = GenIdentity.builder()
                .id(BigDecimal.ZERO)
                .shortName("new-contact")
                .firstName("New")
                .lastName("Contact")
                .email("new@example.com")
                .relationship("colleague")
                .identityInApplications(List.of(
                        GenIdentityInApplication.builder()
                                .id(BigDecimal.ZERO)
                                .application("Telegram")
                                .identifier("@new_contact")
                                .build()
                ))
                .build();

        String responseContent = mockMvc.perform(post("/backend/api/v1/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentity response = objectMapper.readValue(responseContent, GenIdentity.class);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getShortName()).isEqualTo("new-contact");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getApplication()).isEqualTo("Telegram");
    }

    @Test
    void testGetIdentities() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/identity"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentity[] response = objectMapper.readValue(responseContent, GenIdentity[].class);

        assertThat(response).isNotEmpty();
        assertThat(response)
                .extracting(GenIdentity::getShortName)
                .contains("sender", "receiver");
    }

    @Test
    void testUpdateIdentity() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "sender".equals(identity.getShortName()))
                .findFirst()
                .orElseThrow();

        GenIdentity request = GenIdentity.builder()
                .id(BigDecimal.valueOf(existing.getId()))
                .shortName("sender-updated")
                .firstName("John")
                .lastName("Updated")
                .email("sender-updated@example.com")
                .relationship("best-friend")
                .identityInApplications(List.of(
                        GenIdentityInApplication.builder()
                                .id(BigDecimal.ZERO)
                                .application("Signal")
                                .identifier("+4100000009")
                                .build()
                ))
                .build();

        String responseContent = mockMvc.perform(put("/backend/api/v1/identity/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentity response = objectMapper.readValue(responseContent, GenIdentity.class);

        assertThat(response.getId()).isEqualTo(BigDecimal.valueOf(existing.getId()));
        assertThat(response.getShortName()).isEqualTo("sender-updated");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getApplication()).isEqualTo("Signal");
    }

    @Test
    void testDeleteIdentity() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "receiver".equals(identity.getShortName()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(delete("/backend/api/v1/identity/{id}", existing.getId()))
                .andExpect(status().isNoContent());

        assertThat(identityRepository.findById(existing.getId())).isEmpty();
    }
}
