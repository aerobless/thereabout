package com.sixtymeters.thereabout.choices;

import com.sixtymeters.thereabout.choices.data.ChoicesRepository;
import com.sixtymeters.thereabout.choices.service.ChoicesService;
import com.sixtymeters.thereabout.generated.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"thereabout.telegram.tdlib.api-id=0", "thereabout.telegram.tdlib.api-hash="})
class ChoicesTest {
    @Autowired ChoicesService service;
    @Autowired ChoicesRepository repository;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    private final LocalDate date = LocalDate.of(1902, 1, 2);

    @BeforeEach @AfterEach void clean() { repository.deleteAllById(List.of(date, date.minusDays(1))); }

    @Test void emptyHistoryIncludesEveryCalendarDayWithoutCreatingRows() {
        var history = service.history(date, 30);
        assertThat(history.getScore()).isZero();
        assertThat(history.getEditable()).isTrue();
        assertThat(history.getSeries()).hasSize(30);
        assertThat(history.getSeries().getFirst().getDate()).isEqualTo(LocalDate.of(1901, 12, 4));
        assertThat(history.getSeries()).allSatisfy(day -> assertThat(day.getScore()).isZero());
        assertThat(repository.findById(date)).isEmpty();
    }

    @Test void signedAdjustmentsPersistAndDaysRemainIndependent() throws Exception {
        for (int delta : new int[]{1, -1, -1}) {
            var result = mvc.perform(post("/backend/api/v1/choices/{date}/adjust", date)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":" + delta + "}"))
                    .andReturn().getResponse();
            assertThat(result.getStatus()).isEqualTo(200);
        }
        service.adjust(date.minusDays(1), 1);
        var response = mvc.perform(get("/backend/api/v1/choices").param("date", date.toString()).param("days", "7"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var history = mapper.readValue(response.getContentAsString(), GenChoicesHistory.class);
        assertThat(history.getScore()).isEqualTo(-1);
        assertThat(history.getSeries()).hasSize(7);
        assertThat(history.getSeries().get(5).getScore()).isEqualTo(1);
        assertThat(repository.findById(date).orElseThrow().getScore()).isEqualTo(-1);
    }

    @Test void concurrentFirstWritesDoNotLosePoints() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> writes = new ArrayList<>();
            for (int i = 0; i < 40; i++) writes.add(() -> { service.adjust(date, 1); return null; });
            for (var result : executor.invokeAll(writes)) result.get(20, TimeUnit.SECONDS);
        }
        assertThat(service.history(date, 7).getScore()).isEqualTo(40);
    }

    @Test void invalidWritesAndRangesAreRejected() throws Exception {
        for (String body : List.of("{}", "{\"delta\":0}", "{\"delta\":2}", "{\"delta\":-2}", "{\"delta\":null}", "{\"delta\":1.5}", "{\"delta\":-1.5}")) {
            assertThat(mvc.perform(post("/backend/api/v1/choices/{date}/adjust", date)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(mvc.perform(get("/backend/api/v1/choices").param("date", "bad").param("days", "7"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(get("/backend/api/v1/choices").param("date", date.toString()).param("days", "8"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        var tomorrow = LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(1);
        assertThat(service.history(tomorrow, 7).getEditable()).isFalse();
        assertThat(mvc.perform(post("/backend/api/v1/choices/{date}/adjust", tomorrow)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":1}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(repository.findById(date)).isEmpty();
    }
}
