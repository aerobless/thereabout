package com.sixtymeters.thereabout.calendar.data;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class CalendarStore {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;

    public record Connection(String account, String webhookUrl, String state, String error, long pending,
                             long processed, Instant nextSafety, Instant retryAt, int failures) {}
    public record CalendarRow(long id, String account, String googleId, String name, String color, String timeZone,
                              String accessRole, boolean selected, String syncToken, String generation,
                              boolean fullSync, long pending, long processed, String state, String error,
                              int importedCount, Instant lastSyncAt, Instant retryAt, int failures) {}
    public record ChannelRow(String id, Long calendarId, String tokenHash, String resourceId, String earlyResourceId,
                             String state, Instant createdAt, Instant expiresAt, Instant renewAt, Instant lastNotificationAt) {}
    public record StoredEvent(long id, Event event) {}
    public record LocalDeletion(String googleId, String originalStart) {}
    public record Guest(long applicationIdentityId, Long identityId, String email, String name,
                        String responseStatus, boolean organizer, boolean self) {}

    public void transaction(Runnable work) { tx.executeWithoutResult(status -> work.run()); }
    public int update(String sql, Object... args) { return db.update(sql, args); }
    public String config(String key) {
        return db.query("SELECT config_value FROM configuration WHERE config_key=?", (r,n) -> r.getString(1), key)
                .stream().findFirst().orElse(null);
    }
    public void config(String key, String value) {
        if (value == null || value.isBlank()) db.update("DELETE FROM configuration WHERE config_key=?", key);
        else db.update("INSERT INTO configuration(config_key,config_value) VALUES (?,?) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)", key, value.trim());
    }
    public Connection connection() {
        return db.queryForObject("SELECT * FROM calendar_connection WHERE id=1", (r,n) -> new Connection(
                r.getString("account"), r.getString("webhook_url"), r.getString("state"), r.getString("error"),
                r.getLong("pending_version"), r.getLong("processed_version"), instant(r,"next_safety_at"), instant(r,"retry_at"), r.getInt("failures")));
    }
    public List<CalendarRow> calendars() { return db.query("SELECT * FROM calendar_calendar ORDER BY name,id", this::mapCalendar); }
    public CalendarRow calendar(long id) {
        return db.query("SELECT * FROM calendar_calendar WHERE id=?", this::mapCalendar, id).stream().findFirst().orElseThrow();
    }
    private CalendarRow mapCalendar(ResultSet r, int n) throws SQLException {
        return new CalendarRow(r.getLong("id"), r.getString("account"), r.getString("google_id"), r.getString("name"),
                r.getString("color"), r.getString("time_zone"), r.getString("access_role"), r.getBoolean("selected"),
                r.getString("sync_token"), r.getString("active_generation"), r.getBoolean("full_sync"),
                r.getLong("pending_version"), r.getLong("processed_version"), r.getString("state"), r.getString("error"),
                r.getInt("imported_count"), instant(r,"last_sync_at"), instant(r,"retry_at"), r.getInt("failures"));
    }
    public void metadata(String account, CalendarListEntry entry) {
        db.update("""
            INSERT INTO calendar_calendar(account,google_id,name,color,time_zone,access_role) VALUES (?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE name=VALUES(name),color=VALUES(color),time_zone=VALUES(time_zone),access_role=VALUES(access_role)
            """, account, entry.getId(), Objects.requireNonNullElse(entry.getSummaryOverride(), Objects.requireNonNullElse(entry.getSummary(), entry.getId())),
                entry.getBackgroundColor(), Objects.requireNonNullElse(entry.getTimeZone(), "UTC"), Objects.requireNonNullElse(entry.getAccessRole(), "none"));
    }
    public List<ChannelRow> channels() {
        return db.query("SELECT * FROM calendar_channel", this::mapChannel);
    }
    private ChannelRow mapChannel(ResultSet r, int n) throws SQLException {
        return new ChannelRow(r.getString("id"), r.getObject("calendar_id", Long.class),
                r.getString("token_hash"), r.getString("resource_id"), r.getString("early_resource_id"), r.getString("state"),
                instant(r,"created_at"), instant(r,"expires_at"), instant(r,"renew_at"), instant(r,"last_notification_at"));
    }
    public Optional<ChannelRow> channel(String id) {
        return db.query("SELECT * FROM calendar_channel WHERE id=?", this::mapChannel, id).stream().findFirst();
    }
    public Optional<ChannelRow> lockChannel(String id) {
        return db.query("SELECT * FROM calendar_channel WHERE id=? FOR UPDATE", this::mapChannel, id).stream().findFirst();
    }
    public void pending(Long calendarId) {
        if (calendarId == null) db.update("UPDATE calendar_connection SET pending_version=pending_version+1 WHERE id=1");
        else db.update("UPDATE calendar_calendar SET pending_version=pending_version+1 WHERE id=? AND selected=TRUE", calendarId);
    }
    public long identity(String email, String displayName) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        db.update("""
            INSERT INTO identity_in_application(application,identifier,username_hint,is_group) VALUES ('GOOGLE',?,?,FALSE)
            ON DUPLICATE KEY UPDATE username_hint=COALESCE(VALUES(username_hint),username_hint)
            """, normalized, displayName);
        return Objects.requireNonNull(db.queryForObject("SELECT id FROM identity_in_application WHERE application='GOOGLE' AND identifier=?", Long.class, normalized));
    }
    public void page(CalendarRow calendar, String generation, List<Event> events) {
        transaction(() -> events.forEach(event -> saveEvent(calendar, generation, event)));
    }
    public void saveEvent(CalendarRow calendar, String generation, Event event) {
        if (event.getId() == null) throw new IllegalArgumentException("Google event has no ID");
        try {
            db.update("""
                INSERT INTO calendar_event(calendar_id,generation,google_id,title,location,description,start_time,end_time,start_date,end_date,
                    time_zone,status,recurring_event_id,original_start,recurrence,payload) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE title=VALUES(title),location=VALUES(location),description=VALUES(description),start_time=VALUES(start_time),
                    end_time=VALUES(end_time),start_date=VALUES(start_date),end_date=VALUES(end_date),time_zone=VALUES(time_zone),status=VALUES(status),
                    recurring_event_id=VALUES(recurring_event_id),original_start=VALUES(original_start),recurrence=VALUES(recurrence),payload=VALUES(payload)
                """, calendar.id(), generation, event.getId(), event.getSummary(), event.getLocation(), event.getDescription(),
                    dateTime(event.getStart()), dateTime(event.getEnd()), date(event.getStart()), date(event.getEnd()),
                    event.getStart() == null ? null : event.getStart().getTimeZone(), event.getStatus(), event.getRecurringEventId(),
                    com.sixtymeters.thereabout.calendar.google.GoogleCalendarGateway.original(event.getOriginalStartTime()),
                    event.getRecurrence() == null ? null : String.join("\n", event.getRecurrence()), GsonFactory.getDefaultInstance().toString(event));
        } catch (IOException e) { throw new IllegalStateException("Unable to serialize calendar event", e); }
        Long id = db.queryForObject("SELECT id FROM calendar_event WHERE calendar_id=? AND generation=? AND google_id=?", Long.class, calendar.id(), generation, event.getId());
        db.update("DELETE FROM calendar_participant WHERE event_id=?", id);
        if (event.getAttendees() != null) for (EventAttendee guest : event.getAttendees()) {
            participant(id, guest.getEmail(), guest.getDisplayName(), guest.getResponseStatus(), Boolean.TRUE.equals(guest.getOrganizer()), Boolean.TRUE.equals(guest.getSelf()));
        }
        if (event.getOrganizer() != null) participant(id, event.getOrganizer().getEmail(), event.getOrganizer().getDisplayName(), null, true, Boolean.TRUE.equals(event.getOrganizer().getSelf()));
    }
    private void participant(Long eventId, String email, String name, String response, boolean organizer, boolean self) {
        if (email == null || email.isBlank()) return;
        long identity = identity(email, name);
        db.update("""
            INSERT INTO calendar_participant(event_id,application_identity_id,display_name,response_status,organizer,self) VALUES (?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE organizer=(organizer OR VALUES(organizer)),self=(self OR VALUES(self))
            """, eventId, identity, name, response, organizer, self);
    }
    public List<StoredEvent> events(CalendarRow calendar) {
        if (calendar.generation() == null) return List.of();
        return db.query("SELECT id,payload FROM calendar_event WHERE calendar_id=? AND generation=?", (r,n) -> {
            try { return new StoredEvent(r.getLong(1), GsonFactory.getDefaultInstance().fromString(r.getString(2), Event.class)); }
            catch (IOException e) { throw new SQLException("Invalid persisted calendar event", e); }
        }, calendar.id(), calendar.generation());
    }
    public List<Guest> guests(long eventId) {
        return db.query("""
            SELECT a.id,a.identity_id,a.identifier,COALESCE(i.short_name,p.display_name,a.username_hint,a.identifier) AS name,
                p.response_status,p.organizer,p.self FROM calendar_participant p
            JOIN identity_in_application a ON a.id=p.application_identity_id LEFT JOIN identity i ON i.id=a.identity_id WHERE p.event_id=?
            """, (r,n) -> new Guest(r.getLong(1), r.getObject(2,Long.class), r.getString(3), r.getString(4), r.getString(5), r.getBoolean(6), r.getBoolean(7)), eventId);
    }
    public Set<LocalDeletion> localDeletions(long calendarId) {
        return new HashSet<>(db.query("SELECT google_id,original_start FROM calendar_local_deletion WHERE calendar_id=?",
                (r,n) -> new LocalDeletion(r.getString(1),r.getString(2)), calendarId));
    }
    public boolean alreadyDeleted(long calendarId, String eventId, String originalStart) {
        return !db.query("SELECT 1 FROM calendar_local_deletion WHERE calendar_id=? AND original_start=? AND (google_id=? OR source_event_id=?)",
                (r,n) -> true, calendarId, originalStart, eventId, eventId).isEmpty();
    }
    public void deleteLocally(long calendarId, String googleId, String originalStart, String sourceEventId) {
        db.update("""
            INSERT INTO calendar_local_deletion(calendar_id,google_id,original_start,source_event_id) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE source_event_id=VALUES(source_event_id)
            """, calendarId, googleId, originalStart, sourceEventId);
    }
    public static Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    private static Instant instant(ResultSet r, String col) throws SQLException { Timestamp t = r.getTimestamp(col); return t == null ? null : t.toInstant(); }
    private static Timestamp dateTime(EventDateTime t) { return t == null || t.getDateTime() == null ? null : new Timestamp(t.getDateTime().getValue()); }
    private static String date(EventDateTime t) { return t == null || t.getDate() == null ? null : t.getDate().toStringRfc3339(); }
}
