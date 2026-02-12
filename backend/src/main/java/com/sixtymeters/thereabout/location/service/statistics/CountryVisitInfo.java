package com.sixtymeters.thereabout.location.service.statistics;

import lombok.Getter;

import java.sql.Date;
import java.time.LocalDate;

@Getter
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
