package com.sixtymeters.thereabout.calendar.transport;

import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import com.sixtymeters.thereabout.calendar.data.CalendarStore.CalendarRow;
import com.sixtymeters.thereabout.calendar.service.CalendarOccurrences;
import com.sixtymeters.thereabout.calendar.service.CalendarSyncService;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.api.CalendarApi;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class CalendarController implements CalendarApi {
    private final CalendarStore store;
    private final CalendarSyncService sync;
    private final CalendarOccurrences occurrences;
    public record CalendarInfo(long id, String name, String color, String timeZone, String accessRole, boolean selected,
                               String state, String error, int importedCount, Instant lastSyncAt, String webhookHealth, Instant channelExpiresAt) {}

    @Override
    public ResponseEntity<GenGoogleCalendarStatus> getGoogleCalendarStatus() {
        var c=store.connection();
        var configured=sync.secrets();
        return noCache(GenGoogleCalendarStatus.builder().account(c.account()).state(c.state()).error(c.error())
                .webhookUrl(c.webhookUrl()).webhookHealth(sync.channelHealth(null))
                .secrets(GenGoogleSecretStatus.builder().clientId(configured.get("clientId")).clientSecret(configured.get("clientSecret")).refreshToken(configured.get("refreshToken")).build())
                .calendars(store.calendars().stream().map(this::info).toList()).build());
    }
    @Override
    public ResponseEntity<GenCalendarSecretValue> revealGoogleCalendarSecret(String key) { return noCache(GenCalendarSecretValue.builder().value(sync.reveal(key)).build()); }
    @Override
    public ResponseEntity<GenGoogleCalendarStatus> saveGoogleCalendarCredentials(GenGoogleCredentials values) {
        Map<String,String> changes=new HashMap<>();
        if (values.getClientId()!=null) changes.put("clientId",values.getClientId());
        if (values.getClientSecret()!=null) changes.put("clientSecret",values.getClientSecret());
        if (values.getRefreshToken()!=null) changes.put("refreshToken",values.getRefreshToken());
        sync.credentials(changes); return getGoogleCalendarStatus();
    }
    @Override
    public ResponseEntity<Void> saveCalendarWebhook(GenCalendarWebhookSettings settings) { sync.webhookUrl(settings.getUrl()); return ResponseEntity.noContent().build(); }
    @Override
    public ResponseEntity<List<GenCalendarInfo>> getAvailableGoogleCalendars() { return noCache(sync.available().stream().map(this::info).toList()); }
    @Override
    public ResponseEntity<Void> importGoogleCalendars(GenCalendarSelection selection) {
        sync.select(selection.getCalendarIds()); return ResponseEntity.accepted().build();
    }
    @Override
    public ResponseEntity<Void> syncGoogleCalendars() { sync.syncNow(); return ResponseEntity.accepted().build(); }
    @Override
    public ResponseEntity<Void> receiveGoogleCalendarNotification(String id,String token,String resource,String state) {
        sync.notification(id,token,resource,state); return ResponseEntity.noContent().build();
    }
    @Override
    public ResponseEntity<List<GenCalendarOccurrence>> getCalendarDay(LocalDate date,String timeZone) {
        try { return ResponseEntity.ok(occurrences.day(date,ZoneId.of(timeZone)).stream().map(CalendarApiMapper.INSTANCE::occurrence).toList()); }
        catch (DateTimeException e) { throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Invalid IANA timezone"); }
    }
    @Override
    public ResponseEntity<List<GenCalendarOccurrence>> getUpcomingCalendarEvent(String timeZone) {
        try { return noCache(occurrences.upcoming(Instant.now(),ZoneId.of(timeZone)).stream().map(CalendarApiMapper.INSTANCE::occurrence).toList()); }
        catch (DateTimeException e) { throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Invalid IANA timezone"); }
    }
    @Override
    public ResponseEntity<Void> deleteCalendarEvent(Long calendarId,String eventId,Optional<String> originalStart) {
        sync.delete(calendarId,eventId,originalStart.orElse(null)); return ResponseEntity.noContent().build();
    }
    private GenCalendarInfo info(CalendarRow calendar) {
        Instant expiry=store.channels().stream().filter(c -> Objects.equals(c.calendarId(),calendar.id()) && "ACTIVE".equals(c.state()))
                .map(CalendarStore.ChannelRow::expiresAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return CalendarApiMapper.INSTANCE.calendar(new CalendarInfo(calendar.id(),calendar.name(),calendar.color(),calendar.timeZone(),calendar.accessRole(),calendar.selected(),
                calendar.state(),calendar.error(),calendar.importedCount(),calendar.lastSyncAt(),calendar.selected()?sync.channelHealth(calendar.id()):"INACTIVE",expiry));
    }
    private static <T> ResponseEntity<T> noCache(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
