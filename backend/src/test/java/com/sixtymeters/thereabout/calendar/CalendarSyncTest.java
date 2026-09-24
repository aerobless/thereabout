package com.sixtymeters.thereabout.calendar;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.calendar.model.*;
import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import com.sixtymeters.thereabout.calendar.data.CalendarStore.*;
import com.sixtymeters.thereabout.calendar.google.GoogleCalendarGateway;
import com.sixtymeters.thereabout.calendar.service.CalendarOccurrences;
import com.sixtymeters.thereabout.calendar.service.CalendarSyncService;
import com.sixtymeters.thereabout.config.ThereaboutException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.*;
import java.util.*;

import static com.sixtymeters.thereabout.calendar.CalendarOccurrencesTest.*;
import static com.sixtymeters.thereabout.calendar.data.CalendarStore.timestamp;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties="thereabout.calendar.worker-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CalendarSyncTest {
    @MockitoSpyBean CalendarStore store;
    @Autowired CalendarOccurrences occurrences;
    @Autowired MockMvc mvc;
    @MockitoBean GoogleCalendarGateway google;
    CalendarSyncService sync;
    long id;
    @BeforeEach void setup() throws Exception {
        sync=new CalendarSyncService(store,google);
        ReflectionTestUtils.setField(sync,"workerEnabled",true);
        store.update("UPDATE calendar_connection SET account='me@example.com',state='READY',webhook_url=?,pending_version=0,processed_version=0,next_safety_at=?,retry_at=NULL WHERE id=1",
                "https://example.com"+CalendarSyncService.CALLBACK_PATH,timestamp(Instant.now().plusSeconds(86400)));
        store.metadata("me@example.com", new CalendarListEntry().setId("primary-test").setSummary("Personal").setTimeZone("Europe/Zurich").setAccessRole("owner"));
        id=store.calendars().stream().filter(c -> c.googleId().equals("primary-test")).findFirst().orElseThrow().id();
        store.update("UPDATE calendar_calendar SET selected=TRUE,active_generation='original',sync_token='old',pending_version=1 WHERE id=?",id);
        when(google.account()).thenReturn("me@example.com");
        when(google.calendars(any())).thenReturn(new CalendarList().setItems(List.of(new CalendarListEntry().setId("primary-test").setSummary("Personal").setTimeZone("Europe/Zurich").setAccessRole("owner"))));
        when(google.events(anyString(),any(),any())).thenReturn(new Events().setItems(List.of()).setNextSyncToken("next"));
        when(google.watch(any(),anyString(),anyString(),anyString())).thenAnswer(call -> new Channel().setId(call.getArgument(1)).setResourceId("resource-"+call.getArgument(1)).setExpiration(Instant.now().plusSeconds(604800).toEpochMilli()));
    }
    @Test void fullImportPublishesOnlyAfterFinalPageAndPreservesIdentityLinks() throws Exception {
        Event event=timed("one","2026-09-24T09:00:00+02:00","2026-09-24T10:00:00+02:00")
                .setAttendees(List.of(new EventAttendee().setEmail("GUEST@example.com").setDisplayName("Guest")));
        when(google.events("primary-test",null,null)).thenReturn(new Events().setItems(List.of(event)).setNextPageToken("p2"));
        when(google.events("primary-test",null,"p2")).thenAnswer(call -> {
            assertThat(store.calendar(id).generation()).isEqualTo("original");
            assertThat(store.events(store.calendar(id))).isEmpty();
            return new Events().setItems(List.of(event)).setNextSyncToken("done");
        });
        sync.synchronize(store.calendar(id),true);
        assertThat(store.events(store.calendar(id))).hasSize(1);
        assertThat(store.calendar(id).syncToken()).isEqualTo("done");
        var guest=store.guests(store.events(store.calendar(id)).getFirst().id()).getFirst();
        assertThat(guest.email()).isEqualTo("guest@example.com");
        assertThat(guest.identityId()).isNull();
        store.update("INSERT INTO identity(short_name) VALUES ('Calendar test guest')");
        store.update("UPDATE identity_in_application SET identity_id=(SELECT MAX(id) FROM identity WHERE short_name='Calendar test guest') WHERE id=?",guest.applicationIdentityId());
        doReturn(new Events().setItems(List.of(event)).setNextSyncToken("done")).when(google).events("primary-test",null,"p2");
        sync.synchronize(store.calendar(id),true);
        var after=store.guests(store.events(store.calendar(id)).getFirst().id()).getFirst();
        assertThat(after.applicationIdentityId()).isEqualTo(guest.applicationIdentityId());
        assertThat(after.identityId()).isNotNull();
    }
    @Test void failedFullImportKeepsPublishedSnapshotAndToken() throws Exception {
        store.page(store.calendar(id),"original",List.of(allDay("existing","2026-09-24","2026-09-25")));
        when(google.events("primary-test",null,null)).thenReturn(new Events().setItems(List.of(allDay("new","2026-09-24","2026-09-25"))).setNextPageToken("p2"));
        when(google.events("primary-test",null,"p2")).thenThrow(new IOException("offline"));
        assertThatThrownBy(() -> sync.synchronize(store.calendar(id),true)).isInstanceOf(IOException.class);
        assertThat(store.calendar(id).syncToken()).isEqualTo("old");
        assertThat(store.events(store.calendar(id))).extracting(e -> e.event().getId()).containsExactly("existing");
    }
    @Test void expiredSyncTokenRebuildsCalendar() throws Exception {
        when(google.events("primary-test","old",null)).thenThrow(new GoogleJsonResponseException(new HttpResponseException.Builder(410,"Gone",new HttpHeaders()),null));
        when(google.events("primary-test",null,null)).thenReturn(new Events().setItems(List.of(allDay("new","2026-09-24","2026-09-25"))).setNextSyncToken("fresh"));
        sync.synchronize(store.calendar(id),false);
        assertThat(store.calendar(id).syncToken()).isEqualTo("fresh");
        assertThat(store.events(store.calendar(id))).hasSize(1);
    }
    @Test void callbacksAreAuthenticatedDurableAndCannotLoseMidSyncNotification() throws Exception {
        channel("active",id,"token","resource",Instant.now().plusSeconds(5000));
        assertThatThrownBy(() -> sync.notification("active","wrong","resource","exists")).isInstanceOf(ThereaboutException.class);
        assertThatThrownBy(() -> sync.notification("active","token","wrong","exists")).isInstanceOf(ThereaboutException.class);
        sync.notification("active","token","resource","exists");
        long pending=store.calendar(id).pending();
        when(google.events("primary-test","old",null)).thenAnswer(call -> {
            sync.notification("active","token","resource","exists");
            return new Events().setItems(List.of()).setNextSyncToken("new");
        });
        sync.synchronize(store.calendar(id),false);
        assertThat(store.calendar(id).processed()).isEqualTo(pending);
        assertThat(store.calendar(id).pending()).isGreaterThan(pending);
    }
    @Test void earlyCallbackIsVerifiedAfterWatchAndDuplicateCallbacksCoalesce() throws Exception {
        when(google.watch(any(),anyString(),anyString(),anyString())).thenAnswer(call -> {
            String channel=call.getArgument(1), token=call.getArgument(2);
            sync.notification(channel,token,"resource-"+channel,"sync");
            return new Channel().setResourceId("resource-"+channel).setExpiration(Instant.now().plusSeconds(604800).toEpochMilli());
        });
        sync.work();
        assertThat(sync.channelHealth(id)).isEqualTo("HEALTHY");
        ChannelRow channel=store.channels().stream().filter(c -> Objects.equals(c.calendarId(),id)).findFirst().orElseThrow();
        assertThat(channel.tokenHash()).hasSize(64);
        assertThat(store.calendar(id).pending()).isEqualTo(store.calendar(id).processed());
        sync.work(); // Consume registration's durable metadata catch-up.
        clearInvocations(google);
        sync.work();
        verifyNoInteractions(google);
    }
    @Test void dailySafetyCheckRunsOnceAndRestartsCatchUp() {
        channel("event",id,"token","event-resource",Instant.now().plusSeconds(60000));
        channel("metadata",null,"token","metadata-resource",Instant.now().plusSeconds(60000));
        store.update("UPDATE calendar_calendar SET processed_version=pending_version WHERE id=?",id);
        store.update("UPDATE calendar_connection SET next_safety_at=? WHERE id=1",timestamp(Instant.now().minusSeconds(1)));
        sync.work();
        assertThat(store.connection().nextSafety()).isAfter(Instant.now().plusSeconds(86000));
        assertThat(store.calendar(id).pending()).isEqualTo(store.calendar(id).processed());
        clearInvocations(google); sync.work(); verifyNoInteractions(google);
        sync.resume();
        assertThat(store.calendar(id).pending()).isGreaterThan(store.calendar(id).processed());
    }
    @Test void renewalCreatesReplacementAndRetiresOldChannel() throws Exception {
        channel("old-channel",id,"old-token","old-resource",Instant.now().plusSeconds(30));
        store.update("UPDATE calendar_channel SET renew_at=? WHERE id='old-channel'",timestamp(Instant.now().minusSeconds(1)));
        sync.work();
        assertThat(store.channel("old-channel").orElseThrow().state()).isEqualTo("RETIRED");
        assertThat(store.channels().stream().filter(c -> Objects.equals(c.calendarId(),id) && c.state().equals("ACTIVE"))).hasSize(1);
        verify(google).stop("old-channel","old-resource");
        assertThatThrownBy(() -> sync.notification("old-channel","old-token","old-resource","exists")).isInstanceOf(ThereaboutException.class);
    }
    @Test void missingCallbackShowsDegradedAndFailuresBackOff() throws Exception {
        channel("unverified",id,"token","resource",Instant.now().plusSeconds(60000));
        store.update("UPDATE calendar_channel SET last_notification_at=NULL,created_at=? WHERE id='unverified'",timestamp(Instant.now().minusSeconds(600)));
        assertThat(sync.channelHealth(id)).isEqualTo("DEGRADED");
        when(google.events(anyString(),any(),any())).thenThrow(new IOException("secret must never be exposed"));
        sync.work();
        assertThat(store.calendar(id).retryAt()).isAfter(Instant.now());
        assertThat(store.calendar(id).error()).doesNotContain("secret");
        sync.work(); // Metadata watch registration is independent of the event retry.
        clearInvocations(google); sync.work(); verifyNoInteractions(google);
    }
    @Test void credentialsCreateSelfAndAccountChangePreservesHistory() throws Exception {
        store.page(store.calendar(id),"original",List.of(allDay("existing","2026-09-24","2026-09-25")));
        sync.credentials(Map.of("clientId","test-client","clientSecret","secret","refreshToken","refresh"));
        assertThat(store.calendar(id).selected()).isTrue();
        assertThat(store.identity("me@example.com",null)).isPositive();
        when(google.account()).thenReturn("other@example.com");
        sync.credentials(Map.of("refreshToken","replacement"));
        assertThat(store.calendar(id).selected()).isFalse();
        assertThat(store.events(store.calendar(id))).hasSize(1);
        sync.credentials(Map.of("refreshToken",""));
        assertThat(store.connection().state()).isEqualTo("NOT_CONFIGURED");
    }
    @Test void deselectionRetainsEventsAndStopsChannel() throws Exception {
        store.page(store.calendar(id),"original",List.of(allDay("existing","2026-09-24","2026-09-25")));
        channel("event",id,"token","resource",Instant.now().plusSeconds(5000));
        sync.select(List.of());
        assertThat(store.calendar(id).selected()).isFalse();
        assertThat(store.events(store.calendar(id))).hasSize(1);
        verify(google).stop("event","resource");
    }
    @Test void movedAndCancelledExceptionsOverrideMasterAcrossDays() {
        Event master=timed("series","2026-09-21T09:00:00+02:00","2026-09-21T10:00:00+02:00").setRecurrence(List.of("RRULE:FREQ=DAILY"));
        Event moved=timed("moved","2026-09-25T11:00:00+02:00","2026-09-25T12:00:00+02:00").setRecurringEventId("series").setOriginalStartTime(timed("x","2026-09-24T09:00:00+02:00","2026-09-24T10:00:00+02:00").getStart());
        store.page(store.calendar(id),"original",List.of(master,moved));
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich"))).isEmpty();
        assertThat(occurrences.day(LocalDate.parse("2026-09-25"),ZoneId.of("Europe/Zurich"))).hasSize(2);
        moved.setStatus("cancelled"); store.page(store.calendar(id),"original",List.of(moved));
        assertThat(occurrences.day(LocalDate.parse("2026-09-25"),ZoneId.of("Europe/Zurich"))).hasSize(1);
    }
    @Test void localSeriesDeletionIsIdempotentAndSurvivesMovedExceptionsAndFullImport() throws Exception {
        Event master=timed("series","2026-09-21T09:00:00+02:00","2026-09-21T10:00:00+02:00").setRecurrence(List.of("RRULE:FREQ=DAILY"));
        store.page(store.calendar(id),"original",List.of(master));
        long pending=store.calendar(id).pending();
        sync.delete(id,"series","2026-09-24T09:00:00+02:00");
        sync.delete(id,"series","2026-09-24T07:00:00Z");
        assertThat(store.localDeletions(id)).hasSize(1);
        assertThat(store.calendar(id).pending()).isEqualTo(pending);
        verifyNoInteractions(google);
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich"))).isEmpty();
        assertThat(occurrences.day(LocalDate.parse("2026-09-25"),ZoneId.of("Europe/Zurich"))).hasSize(1);
        assertThatThrownBy(() -> sync.delete(id,"series",null)).isInstanceOf(ThereaboutException.class);
        assertThatThrownBy(() -> sync.delete(id,"series","invalid")).isInstanceOf(ThereaboutException.class);
        Event moved=timed("moved","2026-09-25T11:00:00+02:00","2026-09-25T12:00:00+02:00")
                .setRecurringEventId("series").setOriginalStartTime(timed("x","2026-09-24T09:00:00+02:00","2026-09-24T10:00:00+02:00").getStart());
        when(google.events(anyString(),any(),any())).thenReturn(new Events().setItems(List.of(master,moved)).setNextSyncToken("new"));
        sync.synchronize(store.calendar(id),true);
        assertThat(occurrences.day(LocalDate.parse("2026-09-25"),ZoneId.of("Europe/Zurich"))).hasSize(1);
        sync.delete(id,"moved","2026-09-24T07:00:00Z");
        store.page(store.calendar(id),store.calendar(id).generation(),List.of(moved.setStart(timed("x","2026-09-26T09:00:00+02:00","2026-09-26T10:00:00+02:00").getStart())
                .setEnd(timed("x","2026-09-26T09:00:00+02:00","2026-09-26T10:00:00+02:00").getEnd())));
        assertThat(occurrences.day(LocalDate.parse("2026-09-26"),ZoneId.of("Europe/Zurich"))).hasSize(1);
        when(google.events(anyString(),any(),any())).thenReturn(new Events().setItems(List.of(master)).setNextSyncToken("rebuilt"));
        sync.synchronize(store.calendar(id),true);
        sync.delete(id,"moved","2026-09-24T07:00:00Z"); // Previously deleted exception disappeared from Google's snapshot.
        assertThat(store.localDeletions(id)).hasSize(1);
    }
    @Test void localDeletionWorksOfflineOnReadOnlyInactiveCalendarsAndPreservesOtherCopies() throws Exception {
        Event event=allDay("event","2026-09-24","2026-09-26");
        store.page(store.calendar(id),"original",List.of(event));
        store.metadata("another-account",new CalendarListEntry().setId("copy").setSummary("Copy").setAccessRole("reader"));
        long copy=store.calendars().stream().filter(c -> c.googleId().equals("copy")).findFirst().orElseThrow().id();
        store.update("UPDATE calendar_calendar SET active_generation='copy' WHERE id=?",copy);
        store.page(store.calendar(copy),"copy",List.of(event));
        store.update("UPDATE calendar_calendar SET access_role='reader',selected=FALSE WHERE id=?",id);
        store.update("UPDATE calendar_connection SET state='NOT_CONFIGURED',account=NULL WHERE id=1");
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich"))).allMatch(e -> e.canDelete());
        sync.delete(id,"event",null);
        sync.delete(id,"event",null);
        verifyNoInteractions(google);
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("America/New_York"))).singleElement().satisfies(e -> assertThat(e.calendarId()).isEqualTo(copy));
        when(google.events(anyString(),any(),any())).thenReturn(new Events().setItems(List.of(event.setSummary("Changed in Google"))).setNextSyncToken("new"));
        sync.synchronize(store.calendar(id),false);
        sync.synchronize(store.calendar(id),true);
        CalendarOccurrences restarted=new CalendarOccurrences(store);
        assertThat(restarted.day(LocalDate.parse("2026-09-25"),ZoneId.of("Europe/Zurich"))).singleElement().satisfies(e -> assertThat(e.calendarId()).isEqualTo(copy));
        assertThat(store.events(store.calendar(id))).hasSize(1);
    }
    @Test void allDaySeriesDeletionPreservesOtherDatesAcrossTimeZones() {
        Event master=allDay("daily","2026-09-01","2026-09-02").setRecurrence(List.of("RRULE:FREQ=DAILY"));
        store.page(store.calendar(id),"original",List.of(master));
        sync.delete(id,"daily","2026-09-24");
        for (String zone : List.of("Europe/Zurich","Pacific/Auckland","America/Los_Angeles")) {
            assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of(zone))).isEmpty();
            assertThat(occurrences.day(LocalDate.parse("2026-09-25"),ZoneId.of(zone))).hasSize(1);
        }
        assertThatThrownBy(() -> sync.delete(id,"daily","2026-09-24T00:00:00Z")).isInstanceOf(ThereaboutException.class);
        verifyNoInteractions(google);
    }
    @Test void failedLocalStorageKeepsEventAndDoesNotContactGoogle() {
        store.page(store.calendar(id),"original",List.of(allDay("event","2026-09-24","2026-09-25")));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test storage failure"))
                .when(store).deleteLocally(id,"event","","event");
        assertThatThrownBy(() -> sync.delete(id,"event",null)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(store.localDeletions(id)).isEmpty();
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich"))).hasSize(1);
        verifyNoInteractions(google);
    }
    @Test void fullImportPublishPreservesDeletionMadeWhileFetchingTheSnapshot() throws Exception {
        Event event=allDay("event","2026-09-24","2026-09-25");
        store.page(store.calendar(id),"original",List.of(event));
        when(google.events("primary-test",null,null)).thenAnswer(call -> {
            sync.delete(id,"event",null);
            return new Events().setItems(List.of(event)).setNextSyncToken("new");
        });
        sync.synchronize(store.calendar(id),true);
        assertThat(store.events(store.calendar(id))).hasSize(1);
        assertThat(store.localDeletions(id)).hasSize(1);
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich"))).isEmpty();
        verify(google).events("primary-test",null,null);
        verifyNoMoreInteractions(google);
    }
    @Test void expiredChannelsAreDegradedAndDisconnectedChannelsArePaused() {
        channel("expired",id,"token","resource",Instant.now().minusSeconds(1));
        assertThat(sync.channelHealth(id)).isEqualTo("DEGRADED");
        assertThatThrownBy(() -> sync.notification("expired","token","resource","exists")).isInstanceOf(ThereaboutException.class);
        sync.webhookUrl("");
        assertThat(sync.channelHealth(id)).isEqualTo("DISABLED");
        store.update("UPDATE calendar_connection SET state='NOT_CONFIGURED' WHERE id=1");
        assertThat(sync.channelHealth(id)).isEqualTo("PAUSED");
    }
    @Test void manualImportAndSyncWorkWithoutWebhookAndDoNotPollGoogle() throws Exception {
        sync.webhookUrl("");
        store.update("UPDATE calendar_connection SET next_safety_at=NULL WHERE id=1");
        when(google.events("primary-test",null,null)).thenReturn(new Events()
                .setItems(List.of(allDay("manual-event","2026-09-24","2026-09-25"))).setNextSyncToken("imported"));
        sync.select(List.of(id));
        sync.work();
        assertThat(store.calendar(id).state()).isEqualTo("READY");
        assertThat(store.calendar(id).syncToken()).isEqualTo("imported");
        assertThat(store.events(store.calendar(id))).hasSize(1);
        assertThat(store.channels()).isEmpty();
        assertThat(sync.channelHealth(id)).isEqualTo("DISABLED");
        assertThat(occurrences.day(LocalDate.parse("2026-09-24"),ZoneId.of("Europe/Zurich")))
                .singleElement().satisfies(e -> assertThat(e.canDelete()).isTrue());
        clearInvocations(google);
        sync.work();
        verifyNoInteractions(google);
        sync.syncNow();
        sync.work();
        verify(google).events("primary-test","imported",null);
        verify(google,never()).watch(any(),anyString(),anyString(),anyString());
        sync.delete(id,"manual-event",null);
        assertThat(store.localDeletions(id)).hasSize(1);
        sync.webhookUrl("https://example.com"+CalendarSyncService.CALLBACK_PATH);
        sync.work();
        verify(google,times(2)).watch(any(),anyString(),anyString(),anyString());
    }
    @Test void httpStatusNeverContainsSecretsAndRevealIsNotCacheable() throws Exception {
        store.config("GOOGLE_REFRESH_TOKEN","sensitive-refresh");
        var status=mvc.perform(get("/backend/api/v1/calendar/google/status")).andReturn().getResponse();
        assertThat(status.getStatus()).isEqualTo(200);
        assertThat(status.getContentAsString()).doesNotContain("sensitive-refresh","tokenHash","syncToken");
        var reveal=mvc.perform(get("/backend/api/v1/calendar/google/secrets/refreshToken")).andReturn().getResponse();
        assertThat(reveal.getContentAsString()).contains("sensitive-refresh");
        assertThat(reveal.getHeader("Cache-Control")).isEqualTo("no-store");
        channel("callback",id,"token","resource",Instant.now().plusSeconds(5000));
        var callback=mvc.perform(post("/backend/api/v1/calendar/google/notifications").header("X-Goog-Channel-ID","callback").header("X-Goog-Channel-Token","token")
                .header("X-Goog-Resource-ID","resource").header("X-Goog-Resource-State","exists").contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
        assertThat(callback.getStatus()).isEqualTo(204);
    }
    private void channel(String channel,Long calendar,String token,String resource,Instant expiry) {
        store.update("INSERT INTO calendar_channel(id,calendar_id,token_hash,resource_id,state,created_at,expires_at,renew_at,last_notification_at) VALUES (?,?,?,?,'ACTIVE',?,?,?,?)",
                channel,calendar,CalendarSyncService.hash(token),resource,timestamp(Instant.now()),timestamp(expiry),timestamp(expiry.minusSeconds(100)),timestamp(Instant.now()));
    }
}
