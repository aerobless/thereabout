package com.sixtymeters.thereabout.communication.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
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
                .relationship("friend")
                .build();
        IdentityInApplicationEntity senderApplication = IdentityInApplicationEntity.builder()
                .identity(senderIdentity)
                .application(CommunicationApplication.WHATSAPP)
                .identifier("+4100000001")
                .build();
        senderIdentity.getIdentityInApplications().add(senderApplication);
        identityRepository.save(senderIdentity);

        IdentityEntity receiverIdentity = IdentityEntity.builder()
                .shortName("receiver")
                .relationship("family")
                .build();
        IdentityInApplicationEntity receiverApplication = IdentityInApplicationEntity.builder()
                .identity(receiverIdentity)
                .application(CommunicationApplication.WHATSAPP)
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
    void testUpdateIdentityWithoutIdentityInApplications() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "receiver".equals(identity.getShortName()))
                .findFirst()
                .orElseThrow();

        String requestBody = """
                {"id":%d,"shortName":"receiver-updated","relationship":"colleague"}
                """.formatted(existing.getId()).strip();

        String responseContent = mockMvc.perform(put("/backend/api/v1/identity/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentity response = objectMapper.readValue(responseContent, GenIdentity.class);

        assertThat(response.getId()).isEqualTo(BigDecimal.valueOf(existing.getId()));
        assertThat(response.getShortName()).isEqualTo("receiver-updated");
        assertThat(response.getRelationship()).isEqualTo("colleague");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getIdentifier()).isEqualTo("+4100000002");
    }

    @Test
    void testUpdateIdentityPreservesExistingIdentityInApplications() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "sender".equals(identity.getShortName()))
                .findFirst()
                .orElseThrow();

        IdentityInApplicationEntity existingApp = existing.getIdentityInApplications().getFirst();

        GenIdentity request = GenIdentity.builder()
                .id(BigDecimal.valueOf(existing.getId()))
                .shortName("sender-edited")
                .relationship("colleague")
                .identityInApplications(List.of(
                        GenIdentityInApplication.builder()
                                .id(BigDecimal.valueOf(existingApp.getId()))
                                .application("WhatsApp")
                                .identifier("+4100000001")
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
        assertThat(response.getShortName()).isEqualTo("sender-edited");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getId()).isEqualTo(BigDecimal.valueOf(existingApp.getId()));
        assertThat(response.getIdentityInApplications().getFirst().getApplication()).isEqualTo("WhatsApp");
        assertThat(response.getIdentityInApplications().getFirst().getIdentifier()).isEqualTo("+4100000001");
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
