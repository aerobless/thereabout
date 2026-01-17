package com.sixtymeters.thereabout.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.generated.model.GenUserStatistics;
import com.sixtymeters.thereabout.model.location.LocationHistoryEntity;
import com.sixtymeters.thereabout.model.location.LocationHistoryRepository;
import com.sixtymeters.thereabout.model.location.LocationHistorySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Set up location history entries with different country codes
        LocalDate baseDate = LocalDate.now().minusDays(30);

        // Switzerland entries - multiple days
        locationHistoryRepository.save(LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.of(baseDate.plusDays(1), java.time.LocalTime.of(10, 0)))
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
                .build());

        locationHistoryRepository.save(LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.of(baseDate.plusDays(2), java.time.LocalTime.of(14, 0)))
                .latitude(46.5197)
                .longitude(6.6323)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(380)
                .heading(180)
                .velocity(3)
                .source(LocationHistorySource.THEREABOUT_API)
                .estimatedIsoCountryCode("CH")
                .ignoreEntry(false)
                .build());

        // France entries
        locationHistoryRepository.save(LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.of(baseDate.plusDays(5), java.time.LocalTime.of(12, 0)))
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
                .build());

        locationHistoryRepository.save(LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.of(baseDate.plusDays(6), java.time.LocalTime.of(16, 0)))
                .latitude(45.7640)
                .longitude(4.8357)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(173)
                .heading(45)
                .velocity(2)
                .source(LocationHistorySource.THEREABOUT_API)
                .estimatedIsoCountryCode("FR")
                .ignoreEntry(false)
                .build());

        // Germany entry
        locationHistoryRepository.save(LocationHistoryEntity.builder()
                .timestamp(LocalDateTime.of(baseDate.plusDays(10), java.time.LocalTime.of(11, 0)))
                .latitude(52.5200)
                .longitude(13.4050)
                .horizontalAccuracy(10)
                .verticalAccuracy(5)
                .altitude(34)
                .heading(90)
                .velocity(4)
                .source(LocationHistorySource.THEREABOUT_API)
                .estimatedIsoCountryCode("DE")
                .ignoreEntry(false)
                .build());
    }

    @Test
    void testGetStatistics() throws Exception {
        String responseContent = mockMvc.perform(get("/backend/api/v1/statistics"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GenUserStatistics response = objectMapper.readValue(responseContent, GenUserStatistics.class);

        assertThat(response).isNotNull();
        assertThat(response.getVisitedCountries()).isNotEmpty();

        // Verify that we have statistics for the countries we created
        assertThat(response.getVisitedCountries())
                .extracting("countryIsoCode")
                .contains("CH", "FR", "DE");

        // Verify structure of country statistics
        var chStat = response.getVisitedCountries().stream()
                .filter(c -> "CH".equals(c.getCountryIsoCode()))
                .findFirst()
                .orElseThrow();

        assertThat(chStat)
                .extracting(
                        "countryIsoCode",
                        "countryName",
                        "numberOfDaysSpent"
                )
                .doesNotContainNull();
        assertThat(chStat.getFirstVisit()).isNotNull();
        assertThat(chStat.getLastVisit()).isNotNull();
    }
}
