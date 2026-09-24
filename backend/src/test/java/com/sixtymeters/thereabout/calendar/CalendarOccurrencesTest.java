package com.sixtymeters.thereabout.calendar;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.sixtymeters.thereabout.calendar.service.CalendarOccurrences;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CalendarOccurrencesTest {
    static Event timed(String id,String start,String end) {
        return new Event().setId(id).setSummary("Meeting").setStatus("confirmed")
                .setStart(new EventDateTime().setDateTime(new DateTime(start)).setTimeZone("Europe/Zurich"))
                .setEnd(new EventDateTime().setDateTime(new DateTime(end)).setTimeZone("Europe/Zurich"));
    }
    static Event allDay(String id,String start,String end) {
        return new Event().setId(id).setSummary("Holiday").setStatus("confirmed")
                .setStart(new EventDateTime().setDate(new DateTime(start)))
                .setEnd(new EventDateTime().setDate(new DateTime(end)));
    }
    @Test void endlessWeeklyRecurrenceKeepsWallTimeAcrossDst() {
        Event e=timed("series","2026-03-22T09:00:00+01:00","2026-03-22T10:00:00+01:00").setRecurrence(List.of("RRULE:FREQ=WEEKLY"));
        var result=CalendarOccurrences.spans(e,LocalDate.parse("2026-03-29"),ZoneId.of("Europe/Zurich"),"Europe/Zurich");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().start()).isEqualTo(Instant.parse("2026-03-29T07:00:00Z"));
    }
    @Test void recurrenceHasNoHistoryCutoff() {
        Event e=timed("old","2000-01-01T09:00:00+01:00","2000-01-01T10:00:00+01:00").setRecurrence(List.of("RRULE:FREQ=DAILY"));
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2035-09-24"),ZoneId.of("Europe/Zurich"),"Europe/Zurich")).hasSize(1);
    }
    @Test void exclusionsAndAdditionalDatesAreApplied() {
        Event e=timed("series","2026-09-21T09:00:00+02:00","2026-09-21T10:00:00+02:00")
                .setRecurrence(List.of("RRULE:FREQ=WEEKLY;COUNT=3","EXDATE;TZID=Europe/Zurich:20260928T090000","RDATE;TZID=Europe/Zurich:20260929T090000"));
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-09-28"),ZoneId.of("Europe/Zurich"),"Europe/Zurich")).isEmpty();
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-09-29"),ZoneId.of("Europe/Zurich"),"Europe/Zurich")).hasSize(1);
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-10-12"),ZoneId.of("Europe/Zurich"),"Europe/Zurich")).isEmpty();
    }
    @Test void allDayDatesUseExclusiveEndInViewerZone() {
        Event e=allDay("trip","2026-09-23","2026-09-25");
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-09-24"),ZoneId.of("America/New_York"),"Europe/Zurich")).hasSize(1);
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-09-25"),ZoneId.of("America/New_York"),"Europe/Zurich")).isEmpty();
    }
    @Test void recurringMultiDayAllDaySpansMidnight() {
        Event e=allDay("trip","2026-09-01","2026-09-04").setRecurrence(List.of("RRULE:FREQ=WEEKLY"));
        var result=CalendarOccurrences.spans(e,LocalDate.parse("2026-09-09"),ZoneId.of("Europe/Zurich"),"Europe/Zurich");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().originalStart()).isEqualTo("2026-09-08");
    }
    @Test void recurringTimedEventOverlapsNextDay() {
        Event e=timed("overnight","2026-09-21T23:00:00+02:00","2026-09-22T02:00:00+02:00").setRecurrence(List.of("RRULE:FREQ=WEEKLY"));
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-09-29"),ZoneId.of("Europe/Zurich"),"Europe/Zurich")).hasSize(1);
    }
    @Test void midnightEndDoesNotLeakIntoNextDay() {
        Event e=timed("night","2026-09-23T23:00:00+02:00","2026-09-24T00:00:00+02:00");
        assertThat(CalendarOccurrences.spans(e,LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich"),"Europe/Zurich")).isEmpty();
    }
    @Test void exceptionKeysNormalizeOffsets() {
        assertThat(CalendarOccurrences.canonical("2026-09-24T09:00:00+02:00")).isEqualTo(CalendarOccurrences.canonical("2026-09-24T07:00:00Z"));
    }
}
