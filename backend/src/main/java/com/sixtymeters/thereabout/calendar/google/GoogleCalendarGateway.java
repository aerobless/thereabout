package com.sixtymeters.thereabout.calendar.google;

import com.google.auth.oauth2.UserCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** All Google requests, including retries, pass through this adapter and its shared throttle. */
@Component
public class GoogleCalendarGateway {
    private Calendar client;
    private long nextRequest;

    public void configure(String clientId, String secret, String refreshToken) {
        if (clientId == null || secret == null || refreshToken == null) { client = null; return; }
        HttpCredentialsAdapter credential = new HttpCredentialsAdapter(UserCredentials.newBuilder()
                .setClientId(clientId).setClientSecret(secret).setRefreshToken(refreshToken).build());
        client = new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), request -> {
            credential.initialize(request);
            request.setConnectTimeout(15_000);
            request.setReadTimeout(30_000);
            request.setNumberOfRetries(0); // Durable worker owns backoff; no hidden request bursts.
            request.setLoggingEnabled(false);
        }).setApplicationName("Thereabout").build();
    }

    private synchronized Calendar api() throws IOException {
        if (client == null) throw new IOException("Google credentials are not configured");
        long wait = nextRequest - System.nanoTime();
        if (wait > 0) try { Thread.sleep(java.time.Duration.ofNanos(wait)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
        nextRequest = System.nanoTime() + 1_000_000_000L;
        return client;
    }

    public String account() throws IOException {
        String page = null;
        do {
            CalendarList result = calendars(page);
            if (result.getItems() != null) for (CalendarListEntry calendar : result.getItems()) {
                if (Boolean.TRUE.equals(calendar.getPrimary())) return calendar.getId();
            }
            page = result.getNextPageToken();
        } while (page != null);
        throw new IOException("No primary calendar is accessible");
    }
    public CalendarList calendars(String page) throws IOException {
        return api().calendarList().list().setShowHidden(true).setMaxResults(250).setPageToken(page).execute();
    }
    public Events events(String calendarId, String token, String page) throws IOException {
        return api().events().list(calendarId).setSingleEvents(false).setShowDeleted(true)
                .setMaxResults(2500).setSyncToken(token).setPageToken(page).execute();
    }
    public Channel watch(String calendarId, String id, String token, String url) throws IOException {
        Channel channel = new Channel().setId(id).setToken(token).setType("web_hook").setAddress(url)
                .setParams(Map.of("ttl", "604800"));
        return calendarId == null ? api().calendarList().watch(channel).execute()
                : api().events().watch(calendarId, channel).execute();
    }
    public void stop(String id, String resource) throws IOException {
        if (resource != null) api().channels().stop(new Channel().setId(id).setResourceId(resource)).execute();
    }
    public static String original(EventDateTime value) {
        if (value == null) return null;
        return value.getDate() != null ? value.getDate().toStringRfc3339() : value.getDateTime().toStringRfc3339();
    }
    public static int status(Exception e) { return e instanceof GoogleJsonResponseException g ? g.getStatusCode() : 0; }
    public static boolean credentialsFailed(Exception e) {
        if (status(e) == 401) return true;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof com.google.api.client.auth.oauth2.TokenResponseException token
                    && token.getDetails() != null
                    && token.getDetails().getError() != null
                    && java.util.Set.of("invalid_grant", "invalid_client", "unauthorized_client")
                            .contains(token.getDetails().getError())) return true;
            if (cause instanceof com.google.api.client.http.HttpResponseException response
                    && !(cause instanceof GoogleJsonResponseException) && response.getContent() != null) {
                try {
                    Object error = GsonFactory.getDefaultInstance().fromString(response.getContent(),
                            com.google.api.client.util.GenericData.class).get("error");
                    if (error != null && java.util.Set.of("invalid_grant", "invalid_client", "unauthorized_client").contains(error)) return true;
                } catch (IOException ignored) { /* A transient non-JSON response is not a credential rejection. */ }
            }
        }
        return false;
    }
    public static String safeError(Exception e) {
        if (credentialsFailed(e)) return "Google credentials were rejected. Update the credentials in Secrets.";
        return switch (status(e)) {
            case 403 -> "Google denied access or its quota was exceeded. Check Calendar permissions and API quota.";
            case 404 -> "Google calendar or event is no longer accessible.";
            case 429 -> "Google rate limit reached. Retrying with backoff.";
            default -> "Google synchronization failed. Check connectivity and configuration; it will be retried.";
        };
    }
}
