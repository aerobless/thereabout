package com.sixtymeters.thereabout.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.generated.model.GenAddLocationToListRequest;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryList;
import com.sixtymeters.thereabout.model.location.LocationHistoryEntity;
import com.sixtymeters.thereabout.model.location.LocationHistoryRepository;
import com.sixtymeters.thereabout.model.location.LocationHistorySource;
import com.sixtymeters.thereabout.model.location.LocationListEntity;
import com.sixtymeters.thereabout.model.location.LocationListRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LocationListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocationListRepository locationListRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private LocationListEntity testList;
    private LocationHistoryEntity testLocation1;
    private LocationHistoryEntity testLocation2;

    @BeforeEach
    void setUp() {
        // Set up test locations
        testLocation1 = LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.now())
                .latitude(47.3769)
                .longitude(8.5417)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(400)
                .heading(90)
                .velocity(5)
                .source(LocationHistorySource.THEREABOUT_API)
                .ignoreEntry(false)
                .build();
        testLocation1 = locationHistoryRepository.save(testLocation1);

        testLocation2 = LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.now().plusHours(1))
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
        testLocation2 = locationHistoryRepository.save(testLocation2);

        // Set up test list
        testList = LocationListEntity.builder()
                .name("Test List")
                .locationHistoryEntries(new java.util.ArrayList<>())
                .build();
        testList = locationListRepository.save(testList);
    }

    @Test
    void testCreateLocationHistoryList() throws Exception {
        GenLocationHistoryList listRequest = GenLocationHistoryList.builder()
                .id(BigDecimal.ZERO)
                .name("New List")
                .locationHistoryEntries(new java.util.ArrayList<>())
                .build();

        String responseContent = mockMvc.perform(post("/backend/api/v1/location-history-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(listRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryList response = objectMapper.readValue(responseContent, GenLocationHistoryList.class);

        assertThat(response.getName()).isEqualTo("New List");
        assertThat(response.getId()).isNotNull();
    }

    @Test
    void testGetLocationHistoryLists() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/location-history-list"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryList[] response = objectMapper.readValue(responseContent, GenLocationHistoryList[].class);

        assertThat(response).isNotEmpty();
        assertThat(response[0])
                .extracting("id", "name")
                .containsExactly(
                        BigDecimal.valueOf(testList.getId()),
                        testList.getName()
                );
    }

    @Test
    void testGetLocationHistoryList() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/location-history-list/{id}", testList.getId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenLocationHistoryList response = objectMapper.readValue(responseContent, GenLocationHistoryList.class);

        assertThat(response)
                .extracting("id", "name")
                .containsExactly(
                        BigDecimal.valueOf(testList.getId()),
                        testList.getName()
                );
    }

    @Test
    void testAddLocationToList() throws Exception {
        GenAddLocationToListRequest request = GenAddLocationToListRequest.builder()
                .locationHistoryEntryId(BigDecimal.valueOf(testLocation1.getId()))
                .build();

        mockMvc.perform(post("/backend/api/v1/location-history-list/{id}/location", testList.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // Verify location was added to list
        locationListRepository.flush();
        LocationListEntity updatedList = locationListRepository.findById(testList.getId()).orElseThrow();
        assertThat(updatedList.getLocationHistoryEntries())
                .isNotNull()
                .extracting("id")
                .contains(testLocation1.getId());
    }

    @Test
    void testRemoveLocationFromList() throws Exception {
        // First add a location to the list
        GenAddLocationToListRequest addRequest = GenAddLocationToListRequest.builder()
                .locationHistoryEntryId(BigDecimal.valueOf(testLocation1.getId()))
                .build();

        mockMvc.perform(post("/backend/api/v1/location-history-list/{id}/location", testList.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isNoContent());

        // Then remove it
        GenAddLocationToListRequest removeRequest = GenAddLocationToListRequest.builder()
                .locationHistoryEntryId(BigDecimal.valueOf(testLocation1.getId()))
                .build();

        mockMvc.perform(delete("/backend/api/v1/location-history-list/{id}/location", testList.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(removeRequest)))
                .andExpect(status().isNoContent());

        // Verify location was removed from list
        locationListRepository.flush();
        LocationListEntity updatedList = locationListRepository.findById(testList.getId()).orElseThrow();
        assertThat(updatedList.getLocationHistoryEntries())
                .isNotNull()
                .extracting("id")
                .doesNotContain(testLocation1.getId());
    }

    @Test
    void testDeleteLocationHistoryList() throws Exception {
        mockMvc.perform(delete("/backend/api/v1/location-history-list/{id}", testList.getId()))
                .andExpect(status().isNoContent());

        assertThat(locationListRepository.findById(testList.getId())).isEmpty();
    }

}
