package com.sixtymeters.thereabout.health.transport;

import com.sixtymeters.thereabout.health.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HeartHistoryControllerTest {
    private static final LocalDate DAY = LocalDate.of(1902, 3, 1);
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired HealthMetricRepository metrics;
    @Autowired HealthMetricHeartRateRepository hearts;
    private HealthMetricEntity save(String name, LocalDate date, String value, String units) {
        return metrics.saveAndFlush(HealthMetricEntity.builder().metricName(name).metricDate(date).timestamp(date.atStartOfDay())
                .qty(value == null ? null : new BigDecimal(value)).units(units).build());
    }
    private JsonNode request(String endpoint, String date, String days) throws Exception {
        var response = mvc.perform(get("/backend/api/v1/health/" + endpoint).param("date", date).param("days", days)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return mapper.readTree(response.getContentAsString());
    }
    @Test void joinsHeartRateDetailsAndKeepsGeneralHealthReadCompatible() throws Exception {
        var metric = save("heart_rate", DAY, null, "count/min");
        hearts.saveAndFlush(HealthMetricHeartRateEntity.builder().healthMetric(metric).minValue(new BigDecimal("48"))
                .avgValue(new BigDecimal("72.5")).maxValue(new BigDecimal("136")).build());
        save("resting_heart_rate", DAY, "55", "count/min");
        var result = request("heart-rate", DAY.toString(), "7");
        assertThat(result.path("selectedDay").path("averageBpm").asDouble()).isEqualTo(72.5);
        assertThat(result.path("selectedDay").path("minimumBpm").asDouble()).isEqualTo(48);
        assertThat(result.path("selectedDay").path("maximumBpm").asDouble()).isEqualTo(136);
        assertThat(result.path("selectedDay").path("restingBpm").asDouble()).isEqualTo(55);
        assertThat(result.path("selectedDay").path("recordCount").asInt()).isEqualTo(1);
        assertThat(result.path("selectedDay").path("lastImportedAt").asString()).isNotBlank();
        assertThat(result.path("series").size()).isEqualTo(7);
        assertThat(result.path("series").get(0).path("recordCount").asInt()).isZero();
        var old = mvc.perform(get("/backend/api/v1/health/data").param("fromDate", DAY.toString())).andReturn().getResponse();
        assertThat(old.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(old.getContentAsString()).path("metrics").path("resting_heart_rate").get(0).path("qty").asDouble()).isEqualTo(55);
        assertThat(request("heart-rate", DAY.toString(), "30").path("selectedDay")).isEqualTo(result.path("selectedDay"));
    }
    @Test void hrvHistoryWarmsUpBaselineAndReturnsIdenticalSummariesAcrossRanges() throws Exception {
        for (int i = 0; i < 60; i++) save("heart_rate_variability", DAY.minusDays(i), i < 7 ? "42" : "40", "ms");
        save("heart_rate_variability", DAY.plusDays(1), "999", "ms");
        var week = request("hrv", DAY.toString(), "7");
        var month = request("hrv", DAY.toString(), "30");
        assertThat(week.path("state").asString()).isEqualTo("UP");
        assertThat(week.path("changePercent").asDouble()).isEqualTo(5);
        assertThat(week.path("selectedDay")).isEqualTo(month.path("selectedDay"));
        assertThat(month.path("series").get(0).path("baselineCoverage").asInt()).isEqualTo(30);
        assertThat(month.path("series").get(0).path("trendMs").asDouble()).isEqualTo(40);
        assertThat(month.path("series").get(29).path("averageMs").asDouble()).isEqualTo(42);
    }
    @Test void emptyHistoryReturnsCalendarDaysAndNoInventedData() throws Exception {
        for (String endpoint : new String[]{"heart-rate", "hrv"}) {
            var result = request(endpoint, DAY.minusYears(1).toString(), "30");
            assertThat(result.path("series").size()).isEqualTo(30);
            assertThat(result.path("selectedDay").path("recordCount").asInt()).isZero();
        }
        assertThat(request("hrv", DAY.minusYears(1).toString(), "7").path("state").asString()).isEqualTo("NO_DATA");
    }
    @Test void rejectsUnsupportedRangesAndMalformedOrMissingDates() throws Exception {
        for (String endpoint : new String[]{"heart-rate", "hrv"}) {
            for (String days : new String[]{"0", "90", "-1", "bad"}) {
                assertThat(mvc.perform(get("/backend/api/v1/health/" + endpoint).param("date", DAY.toString()).param("days", days))
                        .andReturn().getResponse().getStatus()).isEqualTo(400);
            }
            assertThat(mvc.perform(get("/backend/api/v1/health/" + endpoint).param("date", "not-a-date").param("days", "7"))
                    .andReturn().getResponse().getStatus()).isEqualTo(400);
            assertThat(mvc.perform(get("/backend/api/v1/health/" + endpoint).param("days", "7"))
                    .andReturn().getResponse().getStatus()).isEqualTo(400);
        }
    }
}
