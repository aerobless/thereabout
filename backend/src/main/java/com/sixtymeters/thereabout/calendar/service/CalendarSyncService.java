package com.sixtymeters.thereabout.calendar.service;

import com.google.api.services.calendar.model.*;
import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import com.sixtymeters.thereabout.calendar.data.CalendarStore.*;
import com.sixtymeters.thereabout.calendar.google.GoogleCalendarGateway;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.sixtymeters.thereabout.calendar.data.CalendarStore.timestamp;

/** One backend owns sync. The monitor serializes remote mutations; webhook receipt never waits on it. */
@Service
@RequiredArgsConstructor
public class CalendarSyncService {
    private final CalendarStore store;
    private final GoogleCalendarGateway google;
    @Value("${thereabout.calendar.worker-enabled:true}") private boolean workerEnabled;
    private static final Map<String,String> SECRET_KEYS = Map.of("clientId", "GOOGLE_CLIENT_ID", "clientSecret", "GOOGLE_CLIENT_SECRET", "refreshToken", "GOOGLE_REFRESH_TOKEN");
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final String CALLBACK_PATH = "/backend/api/v1/calendar/google/notifications";

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void resume() {
        if (!workerEnabled) return;
        configure();
        store.update("UPDATE calendar_calendar SET pending_version=pending_version+1 WHERE selected=TRUE");
        store.pending(null);
    }
    private void configure() { google.configure(store.config("GOOGLE_CLIENT_ID"), store.config("GOOGLE_CLIENT_SECRET"), store.config("GOOGLE_REFRESH_TOKEN")); }
    public Map<String,Boolean> secrets() {
        Map<String,Boolean> result = new LinkedHashMap<>();
        SECRET_KEYS.forEach((key,value) -> result.put(key, store.config(value) != null));
        return result;
    }
    public String reveal(String key) {
        if (!SECRET_KEYS.containsKey(key)) throw bad("Unknown credential field");
        return Objects.requireNonNullElse(store.config(SECRET_KEYS.get(key)), "");
    }
    public synchronized void credentials(Map<String,String> values) {
        if (!SECRET_KEYS.keySet().containsAll(values.keySet())) throw bad("Unknown credential field");
        // Stop channels with the credentials that created them, before replacing those credentials.
        retireAll();
        store.transaction(() -> values.forEach((key,value) -> store.config(SECRET_KEYS.get(key), value)));
        configure();
        if (secrets().containsValue(false)) {
            store.update("UPDATE calendar_connection SET state='NOT_CONFIGURED',error=NULL WHERE id=1");
            return;
        }
        try {
            String account = google.account();
            if (!Objects.equals(account, store.connection().account())) {
                store.update("UPDATE calendar_calendar SET selected=FALSE,state='INACTIVE' WHERE selected=TRUE");
            }
            store.transaction(() -> {
                store.identity(account, null);
                store.update("UPDATE calendar_connection SET account=?,state='READY',error=NULL,retry_at=NULL,failures=0,pending_version=pending_version+1 WHERE id=1", account);
                store.update("UPDATE calendar_calendar SET pending_version=pending_version+1,retry_at=NULL WHERE selected=TRUE");
            });
        } catch (IOException e) {
            store.update("UPDATE calendar_connection SET state='ERROR',error=? WHERE id=1", GoogleCalendarGateway.safeError(e));
        }
    }
    public synchronized void webhookUrl(String url) {
        String normalized = url == null ? "" : url.trim();
        if (!normalized.isEmpty()) {
            try {
                URI uri = URI.create(normalized);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null || !CALLBACK_PATH.equals(uri.getPath())) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw bad("Use a public HTTPS URL ending in " + CALLBACK_PATH); }
        }
        if (!Objects.equals(normalized, store.connection().webhookUrl())) retireAll();
        store.update("UPDATE calendar_connection SET webhook_url=?,retry_at=NULL,failures=0 WHERE id=1", normalized);
        store.update("UPDATE calendar_calendar SET pending_version=pending_version+1,retry_at=NULL WHERE selected=TRUE");
    }
    public synchronized List<CalendarRow> available() {
        requireReady();
        try { metadata(); }
        catch (IOException e) { throw upstream(e); }
        return store.calendars().stream().filter(c -> c.account().equals(store.connection().account())).toList();
    }
    public synchronized void select(List<Long> ids) {
        requireReady();
        Set<Long> selected = new HashSet<>(ids);
        List<CalendarRow> available = available();
        if (!available.stream().filter(c -> readable(c.accessRole())).map(CalendarRow::id).toList().containsAll(selected)) throw bad("Select accessible calendars only");
        for (CalendarRow calendar : available) {
            if (!selected.contains(calendar.id())) {
                retire(calendar.id());
                store.update("UPDATE calendar_calendar SET selected=FALSE,state='INACTIVE' WHERE id=?", calendar.id());
            } else store.update("UPDATE calendar_calendar SET selected=TRUE,full_sync=TRUE,pending_version=pending_version+1,state='QUEUED',error=NULL,retry_at=NULL WHERE id=?", calendar.id());
        }
        if (selected.isEmpty()) retire(null);
    }
    public void syncNow() {
        requireReady();
        store.update("UPDATE calendar_calendar SET pending_version=pending_version+1,retry_at=NULL WHERE selected=TRUE");
        store.update("UPDATE calendar_connection SET pending_version=pending_version+1,retry_at=NULL WHERE id=1");
    }
    private void requireReady() {
        Connection connection = store.connection();
        if (!"READY".equals(connection.state())) throw bad("Configure valid Google credentials first");
    }
    public static boolean readable(String role) { return Set.of("owner", "writer", "reader", "writerWithoutPrivateAccess").contains(role); }

    /** Header-only callbacks are authenticated and made durable before the controller acknowledges them. */
    public void notification(String id, String token, String resource, String state) {
        store.transaction(() -> {
            // Lock across validation and enqueueing, including a concurrent watch activation or retirement.
            ChannelRow channel = store.lockChannel(id).orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "Unknown channel"));
            if (token == null || resource == null || resource.isBlank() || !Set.of("sync","exists","not_exists").contains(Objects.requireNonNullElse(state,""))
                || !MessageDigest.isEqual(channel.tokenHash().getBytes(StandardCharsets.US_ASCII), hash(token).getBytes(StandardCharsets.US_ASCII)))
                throw new ThereaboutException(HttpStatus.FORBIDDEN, "Invalid notification");
            if (!Set.of("PENDING", "ACTIVE").contains(channel.state()) || (channel.expiresAt() != null && !channel.expiresAt().isAfter(Instant.now())))
                throw new ThereaboutException(HttpStatus.NOT_FOUND, "Inactive channel");
            if (channel.resourceId() != null && !channel.resourceId().equals(resource)) throw new ThereaboutException(HttpStatus.FORBIDDEN, "Resource mismatch");
            store.update("UPDATE calendar_channel SET last_notification_at=?,early_resource_id=? WHERE id=?", timestamp(Instant.now()), resource, id);
            if (channel.resourceId() != null) store.pending(channel.calendarId());
        });
    }

    /** This timer inspects local work only. Google is contacted on notifications, renewal, or daily recovery. */
    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public synchronized void work() {
        if (!workerEnabled || !"READY".equals(store.connection().state())) return;
        Connection connection = store.connection();
        boolean pushEnabled = connection.webhookUrl() != null && !connection.webhookUrl().isBlank();
        Instant now = Instant.now();
        if (connection.retryAt() != null && connection.retryAt().isAfter(now)) return;
        try {
            if (pushEnabled && (connection.nextSafety() == null || !connection.nextSafety().isAfter(now))) {
                store.transaction(() -> {
                    store.update("UPDATE calendar_calendar SET pending_version=pending_version+1 WHERE selected=TRUE");
                    store.update("UPDATE calendar_connection SET pending_version=pending_version+1,next_safety_at=? WHERE id=1", timestamp(now.plusSeconds(86400 + ThreadLocalRandom.current().nextInt(300))));
                });
            }
            if (store.connection().pending() > store.connection().processed()) metadata();
            boolean anySelected = store.calendars().stream().anyMatch(CalendarRow::selected);
            if (!anySelected) return;
            if (pushEnabled) ensureChannel(null);
        } catch (Exception e) { fail(null,e); return; }
        for (CalendarRow calendar : store.calendars()) {
            if (!calendar.selected() || !readable(calendar.accessRole()) || (calendar.retryAt() != null && calendar.retryAt().isAfter(now))) continue;
            if (!"READY".equals(store.connection().state())) break;
            try {
                if (calendar.fullSync() || calendar.syncToken() == null) synchronize(calendar, true);
                if (pushEnabled) ensureChannel(calendar.id());
                CalendarRow current = store.calendar(calendar.id());
                if (current.pending() > current.processed()) synchronize(current, false);
            } catch (Exception e) { fail(calendar.id(),e); }
        }
    }
    private void metadata() throws IOException {
        Connection connection = store.connection();
        List<CalendarListEntry> entries = new ArrayList<>();
        String page = null;
        do {
            CalendarList response = google.calendars(page);
            if (response.getItems() != null) entries.addAll(response.getItems());
            page = response.getNextPageToken();
        } while (page != null);
        store.transaction(() -> {
            store.update("UPDATE calendar_calendar SET access_role='none' WHERE account=?", connection.account());
            entries.stream().filter(e -> !Boolean.TRUE.equals(e.getDeleted())).forEach(e -> store.metadata(connection.account(),e));
            store.update("UPDATE calendar_connection SET processed_version=?,error=NULL,retry_at=NULL,failures=0 WHERE id=1", connection.pending());
        });
        for (CalendarRow c : store.calendars()) if (c.selected() && !readable(c.accessRole())) {
            retire(c.id());
            store.update("UPDATE calendar_calendar SET state='ERROR',error='Calendar access was lost' WHERE id=?", c.id());
        }
    }
    public void synchronize(CalendarRow calendar, boolean full) throws IOException {
        String generation = full ? UUID.randomUUID().toString() : calendar.generation();
        if (full) store.update("DELETE FROM calendar_event WHERE calendar_id=? AND generation<>COALESCE(?, '')", calendar.id(), calendar.generation());
        store.update("UPDATE calendar_calendar SET state='SYNCING',imported_count=0 WHERE id=?", calendar.id());
        String page = null, syncToken;
        int count = 0;
        do {
            Events response;
            try { response = google.events(calendar.googleId(), full ? null : calendar.syncToken(), page); }
            catch (IOException e) {
                if (!full && GoogleCalendarGateway.status(e) == 410) { synchronize(calendar, true); return; }
                throw e;
            }
            List<Event> events = Objects.requireNonNullElse(response.getItems(), List.of());
            store.page(calendar, generation, events);
            count += events.size();
            store.update("UPDATE calendar_calendar SET imported_count=? WHERE id=?", count, calendar.id());
            page = response.getNextPageToken();
            syncToken = response.getNextSyncToken();
        } while (page != null);
        if (syncToken == null) throw new IOException("Google did not provide a sync token");
        String finalToken = syncToken;
        store.transaction(() -> {
            store.update("UPDATE calendar_calendar SET active_generation=?,sync_token=?,processed_version=?,full_sync=FALSE,state='READY',error=NULL,last_sync_at=?,retry_at=NULL,failures=0 WHERE id=?",
                    generation, finalToken, calendar.pending(), timestamp(Instant.now()), calendar.id());
            store.update("DELETE FROM calendar_event WHERE calendar_id=? AND generation<>?", calendar.id(), generation);
        });
    }
    private void ensureChannel(Long calendarId) throws IOException {
        Instant now = Instant.now();
        List<ChannelRow> channels = store.channels().stream().filter(c -> Objects.equals(c.calendarId(), calendarId) && !"RETIRED".equals(c.state())).toList();
        if (channels.stream().anyMatch(c -> "ACTIVE".equals(c.state()) && c.renewAt() != null && c.renewAt().isAfter(now))) return;
        String id = UUID.randomUUID().toString();
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.update("INSERT INTO calendar_channel(id,calendar_id,token_hash,state,created_at) VALUES (?,?,?,'PENDING',?)", id, calendarId, hash(token), timestamp(now));
        Channel response;
        try { response = google.watch(calendarId == null ? null : store.calendar(calendarId).googleId(), id, token, store.connection().webhookUrl()); }
        catch (IOException e) { store.update("UPDATE calendar_channel SET state='RETIRED' WHERE id=?", id); throw e; }
        if (response.getResourceId() == null || response.getExpiration() == null) {
            store.update("UPDATE calendar_channel SET state='RETIRED' WHERE id=?", id);
            throw new IOException("Invalid watch response");
        }
        Instant expires = Instant.ofEpochMilli(response.getExpiration());
        long lifetime = Duration.between(now, expires).getSeconds();
        if (lifetime <= 0) throw new IOException("Channel already expired");
        Instant renew = lifetime >= 86400 ? expires.minusSeconds(86400) : now.plusSeconds(Math.max(1, lifetime * 9 / 10));
        store.transaction(() -> {
            ChannelRow pending = store.lockChannel(id).orElseThrow();
            boolean verified = response.getResourceId().equals(pending.earlyResourceId());
            store.update("UPDATE calendar_channel SET resource_id=?,state='ACTIVE',expires_at=?,renew_at=?,last_notification_at=? WHERE id=?",
                    response.getResourceId(), timestamp(expires), timestamp(renew), verified ? timestamp(pending.lastNotificationAt()) : null, id);
            store.pending(calendarId); // Catch changes between import and registration, even without the initial callback.
        });
        for (ChannelRow old : channels) retireChannel(old);
    }
    private void retire(Long calendarId) { store.channels().stream().filter(c -> Objects.equals(c.calendarId(),calendarId) && !"RETIRED".equals(c.state())).forEach(this::retireChannel); }
    private void retireAll() { store.channels().stream().filter(c -> !"RETIRED".equals(c.state())).forEach(this::retireChannel); }
    private void retireChannel(ChannelRow c) {
        store.update("UPDATE calendar_channel SET state='RETIRED' WHERE id=?", c.id());
        try { google.stop(c.id(),c.resourceId()); } catch (IOException ignored) { /* Already rejected locally; remote subscription expires. */ }
    }
    private void fail(Long calendarId, Exception e) {
        if (GoogleCalendarGateway.credentialsFailed(e)) {
            store.update("UPDATE calendar_connection SET state='ERROR',error=? WHERE id=1", GoogleCalendarGateway.safeError(e));
            return;
        }
        int failures = calendarId == null ? store.connection().failures() : store.calendar(calendarId).failures();
        Instant retry = Instant.now().plusSeconds(Math.min(21600, 30L << Math.min(failures, 10)) + ThreadLocalRandom.current().nextInt(20));
        if (calendarId == null) store.update("UPDATE calendar_connection SET error=?,failures=failures+1,retry_at=? WHERE id=1", GoogleCalendarGateway.safeError(e), timestamp(retry));
        else store.update("UPDATE calendar_calendar SET state='ERROR',error=?,failures=failures+1,retry_at=? WHERE id=?", GoogleCalendarGateway.safeError(e), timestamp(retry), calendarId);
    }
    /** Local markers survive snapshot replacement; this action never contacts Google or queues sync. */
    public synchronized void delete(long calendarId, String eventId, String originalStart) {
        String original;
        try { original = Objects.requireNonNullElse(CalendarOccurrences.canonical(originalStart), ""); }
        catch (DateTimeException e) { throw bad("Invalid occurrence start"); }
        if (store.alreadyDeleted(calendarId,eventId,original)) return;
        CalendarRow calendar = store.calendars().stream().filter(c -> c.id() == calendarId).findFirst()
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND,"Calendar not found"));
        Event stored = store.events(calendar).stream().map(StoredEvent::event).filter(e -> e.getId().equals(eventId)).findFirst()
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND,"Event not found"));
        boolean recurring = (stored.getRecurrence() != null && !stored.getRecurrence().isEmpty()) || stored.getRecurringEventId() != null;
        if (recurring && original.isEmpty()) throw bad("An occurrence is required; entire series deletion is unsupported");
        if (!recurring && !original.isEmpty()) throw bad("This event is not recurring");
        if (stored.getRecurringEventId() != null) {
            if (!original.equals(CalendarOccurrences.canonical(GoogleCalendarGateway.original(stored.getOriginalStartTime()))))
                throw bad("Occurrence does not match this event");
        } else if (recurring) {
            ZoneId zone = ZoneId.of(stored.getStart().getTimeZone() == null ? calendar.timeZone() : stored.getStart().getTimeZone());
            LocalDate day = original.length() == 10 ? LocalDate.parse(original) : Instant.parse(original).atZone(zone).toLocalDate();
            if (CalendarOccurrences.spans(stored,day,zone,calendar.timeZone()).stream()
                    .noneMatch(span -> original.equals(CalendarOccurrences.canonical(span.originalStart()))))
                throw bad("Occurrence does not belong to this series");
        }
        String deletionId = stored.getRecurringEventId() == null ? stored.getId() : stored.getRecurringEventId();
        store.transaction(() -> store.deleteLocally(calendarId,deletionId,original,eventId));
    }
    public String channelHealth(Long calendarId) {
        if (!"READY".equals(store.connection().state())) return "PAUSED";
        if (store.connection().webhookUrl() == null || store.connection().webhookUrl().isBlank()) return "DISABLED";
        if (calendarId == null ? store.connection().error() != null : store.calendar(calendarId).error() != null) return "DEGRADED";
        List<ChannelRow> channels = store.channels().stream().filter(c -> Objects.equals(c.calendarId(),calendarId) && "ACTIVE".equals(c.state())).toList();
        if (channels.stream().anyMatch(c -> c.expiresAt() != null && c.expiresAt().isAfter(Instant.now()) && c.lastNotificationAt() != null)) return "HEALTHY";
        if (channels.stream().anyMatch(c -> c.expiresAt() != null && c.expiresAt().isAfter(Instant.now())
                && c.createdAt().plusSeconds(300).isAfter(Instant.now()))) return "AWAITING_CALLBACK";
        return "DEGRADED";
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ThereaboutException bad(String message) { return new ThereaboutException(HttpStatus.BAD_REQUEST,message); }
    private static ThereaboutException upstream(IOException e) { return new ThereaboutException(HttpStatus.BAD_GATEWAY, GoogleCalendarGateway.safeError(e)); }
}
