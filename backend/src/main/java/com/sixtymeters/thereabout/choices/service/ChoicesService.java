package com.sixtymeters.thereabout.choices.service;

import com.sixtymeters.thereabout.choices.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChoicesService {
    private final ChoicesRepository repository;
    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    @Transactional(readOnly = true)
    public GenChoicesHistory history(LocalDate date, Integer days) {
        if (date == null || days == null || (days != 7 && days != 30))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a date and 7 or 30 days");
        var from = date.minusDays(days - 1);
        var scores = repository.findByScoreDateBetweenOrderByScoreDate(from, date).stream()
                .collect(Collectors.toMap(ChoicesScore::getScoreDate, ChoicesScore::getScore));
        var series = from.datesUntil(date.plusDays(1)).map(day -> GenChoicesDay.builder()
                .date(day).score(scores.getOrDefault(day, 0)).build()).toList();
        return GenChoicesHistory.builder().date(date).score(scores.getOrDefault(date, 0))
                .editable(!date.isAfter(LocalDate.now(ZONE))).series(series).build();
    }

    @Transactional
    public GenChoicesDay adjust(LocalDate date, Integer delta) {
        if (date == null || date.isAfter(LocalDate.now(ZONE)) || delta == null || (delta != -1 && delta != 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use today or a past date and a delta of -1 or 1");
        repository.adjust(date, delta);
        return GenChoicesDay.builder().date(date).score(repository.findById(date).orElseThrow().getScore()).build();
    }
}
