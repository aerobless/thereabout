package com.sixtymeters.thereabout.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.generated.model.GenTrip;
import com.sixtymeters.thereabout.model.LocationHistoryEntity;
import com.sixtymeters.thereabout.model.LocationHistoryRepository;
import com.sixtymeters.thereabout.model.LocationHistorySource;
import com.sixtymeters.thereabout.model.TripEntity;
import com.sixtymeters.thereabout.model.TripsRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripsRepository tripsRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private TripEntity testTrip;

    @BeforeEach
    void setUp() {
        // Set up test trip
        testTrip = TripEntity.builder()
                .start(LocalDate.now().minusDays(10))
                .end(LocalDate.now().minusDays(5))
                .title("Test Trip")
                .description("A test trip")
                .build();
        testTrip = tripsRepository.save(testTrip);

        // Set up location history entries with country codes for the trip period
        LocationHistoryEntity location1 = LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.now().minusDays(8))
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
        locationHistoryRepository.save(location1);

        LocationHistoryEntity location2 = LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.now().minusDays(7))
                .latitude(48.8566)
                .longitude(2.3522)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(35)
                .heading(0)
                .velocity(0)
                .source(LocationHistorySource.THEREABOUT_API)
                .estimatedIsoCountryCode("FR")
                .ignoreEntry(false)
                .build();
        locationHistoryRepository.save(location2);
    }

    @Test
    void testAddTrip() throws Exception {
        // When adding, provide a dummy id (controller will ignore it)
        GenTrip tripRequest = GenTrip.builder()
                .id(BigDecimal.ZERO)
                .start(LocalDate.now().plusDays(1))
                .end(LocalDate.now().plusDays(5))
                .title("New Trip")
                .description("A new trip")
                .build();

        String responseContent = mockMvc.perform(post("/backend/api/v1/trip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenTrip response = objectMapper.readValue(responseContent, GenTrip.class);

        assertThat(response)
                .extracting(
                        "start",
                        "end",
                        "title",
                        "description"
                )
                .containsExactly(
                        tripRequest.getStart(),
                        tripRequest.getEnd(),
                        tripRequest.getTitle(),
                        tripRequest.getDescription()
                );
        assertThat(response.getId()).isNotNull();
    }

    @Test
    void testGetTrips() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/trip"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenTrip[] response = objectMapper.readValue(responseContent, GenTrip[].class);

        assertThat(response).isNotEmpty();
        assertThat(response[0])
                .extracting(
                        "id",
                        "start",
                        "end",
                        "title",
                        "description"
                )
                .containsExactly(
                        BigDecimal.valueOf(testTrip.getId()),
                        testTrip.getStart(),
                        testTrip.getEnd(),
                        testTrip.getTitle(),
                        testTrip.getDescription()
                );
    }

    @Test
    void testUpdateTrip() throws Exception {
        GenTrip updateRequest = GenTrip.builder()
                .id(BigDecimal.valueOf(testTrip.getId()))
                .start(LocalDate.now().minusDays(15))
                .end(LocalDate.now().minusDays(8))
                .title("Updated Trip")
                .description("An updated trip")
                .build();

        String responseContent = mockMvc.perform(put("/backend/api/v1/trip/{id}", testTrip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenTrip response = objectMapper.readValue(responseContent, GenTrip.class);

        assertThat(response)
                .extracting(
                        "id",
                        "start",
                        "end",
                        "title",
                        "description"
                )
                .containsExactly(
                        BigDecimal.valueOf(testTrip.getId()),
                        updateRequest.getStart(),
                        updateRequest.getEnd(),
                        updateRequest.getTitle(),
                        updateRequest.getDescription()
                );
    }

    @Test
    void testDeleteTrip() throws Exception {
        mockMvc.perform(delete("/backend/api/v1/trip/{id}", testTrip.getId()))
                .andExpect(status().isNoContent());

        assertThat(tripsRepository.findById(testTrip.getId())).isEmpty();
    }
}
