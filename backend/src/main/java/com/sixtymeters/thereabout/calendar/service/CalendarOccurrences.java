package com.sixtymeters.thereabout.calendar.service;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import com.sixtymeters.thereabout.calendar.data.CalendarStore.*;
import com.sixtymeters.thereabout.calendar.google.GoogleCalendarGateway;
import lombok.RequiredArgsConstructor;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.component.VEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CalendarOccurrences {
    private final CalendarStore store;
    public record Occurrence(String key, long calendarId, String calendarName, String color, boolean syncing,
                             String eventId, String originalStart, boolean recurring, String title, String location,
                             String description, String start, String end, boolean allDay, String startDate, String endDate,
                             String timeZone, boolean canDelete, List<Guest> guests) {}
    public record Span(Instant start, Instant end, String originalStart, LocalDate startDate, LocalDate endDate) {}

    @Transactional(readOnly = true)
    public List<Occurrence> day(LocalDate day, ZoneId viewZone) {
        List<Occurrence> result = new ArrayList<>();
        for (CalendarRow calendar : store.calendars()) {
            List<StoredEvent> events = store.events(calendar);
            Set<LocalDeletion> deleted = store.localDeletions(calendar.id());
            Set<String> exceptions = new HashSet<>();
            Set<String> cancelledMasters = new HashSet<>();
            for (StoredEvent item : events) {
                Event e = item.event();
                if (e.getRecurringEventId() != null) exceptions.add(e.getRecurringEventId() + "/" + canonical(GoogleCalendarGateway.original(e.getOriginalStartTime())));
                else if ("cancelled".equals(e.getStatus())) cancelledMasters.add(e.getId());
            }
            for (StoredEvent item : events) {
                Event e = item.event();
                if ("cancelled".equals(e.getStatus()) || cancelledMasters.contains(e.getRecurringEventId())) continue;
                for (Span span : spans(e,day,viewZone,calendar.timeZone())) {
                    boolean master = e.getRecurrence() != null && !e.getRecurrence().isEmpty();
                    if (master && exceptions.contains(e.getId() + "/" + canonical(span.originalStart()))) continue;
                    String original = master ? span.originalStart() : GoogleCalendarGateway.original(e.getOriginalStartTime());
                    String deletionId = e.getRecurringEventId() == null ? e.getId() : e.getRecurringEventId();
                    if (deleted.contains(new LocalDeletion(deletionId, Objects.requireNonNullElse(canonical(original), "")))) continue;
                    result.add(new Occurrence(calendar.id()+"/"+e.getId()+"/"+Objects.requireNonNullElse(original,""),calendar.id(),calendar.name(),calendar.color(),calendar.selected(),
                            e.getId(),original,master || e.getRecurringEventId() != null,Objects.requireNonNullElse(e.getSummary(),"(No title)"),e.getLocation(),e.getDescription(),
                            span.start().toString(),span.end().toString(),span.startDate()!=null,span.startDate()==null?null:span.startDate().toString(),span.endDate()==null?null:span.endDate().toString(),
                            e.getStart().getTimeZone()==null?calendar.timeZone():e.getStart().getTimeZone(),
                            true,store.guests(item.id())));
                }
            }
        }
        result.sort(Comparator.comparing(Occurrence::start).thenComparing(Occurrence::calendarId).thenComparing(Occurrence::key));
        return result;
    }
    public static List<Span> spans(Event e, LocalDate day, ZoneId viewZone, String calendarZone) {
        if (e.getStart()==null || e.getEnd()==null) return List.of();
        boolean allDay = e.getStart().getDate()!=null;
        ZoneId zone = ZoneId.of(Objects.requireNonNullElse(e.getStart().getTimeZone(),calendarZone));
        Instant windowStart=day.atStartOfDay(viewZone).toInstant(), windowEnd=day.plusDays(1).atStartOfDay(viewZone).toInstant();
        LocalDate startDate = allDay ? LocalDate.parse(e.getStart().getDate().toStringRfc3339()) : null;
        LocalDate endDate = allDay ? LocalDate.parse(e.getEnd().getDate().toStringRfc3339()) : null;
        Instant start = allDay ? startDate.atStartOfDay(viewZone).toInstant() : Instant.ofEpochMilli(e.getStart().getDateTime().getValue());
        Instant end = allDay ? endDate.atStartOfDay(viewZone).toInstant() : Instant.ofEpochMilli(e.getEnd().getDateTime().getValue());
        if (e.getRecurrence()==null || e.getRecurrence().isEmpty()) return overlaps(start,end,windowStart,windowEnd)
                ? List.of(new Span(start,end,null,startDate,endDate)) : List.of();
        try {
            StringBuilder ics = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Thereabout//Calendar//EN\r\nBEGIN:VEVENT\r\nUID:local\r\nDTSTAMP:20000101T000000Z\r\n");
            if (allDay) ics.append("DTSTART;VALUE=DATE:").append(startDate.format(DateTimeFormatter.BASIC_ISO_DATE)).append("\r\nDTEND;VALUE=DATE:").append(endDate.format(DateTimeFormatter.BASIC_ISO_DATE));
            else {
                DateTimeFormatter format=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
                ics.append("DTSTART;TZID=").append(zone.getId()).append(":").append(start.atZone(zone).format(format))
                        .append("\r\nDTEND;TZID=").append(zone.getId()).append(":").append(end.atZone(zone).format(format));
            }
            ics.append("\r\n");
            for (String rule:e.getRecurrence()) {
                if (!rule.matches("(?s)(RRULE|EXRULE|RDATE|EXDATE)[:;][^\\r\\n]+")) throw new IllegalArgumentException("Unsupported recurrence property");
                ics.append(rule).append("\r\n");
            }
            ics.append("END:VEVENT\r\nEND:VCALENDAR\r\n");
            VEvent event = (VEvent)new CalendarBuilder().build(new StringReader(ics.toString())).getComponent("VEVENT").orElseThrow();
            net.fortuna.ical4j.model.Period<?> period = allDay
                    ? new net.fortuna.ical4j.model.Period<>(day.minusDays(java.time.temporal.ChronoUnit.DAYS.between(startDate,endDate)),day.plusDays(1))
                    : new net.fortuna.ical4j.model.Period<>(windowStart.minus(Duration.between(start,end)).atZone(zone),windowEnd.atZone(zone));
            Set<net.fortuna.ical4j.model.Period<Temporal>> expanded=event.calculateRecurrenceSet(period);
            List<Span> spans=new ArrayList<>();
            for (var occurrence:expanded) {
                LocalDate sd=allDay?LocalDate.from(occurrence.getStart()):null;
                LocalDate ed=allDay?LocalDate.from(occurrence.getEnd()):null;
                Instant s=allDay?sd.atStartOfDay(viewZone).toInstant():Instant.from(occurrence.getStart());
                Instant t=allDay?ed.atStartOfDay(viewZone).toInstant():Instant.from(occurrence.getEnd());
                if (overlaps(s,t,windowStart,windowEnd)) spans.add(new Span(s,t,allDay?sd.toString():s.toString(),sd,ed));
            }
            return spans;
        } catch (Exception failure) { throw new IllegalStateException("Unable to expand calendar recurrence",failure); }
    }
    public static String canonical(String original) {
        if (original==null) return null;
        if (original.length()==10) return LocalDate.parse(original).toString();
        return OffsetDateTime.parse(original).toInstant().toString();
    }
    private static boolean overlaps(Instant start, Instant end, Instant from, Instant to) {
        return start.isBefore(to) && (end.isAfter(from) || (start.equals(end) && !start.isBefore(from)));
    }
}
