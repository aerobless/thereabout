package com.sixtymeters.thereabout.calendar;

import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import com.sixtymeters.thereabout.calendar.service.CalendarOccurrences;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UpcomingCalendarTest {
    private final CalendarStore store=mock(CalendarStore.class);
    private final CalendarStore.CalendarRow calendar=mock(CalendarStore.CalendarRow.class);
    private final ZoneId zone=ZoneId.of("Europe/Zurich");
    private final Instant now=Instant.parse("2026-09-25T10:00:00Z");
    UpcomingCalendarTest() {
        when(store.calendars()).thenReturn(List.of(calendar));when(calendar.id()).thenReturn(1L);
        when(calendar.timeZone()).thenReturn(zone.getId());when(calendar.name()).thenReturn("Example");
        when(store.localDeletions(1L)).thenReturn(Set.of());
    }
    @Test void selectsTheNextTimedOccurrenceAcrossDaysAndIgnoresPastAndAllDayEvents() {
        when(store.events(calendar)).thenReturn(List.of(
            new CalendarStore.StoredEvent(1,CalendarOccurrencesTest.timed("ongoing","2026-09-25T11:00:00+02:00","2026-09-25T13:00:00+02:00")),
            new CalendarStore.StoredEvent(2,CalendarOccurrencesTest.allDay("holiday","2026-09-26","2026-09-27")),
            new CalendarStore.StoredEvent(3,CalendarOccurrencesTest.timed("tomorrow","2026-09-26T09:00:00+02:00","2026-09-26T10:00:00+02:00")),
            new CalendarStore.StoredEvent(4,CalendarOccurrencesTest.timed("later","2026-09-28T09:00:00+02:00","2026-09-28T10:00:00+02:00"))));
        assertThat(new CalendarOccurrences(store).upcoming(now,zone)).extracting(CalendarOccurrences.Occurrence::eventId).containsExactly("tomorrow");
        verify(store,times(1)).events(calendar);
    }
    @Test void honorsLocalDeletionAndCancelledRecurringExceptions() {
        var series=CalendarOccurrencesTest.timed("series","2026-09-21T09:00:00+02:00","2026-09-21T10:00:00+02:00").setRecurrence(List.of("RRULE:FREQ=WEEKLY;COUNT=4"));
        when(store.events(calendar)).thenReturn(List.of(new CalendarStore.StoredEvent(1,series),
            new CalendarStore.StoredEvent(2,CalendarOccurrencesTest.timed("cancelled","2026-09-28T09:00:00+02:00","2026-09-28T10:00:00+02:00")
                .setRecurringEventId("series").setOriginalStartTime(series.getStart().clone().setDateTime(new com.google.api.client.util.DateTime("2026-09-28T09:00:00+02:00"))).setStatus("cancelled"))));
        when(store.localDeletions(1L)).thenReturn(Set.of(new CalendarStore.LocalDeletion("series","2026-10-05T07:00:00Z")));
        assertThat(new CalendarOccurrences(store).upcoming(now,zone)).extracting(CalendarOccurrences.Occurrence::start).containsExactly("2026-10-12T07:00:00Z");
    }
    @Test void returnsEmptyWhenThereAreNoTimedEventsInsideThirtyDays() {
        when(store.events(calendar)).thenReturn(List.of(new CalendarStore.StoredEvent(1,
            CalendarOccurrencesTest.timed("far","2026-12-01T09:00:00+01:00","2026-12-01T10:00:00+01:00"))));
        assertThat(new CalendarOccurrences(store).upcoming(now,zone)).isEmpty();
    }
}
