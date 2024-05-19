package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.domain.statistics.CountryVisitInfo;
import com.sixtymeters.thereabout.generated.model.GenCountryStatistic;
import com.sixtymeters.thereabout.model.LocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.recurse.geocoding.reverse.ReverseGeocoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final LocationHistoryRepository locationHistoryRepository;
    private final ReverseGeocoder reverseGeocoder = new ReverseGeocoder();

    public List<GenCountryStatistic> calculateCountryStats() {
        log.info("Calculating country statistics.");

        return getCountryVisitInfo().entrySet().stream()
                .filter(entry -> !(entry.getKey()== null))
                .filter(entry -> !(entry.getKey().isBlank()))
                .map(countryInfo -> {
                    final var countryDetails = reverseGeocoder.countries()
                            .filter(c -> c.iso().equals(countryInfo.getKey()))
                            .findFirst().orElseThrow();

                    final var countryVisitDetails = countryInfo.getValue();

                    return GenCountryStatistic.builder()
                            .countryIsoCode(countryDetails.iso())
                            .countryName(countryDetails.name())
                            .continent(countryDetails.continent())
                            .numberOfDaysSpent(new BigDecimal(countryVisitDetails.getDayCount()))
                            .firstVisit(countryVisitDetails.getFirstVisit())
                            .lastVisit(countryVisitDetails.getLastVisit())
                            .build();
                }).toList();
    }

    private Map<String, CountryVisitInfo> getCountryVisitInfo() {
        List<CountryVisitInfo> results = locationHistoryRepository.countDaysSpentInCountries();
        return results.stream()
                .collect(Collectors.toMap(
                        CountryVisitInfo::getCountry,
                        info -> info
                ));
    }
}
