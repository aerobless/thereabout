package com.sixtymeters.thereabout.client.transport;

import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.generated.model.GenPreferences;
import com.sixtymeters.thereabout.generated.model.GenWeightProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"thereabout.telegram.tdlib.api-id=0", "thereabout.telegram.tdlib.api-hash="})
@Transactional
class PreferencesControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired ConfigurationRepository repository;
    private final LocalDate started = LocalDate.now().minusDays(40);

    @BeforeEach void setup() {
        repository.saveAll(List.of(
                ConfigurationEntity.builder().configKey(ConfigurationKey.WEIGHT_GOAL_KG).configValue("75.0").build(),
                ConfigurationEntity.builder().configKey(ConfigurationKey.WEIGHT_GOAL_STARTED_ON).configValue(started.toString()).build()));
    }
    private GenPreferences get() throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/backend/api/v1/preferences")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return mapper.readValue(response.getContentAsString(), GenPreferences.class);
    }
    @Test void exposesOnlyWeightPreferences() throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/backend/api/v1/preferences")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(response.getContentAsString()).size()).isEqualTo(2);
        assertThat(get().getWeightGoalKg()).isEqualByComparingTo("75");
        assertThat(get().getWeightGoalStartedOn()).isEqualTo(started);
    }
    @Test void changedGoalPersistsAndStartsTodayButSameGoalPreservesChallenge() throws Exception {
        var same = mvc.perform(put("/backend/api/v1/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"weightGoalKg\":75.00}")).andReturn().getResponse();
        assertThat(same.getStatus()).isEqualTo(200);
        assertThat(get().getWeightGoalStartedOn()).isEqualTo(started);
        var changed = mvc.perform(put("/backend/api/v1/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"weightGoalKg\":78.2}")).andReturn().getResponse();
        assertThat(changed.getStatus()).isEqualTo(200);
        assertThat(get().getWeightGoalKg()).isEqualByComparingTo("78.2");
        assertThat(get().getWeightGoalStartedOn()).isEqualTo(LocalDate.now());
        assertThat(repository.findById(ConfigurationKey.WEIGHT_GOAL_KG).orElseThrow().getConfigValue()).isEqualTo("78.2");
    }
    @Test void finiteLargeValuesFitConfigurationStorage() throws Exception {
        var response = mvc.perform(put("/backend/api/v1/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"weightGoalKg\":1e300}")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(get().getWeightGoalKg()).isEqualByComparingTo("1e300");
    }
    @Test void invalidGoalsLeaveSavedValuesIntact() throws Exception {
        for (String body : List.of("{}", "{\"weightGoalKg\":null}", "{\"weightGoalKg\":0}", "{\"weightGoalKg\":-2}",
                "{\"weightGoalKg\":75.12}", "{\"weightGoalKg\":1e999}", "{\"weightGoalKg\":\"NaN\"}")) {
            var response = mvc.perform(put("/backend/api/v1/preferences").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            assertThat(response.getStatus()).as(body).isEqualTo(400);
            assertThat(get().getWeightGoalKg()).isEqualByComparingTo("75");
            assertThat(get().getWeightGoalStartedOn()).isEqualTo(started);
        }
    }
    @Test void historicalProgressUsesCurrentPreferencesAndValidatesRange() throws Exception {
        LocalDate date = started.minusDays(1);
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/backend/api/v1/health/weight-progress")
                .param("date", date.toString()).param("days", "7")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var result = mapper.readValue(response.getContentAsString(), GenWeightProgress.class);
        assertThat(result.getState()).isEqualTo(GenWeightProgress.StateEnum.BEFORE_CHALLENGE);
        assertThat(result.getBestGapKg()).isNull();
        assertThat(result.getSeries()).hasSize(7);
        assertThat(result.getPreferences().getWeightGoalKg()).isEqualByComparingTo("75");
        var invalid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/backend/api/v1/health/weight-progress")
                .param("date", date.toString()).param("days", "14")).andReturn().getResponse();
        assertThat(invalid.getStatus()).isEqualTo(400);
    }
}
