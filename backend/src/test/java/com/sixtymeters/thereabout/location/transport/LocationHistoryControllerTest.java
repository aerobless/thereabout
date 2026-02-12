package com.sixtymeters.thereabout.location.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.generated.model.GenAddGeoJsonLocation200Response;
import com.sixtymeters.thereabout.generated.model.GenAddGeoJsonLocationRequest;
import com.sixtymeters.thereabout.generated.model.GenGeoJsonLocation;
import com.sixtymeters.thereabout.generated.model.GenGeoJsonLocationGeometry;
import com.sixtymeters.thereabout.generated.model.GenGeoJsonLocationProperties;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.location.data.LocationHistoryEntity;
import com.sixtymeters.thereabout.location.data.LocationHistoryRepository;
import com.sixtymeters.thereabout.location.data.LocationHistorySource;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LocationHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String testApiKey;
    private LocationHistoryEntity testLocation;

    @BeforeEach
    void setUp() {
        // Set up test API key
        testApiKey = "test-api-key-12345";
        ConfigurationEntity apiKeyConfig = ConfigurationEntity.builder()
                .configKey(ConfigurationKey.THEREABOUT_API_KEY)
                .configValue(testApiKey)
                .build();
        configurationRepository.save(apiKeyConfig);

        // Set up test location
        testLocation = LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.now())
                .latitude(47.3769)
                .longitude(8.5417)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(400)
                .heading(90)
                .velocity(5)
                .source(LocationHistorySource.THEREABOUT_API)
                .estimatedIsoCountryCode("CH")
                .ignoreEntry(false)
                .build();
        testLocation = locationHistoryRepository.save(testLocation);
    }

    @Test
    void testAddGeoJsonLocation() throws Exception {
        GenGeoJsonLocation geoJsonLocation = GenGeoJsonLocation.builder()
                .type(GenGeoJsonLocation.TypeEnum.FEATURE)
                .geometry(GenGeoJsonLocationGeometry.builder()
                        .type(GenGeoJsonLocationGeometry.TypeEnum.POINT)
                        .coordinates(List.of(BigDecimal.valueOf(-122.030581), BigDecimal.valueOf(37.331800)))
                        .build())
                .properties(GenGeoJsonLocationProperties.builder()
                        .timestamp(OffsetDateTime.now())
                        .altitude(BigDecimal.ZERO)
                        .speed(BigDecimal.valueOf(4))
                        .course(BigDecimal.valueOf(90))
                        .horizontalAccuracy(BigDecimal.valueOf(30))
                        .verticalAccuracy(BigDecimal.valueOf(-1))
                        .build())
                .build();

        GenAddGeoJsonLocationRequest request = GenAddGeoJsonLocationRequest.builder()
                .locations(List.of(geoJsonLocation))
                .build();

        String responseContent = mockMvc.perform(post("/backend/api/v1/location/geojson")
                        .header("Authorization", "Bearer " + testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenAddGeoJsonLocation200Response response = objectMapper.readValue(responseContent, GenAddGeoJsonLocation200Response.class);

        assertThat(response)
                .extracting("result")
                .isEqualTo("ok");
    }

    @Test
    void testAddLocation() throws Exception {
        // When adding, provide a dummy id (controller will set it to null)
        // The OpenAPI schema requires id, but controller handles it
        GenLocationHistoryEntry locationEntry = GenLocationHistoryEntry.builder()
                .id(BigDecimal.ZERO)
                .timestamp(OffsetDateTime.now())
                .latitude(46.5197)
                .longitude(6.6323)
                .horizontalAccuracy(BigDecimal.valueOf(15))
                .verticalAccuracy(BigDecimal.valueOf(8))
                .altitude(BigDecimal.valueOf(380))
                .heading(BigDecimal.valueOf(180))
                .velocity(BigDecimal.valueOf(3))
                .build();

        String responseContent = mockMvc.perform(post("/backend/api/v1/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationEntry)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryEntry response = objectMapper.readValue(responseContent, GenLocationHistoryEntry.class);

        assertThat(response)
                .extracting(
                        "latitude",
                        "longitude",
                        "horizontalAccuracy",
                        "verticalAccuracy",
                        "altitude",
                        "heading",
                        "velocity"
                )
                .containsExactly(
                        46.5197,
                        6.6323,
                        BigDecimal.valueOf(15),
                        BigDecimal.valueOf(8),
                        BigDecimal.valueOf(380),
                        BigDecimal.valueOf(180),
                        BigDecimal.valueOf(3)
                );
        assertThat(response.getId()).isNotNull();
    }

    @Test
    void testDeleteLocations() throws Exception {
        LocationHistoryEntity location2 = LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.now())
                .latitude(48.8566)
                .longitude(2.3522)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(35)
                .heading(0)
                .velocity(0)
                .source(LocationHistorySource.THEREABOUT_API)
                .ignoreEntry(false)
                .build();
        location2 = locationHistoryRepository.save(location2);

        List<BigDecimal> ids = List.of(
                BigDecimal.valueOf(testLocation.getId()),
                BigDecimal.valueOf(location2.getId())
        );

        mockMvc.perform(delete("/backend/api/v1/location")
                        .param("ids", String.valueOf(testLocation.getId()), String.valueOf(location2.getId())))
                .andExpect(status().isNoContent());

        assertThat(locationHistoryRepository.findById(testLocation.getId())).isEmpty();
        assertThat(locationHistoryRepository.findById(location2.getId())).isEmpty();
    }

    @Test
    void testGetLocations() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/location"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryEntry[] response = objectMapper.readValue(responseContent, GenLocationHistoryEntry[].class);

        assertThat(response).isNotEmpty();
        // Find the test location in the response
        var testLocationResponse = java.util.Arrays.stream(response)
                .filter(r -> r.getId().equals(BigDecimal.valueOf(testLocation.getId())))
                .findFirst()
                .orElseThrow();
        assertThat(testLocationResponse)
                .extracting("id", "latitude", "longitude")
                .containsExactly(
                        BigDecimal.valueOf(testLocation.getId()),
                        testLocation.getLatitude(),
                        testLocation.getLongitude()
                );
    }

    @Test
    void testGetLocationsWithDateFilter() throws Exception {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().plusDays(1);

        String responseContent = mockMvc.perform(get("/backend/api/v1/location")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryEntry[] response = objectMapper.readValue(responseContent, GenLocationHistoryEntry[].class);

        assertThat(response).isNotEmpty();
    }

    @Test
    void testGetSparseLocations() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/location/sparse"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.sixtymeters.thereabout.generated.model.GenSparseLocationHistoryEntry[] response =
                objectMapper.readValue(responseContent, com.sixtymeters.thereabout.generated.model.GenSparseLocationHistoryEntry[].class);

        // Sparse locations use random sampling, so results may be empty
        // Just verify the endpoint works and returns an array
        assertThat(response).isNotNull();
        // If there are results, verify structure
        if (response.length > 0) {
            assertThat(response[0])
                    .extracting("latitude", "longitude")
                    .doesNotContainNull();
        }
    }

    @Test
    void testUpdateLocation() throws Exception {
        GenLocationHistoryEntry updateRequest = GenLocationHistoryEntry.builder()
                .id(BigDecimal.valueOf(testLocation.getId()))
                .timestamp(OffsetDateTime.now())
                .latitude(50.1109)
                .longitude(8.6821)
                .horizontalAccuracy(BigDecimal.valueOf(20))
                .verticalAccuracy(BigDecimal.valueOf(10))
                .altitude(BigDecimal.valueOf(100))
                .heading(BigDecimal.valueOf(270))
                .velocity(BigDecimal.valueOf(10))
                .build();

        String responseContent = mockMvc.perform(put("/backend/api/v1/location/{id}", testLocation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryEntry response = objectMapper.readValue(responseContent, GenLocationHistoryEntry.class);

        assertThat(response.getId()).isEqualTo(BigDecimal.valueOf(testLocation.getId()));
        // Verify the update worked - check that key values are updated
        assertThat(response.getLatitude()).isEqualTo(50.1109);
        assertThat(response.getLongitude()).isEqualTo(8.6821);
        // Verify other updated fields
        assertThat(response.getAltitude()).isEqualTo(BigDecimal.valueOf(100));
        // Note: heading and velocity are NOT updated by the service, they remain at original values
        assertThat(response.getHeading()).isEqualTo(BigDecimal.valueOf(testLocation.getHeading()));
        assertThat(response.getVelocity()).isEqualTo(BigDecimal.valueOf(testLocation.getVelocity()));
        // Note: horizontalAccuracy and verticalAccuracy are set to MANUAL_ACCURACY (0) by the service
        assertThat(response.getHorizontalAccuracy()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.getVerticalAccuracy()).isEqualTo(BigDecimal.ZERO);
    }
}
