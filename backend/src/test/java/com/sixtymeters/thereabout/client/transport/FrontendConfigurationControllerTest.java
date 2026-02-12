package com.sixtymeters.thereabout.client.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.generated.model.GenFileImportStatus;
import com.sixtymeters.thereabout.generated.model.GenFrontendConfigurationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FrontendConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
                        .param("importType", "GOOGLE_MAPS_RECORDS"))
                .andExpect(status().isNoContent());
    }
}
