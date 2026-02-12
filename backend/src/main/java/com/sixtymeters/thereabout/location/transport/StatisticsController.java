package com.sixtymeters.thereabout.location.transport;

import com.sixtymeters.thereabout.location.service.StatisticsService;
import com.sixtymeters.thereabout.generated.api.StatisticsApi;
import com.sixtymeters.thereabout.generated.model.GenUserStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StatisticsController implements StatisticsApi {

    private final StatisticsService statisticsService;

    @Override
    public ResponseEntity<GenUserStatistics> getStatistics() {
        GenUserStatistics userStatistics = GenUserStatistics.builder().build();
        userStatistics.visitedCountries(statisticsService.calculateCountryStats());

        return ResponseEntity.ok(userStatistics);
    }
}
