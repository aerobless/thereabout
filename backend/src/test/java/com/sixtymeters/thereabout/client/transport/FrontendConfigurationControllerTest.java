package com.sixtymeters.thereabout.client.transport;

import tools.jackson.databind.json.JsonMapper;
import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.generated.model.GenFileImportStatus;
import com.sixtymeters.thereabout.generated.model.GenFrontendConfigurationResponse;
import com.sixtymeters.thereabout.generated.model.GenTelegramStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "thereabout.telegram.tdlib.api-id=0",
        "thereabout.telegram.tdlib.api-hash="
})
@Transactional
class FrontendConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private JsonMapper objectMapper;

    private String testApiKey;

    @BeforeEach
    void setUp() {
        // Set up test API key
        testApiKey = "test-api-key-12345";
        ConfigurationEntity apiKeyConfig = ConfigurationEntity.builder()
                .configKey(ConfigurationKey.THEREABOUT_API_KEY)
                .configValue(testApiKey)
                .build();
        configurationRepository.save(apiKeyConfig);
    }

    @Test
    void testFileImportStatus() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/config/import-file"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenFileImportStatus response = objectMapper.readValue(responseContent, GenFileImportStatus.class);

        assertThat(response)
                .extracting("status")
                .isNotNull();
        assertThat(response.getProgress()).isNotNull();
        // Status can be IDLE or IN_PROGRESS depending on import state
        assertThat(response.getStatus()).isIn(GenFileImportStatus.StatusEnum.IDLE, GenFileImportStatus.StatusEnum.IN_PROGRESS);
    }

    @Test
    void testGetFrontendConfiguration() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/config"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenFrontendConfigurationResponse response = objectMapper.readValue(responseContent, GenFrontendConfigurationResponse.class);

        assertThat(response)
                .extracting("googleMapsApiKey", "thereaboutApiKey")
                .containsExactly("NO_KEY_NEEDED_FOR_TESTS", testApiKey);

        assertThat(response.getVersionDetails())
                .extracting("version", "branch", "commitRef")
                .doesNotContainNull();
        assertThat(response.getVersionDetails().getCommitTime()).isNotNull();
    }

    @Test
    void testImportFromFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-location-history.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"locations\": []}".getBytes()
        );

        mockMvc.perform(multipart("/backend/api/v1/config/import-file")
                        .file(file)
                        .param("importType", "GOOGLE_MAPS_RECORDS")
                        .param("receiver", "test-receiver"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetTelegramStatus() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/config/telegram"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenTelegramStatus response = objectMapper.readValue(responseContent, GenTelegramStatus.class);
        assertThat(response.getStatus()).isNotNull();
        assertThat(response.getConfigured()).isFalse(); // test profile has no api_id/api_hash
    }

    @Test
    void testDisconnectTelegram() throws Exception {
        mockMvc.perform(delete("/backend/api/v1/config/telegram/disconnect"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testResyncTelegram() throws Exception {
        mockMvc.perform(post("/backend/api/v1/config/telegram/resync"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCancelTelegramResync() throws Exception {
        mockMvc.perform(post("/backend/api/v1/config/telegram/resync/cancel"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testConnectTelegramWithoutConfigFails() throws Exception {
        mockMvc.perform(post("/backend/api/v1/config/telegram/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+1234567890\"}"))
                .andExpect(status().isBadRequest());
    }
}
