package com.sixtymeters.thereabout.communication.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import com.sixtymeters.thereabout.generated.model.GenIdentityInApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentityInApplicationControllerTest {

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

    private IdentityEntity identity;
    private IdentityInApplicationEntity unlinkedAppIdentity;
    private IdentityInApplicationEntity linkedAppIdentity;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        identityInApplicationRepository.deleteAll();
        identityRepository.deleteAll();

        identity = IdentityEntity.builder()
                .shortName("johndoe")
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .relationship("friend")
                .build();
        identity = identityRepository.save(identity);

        unlinkedAppIdentity = IdentityInApplicationEntity.builder()
                .application("WhatsApp")
                .identifier("+41700000001")
                .build();
        unlinkedAppIdentity = identityInApplicationRepository.save(unlinkedAppIdentity);

        linkedAppIdentity = IdentityInApplicationEntity.builder()
                .identity(identity)
                .application("Telegram")
                .identifier("@johndoe")
                .build();
        linkedAppIdentity = identityInApplicationRepository.save(linkedAppIdentity);
    }

    @Test
    void testGetUnlinkedReturnsOnlyUnlinkedAppIdentities() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/identity-in-application/unlinked"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentityInApplication[] response = objectMapper.readValue(responseContent, GenIdentityInApplication[].class);

        assertThat(response).hasSize(1);
        assertThat(response[0].getApplication()).isEqualTo("WhatsApp");
        assertThat(response[0].getIdentifier()).isEqualTo("+41700000001");
    }

    @Test
    void testLinkAppIdentityToIdentity() throws Exception {
        String responseContent = mockMvc.perform(
                        put("/backend/api/v1/identity-in-application/{id}/link/{identityId}",
                                unlinkedAppIdentity.getId(), identity.getId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentityInApplication response = objectMapper.readValue(responseContent, GenIdentityInApplication.class);

        assertThat(response.getApplication()).isEqualTo("WhatsApp");
        assertThat(response.getIdentifier()).isEqualTo("+41700000001");

        // Verify it no longer appears in unlinked list
        String unlinkedContent = mockMvc.perform(get("/backend/api/v1/identity-in-application/unlinked"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentityInApplication[] unlinkedResponse = objectMapper.readValue(unlinkedContent, GenIdentityInApplication[].class);
        assertThat(unlinkedResponse).isEmpty();
    }

    @Test
    void testUnlinkAppIdentityFromIdentity() throws Exception {
        String responseContent = mockMvc.perform(
                        put("/backend/api/v1/identity-in-application/{id}/unlink",
                                linkedAppIdentity.getId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentityInApplication response = objectMapper.readValue(responseContent, GenIdentityInApplication.class);

        assertThat(response.getApplication()).isEqualTo("Telegram");
        assertThat(response.getIdentifier()).isEqualTo("@johndoe");

        // Verify it now appears in unlinked list
        String unlinkedContent = mockMvc.perform(get("/backend/api/v1/identity-in-application/unlinked"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenIdentityInApplication[] unlinkedResponse = objectMapper.readValue(unlinkedContent, GenIdentityInApplication[].class);
        assertThat(unlinkedResponse).hasSize(2);
    }

    @Test
    void testLinkNonExistentAppIdentityReturns404() throws Exception {
        mockMvc.perform(put("/backend/api/v1/identity-in-application/{id}/link/{identityId}", 99999, identity.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testLinkToNonExistentIdentityReturns404() throws Exception {
        mockMvc.perform(put("/backend/api/v1/identity-in-application/{id}/link/{identityId}", unlinkedAppIdentity.getId(), 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUnlinkNonExistentAppIdentityReturns404() throws Exception {
        mockMvc.perform(put("/backend/api/v1/identity-in-application/{id}/unlink", 99999))
                .andExpect(status().isNotFound());
    }
}
