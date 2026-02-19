package com.sixtymeters.thereabout.communication.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sixtymeters.thereabout.config.FlexibleLocalDateDeserializer;
import com.sixtymeters.thereabout.config.FlexibleOffsetDateTimeDeserializer;
import com.sixtymeters.thereabout.config.HealthMetricDataDeserializer;
import com.sixtymeters.thereabout.generated.model.GenHealthMetricDataInner;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.health.service.HealthDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthAutoExportImporterTest {

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private ImportProgressService importProgressService;

    private ObjectMapper objectMapper;
    private HealthAutoExportImporter importer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(GenHealthMetricDataInner.class, new HealthMetricDataDeserializer());
        module.addDeserializer(java.time.OffsetDateTime.class, new FlexibleOffsetDateTimeDeserializer());
        module.addDeserializer(LocalDate.class, new FlexibleLocalDateDeserializer());
        objectMapper.registerModule(module);

        importer = new HealthAutoExportImporter(healthDataService, objectMapper, importProgressService);
    }

    @Test
    void importFile_normalizesSpeedToAvgSpeed_andCallsHealthDataService() throws Exception {
        File sampleFile = new File(getClass().getResource("/health-auto-export-sample.json").toURI());
        assertThat(sampleFile).exists();
        File copy = File.createTempFile("health-export-test", ".json");
        try {
            Files.copy(sampleFile.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);

            importer.importFile(copy, null);

            ArgumentCaptor<List> workoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(healthDataService).saveWorkouts(workoutsCaptor.capture());
            List<?> workouts = workoutsCaptor.getValue();
            assertThat(workouts).hasSize(2);

            ArgumentCaptor<List> metricsCaptor = ArgumentCaptor.forClass(List.class);
            verify(healthDataService).saveHealthMetrics(metricsCaptor.capture());
            List<?> metrics = metricsCaptor.getValue();
            assertThat(metrics).hasSize(1);
        } finally {
            if (copy.exists()) {
                copy.delete();
            }
        }
    }
}
