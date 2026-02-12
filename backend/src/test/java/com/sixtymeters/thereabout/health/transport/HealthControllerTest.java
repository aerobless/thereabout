package com.sixtymeters.thereabout.health.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixtymeters.thereabout.generated.model.GenSubmitHealthDataRequest;
import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.health.data.HealthMetricEntity;
import com.sixtymeters.thereabout.health.data.HealthMetricHeartRateEntity;
import com.sixtymeters.thereabout.health.data.HealthMetricRepository;
import com.sixtymeters.thereabout.health.data.HealthMetricHeartRateRepository;
import com.sixtymeters.thereabout.health.data.WorkoutEntity;
import com.sixtymeters.thereabout.health.data.WorkoutRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HealthMetricRepository healthMetricRepository;

    @Autowired
    private HealthMetricHeartRateRepository healthMetricHeartRateRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

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
    void testSubmitHealthData() throws Exception {
        GenSubmitHealthDataRequest request = GenSubmitHealthDataRequest.builder().build();

        mockMvc.perform(post("/backend/api/v1/health")
                        .header("Authorization", "Bearer " + testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitHealthDataUnauthorized() throws Exception {
        GenSubmitHealthDataRequest request = GenSubmitHealthDataRequest.builder().build();

        mockMvc.perform(post("/backend/api/v1/health")
                        .header("Authorization", "Bearer wrong-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSubmitHealthDataWithExampleJson() throws Exception {
        // This test uses the structure from the example JSON file
        // The actual JSON would be loaded from a file in a real scenario
        String exampleJson = """
                {
                  "data": {
                    "metrics": [
                      {
                        "name": "heart_rate",
                        "units": "count/min",
                        "data": [
                          {
                            "date": "2026-01-17 00:00:00 +0100",
                            "Min": 47,
                            "Avg": 67.99,
                            "Max": 146,
                            "source": "Theo's Apple Watch|Withings"
                          }
                        ]
                      }
                    ],
                    "workouts": [
                      {
                        "id": "34D54145-2BAC-452D-B719-EBA375CABC8D",
                        "name": "Outdoor Walk",
                        "start": "2026-01-17 17:04:06 +0100",
                        "end": "2026-01-17 17:19:45 +0100",
                        "duration": 939,
                        "location": "Outdoor",
                        "activeEnergyBurned": {
                          "qty": 54.21,
                          "units": "kcal"
                        },
                        "intensity": {
                          "qty": 4.27,
                          "units": "kcal/hr*kg"
                        },
                        "distance": {
                          "qty": 0.59,
                          "units": "mi"
                        }
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/backend/api/v1/health")
                        .header("Authorization", "Bearer " + testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson))
                .andExpect(status().isCreated());
    }

    @Test
    void testUpdateHealthData() throws Exception {
        String initialJson = """
                {
                  "data": {
                    "metrics": [
                      {
                        "name": "heart_rate",
                        "units": "count/min",
                        "data": [
                          {
                            "date": "2026-01-18 00:00:00 +0100",
                            "Min": 50,
                            "Avg": 70,
                            "Max": 150,
                            "source": "Test Source"
                          }
                        ]
                      }
                    ],
                    "workouts": [
                      {
                        "id": "TEST-WORKOUT-001",
                        "name": "Test Run",
                        "start": "2026-01-18 10:00:00 +0100",
                        "end": "2026-01-18 10:30:00 +0100",
                        "duration": 1800,
                        "location": "Outdoor",
                        "activeEnergyBurned": {
                          "qty": 100.0,
                          "units": "kcal"
                        }
                      }
                    ]
                  }
                }
                """;

        // Submit initial data
        mockMvc.perform(post("/backend/api/v1/health")
                        .header("Authorization", "Bearer " + testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialJson))
                .andExpect(status().isCreated());

        // Verify initial data was saved
        LocalDate testDate = LocalDate.of(2026, 1, 18);
        List<HealthMetricEntity> initialMetrics = healthMetricRepository.findAll();
        assertThat(initialMetrics).isNotEmpty();
        
        HealthMetricEntity heartRateMetric = initialMetrics.stream()
                .filter(m -> "heart_rate".equals(m.getMetricName()) && testDate.equals(m.getMetricDate()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Heart rate metric not found"));
        
        // Find heart rate detail by querying all and filtering
        List<HealthMetricHeartRateEntity> allHeartRates = healthMetricHeartRateRepository.findAll();
        HealthMetricHeartRateEntity heartRateDetail = allHeartRates.stream()
                .filter(hr -> hr.getHealthMetric().getId().equals(heartRateMetric.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Heart rate detail not found"));
        
        assertThat(heartRateDetail.getMinValue()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(heartRateDetail.getAvgValue()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(heartRateDetail.getMaxValue()).isEqualByComparingTo(new BigDecimal("150"));
        
        WorkoutEntity initialWorkout = workoutRepository.findById("TEST-WORKOUT-001")
                .orElseThrow(() -> new AssertionError("Initial workout not found"));
        assertThat(initialWorkout.getActiveEnergyBurnedQty()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(initialWorkout.getName()).isEqualTo("Test Run");

        // Submit updated data with different values
        String updatedJson = """
                {
                  "data": {
                    "metrics": [
                      {
                        "name": "heart_rate",
                        "units": "count/min",
                        "data": [
                          {
                            "date": "2026-01-18 00:00:00 +0100",
                            "Min": 45,
                            "Avg": 75,
                            "Max": 160,
                            "source": "Updated Source"
                          }
                        ]
                      }
                    ],
                    "workouts": [
                      {
                        "id": "TEST-WORKOUT-001",
                        "name": "Updated Test Run",
                        "start": "2026-01-18 10:00:00 +0100",
                        "end": "2026-01-18 10:30:00 +0100",
                        "duration": 1800,
                        "location": "Indoor",
                        "activeEnergyBurned": {
                          "qty": 150.0,
                          "units": "kcal"
                        }
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/backend/api/v1/health")
                        .header("Authorization", "Bearer " + testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedJson))
                .andExpect(status().isCreated());

        // Verify data was updated (not duplicated)
        List<HealthMetricEntity> updatedMetrics = healthMetricRepository.findAll();
        long heartRateMetricsCount = updatedMetrics.stream()
                .filter(m -> "heart_rate".equals(m.getMetricName()) && testDate.equals(m.getMetricDate()))
                .count();
        assertThat(heartRateMetricsCount).isEqualTo(1);

        HealthMetricEntity updatedHeartRateMetric = updatedMetrics.stream()
                .filter(m -> "heart_rate".equals(m.getMetricName()) && testDate.equals(m.getMetricDate()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Updated heart rate metric not found"));

        // Find updated heart rate detail
        List<HealthMetricHeartRateEntity> allUpdatedHeartRates = healthMetricHeartRateRepository.findAll();
        HealthMetricHeartRateEntity updatedHeartRateDetail = allUpdatedHeartRates.stream()
                .filter(hr -> hr.getHealthMetric().getId().equals(updatedHeartRateMetric.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Updated heart rate detail not found"));

        // Assert updated values
        assertThat(updatedHeartRateDetail.getMinValue()).isEqualByComparingTo(new BigDecimal("45"));
        assertThat(updatedHeartRateDetail.getAvgValue()).isEqualByComparingTo(new BigDecimal("75"));
        assertThat(updatedHeartRateDetail.getMaxValue()).isEqualByComparingTo(new BigDecimal("160"));
        // Note: Source is only set for quantity metrics, not for heart rate metrics

        // Assert workout was updated
        WorkoutEntity updatedWorkout = workoutRepository.findById("TEST-WORKOUT-001")
                .orElseThrow(() -> new AssertionError("Updated workout not found"));
        assertThat(updatedWorkout.getActiveEnergyBurnedQty()).isEqualByComparingTo(new BigDecimal("150.0"));
        assertThat(updatedWorkout.getName()).isEqualTo("Updated Test Run");
        assertThat(updatedWorkout.getLocation()).isEqualTo("Indoor");
    }
}
