package com.sixtymeters.thereabout.communication.transport;

import tools.jackson.databind.json.JsonMapper;
import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.generated.model.GenIdentity;
import com.sixtymeters.thereabout.generated.model.GenIdentityInApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    private JsonMapper objectMapper;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository applications;

    @BeforeEach
    void setUp() {
        IdentityEntity senderIdentity = IdentityEntity.builder()
                .firstName("sender")
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
                .firstName("receiver")
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
                .requestKey(java.util.UUID.randomUUID().toString())
                .id(BigDecimal.ZERO)
                .firstName("new-contact")
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
        assertThat(response.getFirstName()).isEqualTo("new-contact");
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
                .extracting(GenIdentity::getFirstName)
                .contains("sender", "receiver");
    }

    @Test
    void testUpdateIdentity() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "sender".equals(identity.getFirstName()))
                .findFirst()
                .orElseThrow();

        GenIdentity request = GenIdentity.builder()
                .requestKey(java.util.UUID.randomUUID().toString())
                .id(BigDecimal.valueOf(existing.getId())).version(existing.getMembershipVersion())
                .firstName("sender-updated")
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
        assertThat(response.getFirstName()).isEqualTo("sender-updated");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getApplication()).isEqualTo("Signal");
    }

    @Test
    void testUpdateIdentityWithoutIdentityInApplications() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "receiver".equals(identity.getFirstName()))
                .findFirst()
                .orElseThrow();

        String requestBody = """
                {"id":%d,"firstName":"receiver-updated","relationship":"colleague","version":%d,"requestKey":"%s"}
                """.formatted(existing.getId(), existing.getMembershipVersion(), java.util.UUID.randomUUID()).strip();

        String responseContent = mockMvc.perform(put("/backend/api/v1/identity/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentity response = objectMapper.readValue(responseContent, GenIdentity.class);

        assertThat(response.getId()).isEqualTo(BigDecimal.valueOf(existing.getId()));
        assertThat(response.getFirstName()).isEqualTo("receiver-updated");
        assertThat(response.getRelationship()).isEqualTo("colleague");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getIdentifier()).isEqualTo("+4100000002");
    }

    @Test
    void testUpdateIdentityPreservesExistingIdentityInApplications() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "sender".equals(identity.getFirstName()))
                .findFirst()
                .orElseThrow();

        IdentityInApplicationEntity existingApp = existing.getIdentityInApplications().getFirst();

        GenIdentity request = GenIdentity.builder()
                .requestKey(java.util.UUID.randomUUID().toString())
                .id(BigDecimal.valueOf(existing.getId())).version(existing.getMembershipVersion())
                .firstName("sender-edited")
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
        assertThat(response.getFirstName()).isEqualTo("sender-edited");
        assertThat(response.getIdentityInApplications()).hasSize(1);
        assertThat(response.getIdentityInApplications().getFirst().getId()).isEqualTo(BigDecimal.valueOf(existingApp.getId()));
        assertThat(response.getIdentityInApplications().getFirst().getApplication()).isEqualTo("WhatsApp");
        assertThat(response.getIdentityInApplications().getFirst().getIdentifier()).isEqualTo("+4100000001");
    }

    @Test
    void testDeleteIdentity() throws Exception {
        IdentityEntity existing = identityRepository.findAll().stream()
                .filter(identity -> "receiver".equals(identity.getFirstName()))
                .findFirst()
                .orElseThrow();

        long appId = existing.getIdentityInApplications().getFirst().getId();
        mockMvc.perform(delete("/backend/api/v1/identity/{id}", existing.getId()).param("version", Long.toString(existing.getMembershipVersion())).param("requestKey", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());

        assertThat(identityRepository.findById(existing.getId())).isEmpty();
        assertThat(applications.findById(appId)).isPresent();
        assertThat(applications.findById(appId).orElseThrow().getIdentity()).isNull();
    }

    @Test void normalizesBothNamesAndRejectsInvalidGroupsAndBlankNames() throws Exception {
        var request = new GenIdentity().requestKey(java.util.UUID.randomUUID().toString()).id(BigDecimal.ZERO).firstName("  Theo  ").lastName(" Winter ");
        var content = mockMvc.perform(post("/backend/api/v1/identity").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var saved = objectMapper.readValue(content, GenIdentity.class);
        assertThat(saved.getFirstName()).isEqualTo("Theo");
        assertThat(saved.getLastName()).isEqualTo("Winter");
        assertThat(content).doesNotContain("shortName");
        assertThat(identityRepository.findById(saved.getId().longValue()).orElseThrow().getFullName()).isEqualTo("Theo Winter");
        request.setRequestKey(java.util.UUID.randomUUID().toString());
        request.setIsGroup(true);
        mockMvc.perform(post("/backend/api/v1/identity").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        request.setFirstName(" "); request.setLastName("");
        mockMvc.perform(post("/backend/api/v1/identity").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        request.firstName("Valid contact").isGroup(false).relationship("x".repeat(256));
        mockMvc.perform(post("/backend/api/v1/identity").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());
    }
    @Test void browserWritesRequireReceiptsAndExpectedVersions() throws Exception {
        var input = new GenIdentity().id(BigDecimal.ZERO).firstName("A contact");
        mockMvc.perform(post("/backend/api/v1/identity").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        var existing = identityRepository.findAll().stream().filter(person -> "sender".equals(person.getFirstName())).findFirst().orElseThrow();
        input.id(BigDecimal.valueOf(existing.getId())).requestKey(java.util.UUID.randomUUID().toString());
        mockMvc.perform(put("/backend/api/v1/identity/{id}", existing.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))).andExpect(status().isConflict());
        input.version(existing.getMembershipVersion() + 1);
        mockMvc.perform(put("/backend/api/v1/identity/{id}", existing.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))).andExpect(status().isConflict());
        assertThat(existing.getFirstName()).isEqualTo("sender");
    }

}
