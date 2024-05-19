package com.sixtymeters.thereabout.domain.statistics;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Date;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public final class CountryVisitInfo {
    private final String country;
    private final Long dayCount;
    private final LocalDate firstVisit;
    private final LocalDate lastVisit;

    public CountryVisitInfo(String country, Long dayCount, Date firstVisit, Date lastVisit) {
        this.country = country;
        this.dayCount = dayCount;
        this.firstVisit = firstVisit.toLocalDate();
        this.lastVisit = lastVisit.toLocalDate();
    }
}
