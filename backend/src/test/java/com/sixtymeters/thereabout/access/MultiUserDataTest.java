package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.launcher.LauncherStore;
import com.sixtymeters.thereabout.choices.service.ChoicesService;
import com.sixtymeters.thereabout.client.service.PreferencesService;
import com.sixtymeters.thereabout.health.service.HealthDataService;
import com.sixtymeters.thereabout.health.data.WorkoutRepository;
import com.sixtymeters.thereabout.location.service.LocationHistoryService;
import com.sixtymeters.thereabout.location.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import com.sixtymeters.thereabout.calendar.service.*;
import com.google.api.services.calendar.model.CalendarListEntry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class MultiUserDataTest {
    @Autowired JdbcTemplate db;
    @Autowired LauncherStore launcher;
    @Autowired ChoicesService choices;
    @Autowired PreferencesService preferences;
    @Autowired HealthDataService health;
    @Autowired WorkoutRepository workouts;
    @Autowired LocationHistoryService locations;
    @Autowired LocationHistoryRepository entries;
    @Autowired CalendarStore calendars;
    @Autowired CalendarUserService assignments;
    @Autowired CalendarOccurrences occurrences;
    @Autowired CalendarSyncService sync;
    final UserId a = new UserId(100011), b = new UserId(100012);
    final LocalDate date=LocalDate.of(1902,2,1);
    @BeforeEach void fixtures() {
        com.sixtymeters.thereabout.testing.TestUsers.owner(db);
        for(var user:List.of(a,b)) db.update("INSERT INTO identity(id,short_name,role) VALUES(?,?,'USER') ON DUPLICATE KEY UPDATE role='USER'",user.value(),"Personal fixture "+user.value());
    }
    @Test void launcherIconsAndMixedOrdersCannotCrossUsers() {
        var group=launcher.createGroup(a,new GenLauncherGroupInput().section("Test").name("A")).getGroups().getFirst();
        var other=launcher.createGroup(b,new GenLauncherGroupInput().section("Test").name("B")).getGroups().getFirst();
        var shortcut=launcher.createShortcut(a,new GenLauncherShortcutInput().groupId(group.getId()).title("Private").url("https://example.test")).getShortcuts().getFirst();
        assertThat(launcher.collection(b).getShortcuts()).isEmpty();
        assertThatThrownBy(() -> launcher.icon(b,shortcut.getId())).hasMessageContaining("404");
        assertThatThrownBy(() -> launcher.updateShortcut(b,shortcut.getId(),new GenLauncherShortcutInput().groupId(other.getId()).title("Hijack").url("https://example.test"))).hasMessageContaining("404");
        assertThatThrownBy(() -> launcher.orderGroups(a,List.of(group.getId(),other.getId()))).hasMessageContaining("409");
        assertThat(launcher.collection(a).getGroups()).hasSize(1);
    }
    @Test void choicesPreferencesAndLocationBulkWritesRemainPersonal() {
        choices.adjust(a,date,1); choices.adjust(b,date,-1);
        assertThat(choices.history(a,date,7).getScore()).isEqualTo(1);
        assertThat(choices.history(b,date,7).getScore()).isEqualTo(-1);
        preferences.updateGoal(a,new BigDecimal("82.3"));
        assertThat(preferences.getPreferences(b).getWeightGoalKg()).isEqualByComparingTo("75");
        var own=locations.createLocationHistoryEntry(a,location());
        var foreign=locations.createLocationHistoryEntry(b,location());
        assertThat(locations.getLocationHistory(a,date,date)).extracting(LocationHistoryEntity::getId).containsExactly(own.getId());
        assertThatThrownBy(() -> locations.deleteLocationHistoryEntries(a,List.of(own.getId(),foreign.getId()))).hasMessageContaining("404");
        assertThat(entries.findById(own.getId())).isPresent();
        assertThatThrownBy(() -> locations.updateLocationHistoryEntry(a,foreign.getId(),location())).hasMessageContaining("404");
    }
    private LocationHistoryEntity location() { return LocationHistoryEntity.builder().timestamp(date.atTime(12,0)).latitude(47.0).longitude(8.0).source(LocationHistorySource.THEREABOUT_API).build(); }
    @Test void repeatedImportsReplaceOnlyTheSameUsersDayAndExternalWorkoutId() {
        var at=date.atTime(12,0).atOffset(ZoneOffset.UTC);
        var datum=new GenMetricDataQuantity().date(at).qty(new BigDecimal("80"));
        var metric=new GenHealthMetric().name("weight_body_mass").units("kg").data(List.of(datum));
        health.saveHealthMetrics(a,List.of(metric));health.saveHealthMetrics(b,List.of(metric));
        datum.setQty(new BigDecimal("85")); health.saveHealthMetrics(a,List.of(metric));
        assertThat(db.<BigDecimal>queryForObject("SELECT qty FROM health_metric WHERE user_id=? AND metric_date=?",BigDecimal.class,b.value(),date)).isEqualByComparingTo("80");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM health_metric WHERE user_id=? AND metric_date=?",Integer.class,a.value(),date)).isEqualTo(1);
        var workout=new GenWorkout().id("same-external-id").name("Walk").start(at).end(at.plusHours(1)).stepCount(List.of(new GenQuantityData().date(at).qty(new BigDecimal("10"))));
        health.saveWorkouts(a,List.of(workout));health.saveWorkouts(b,List.of(workout));
        var aid=workouts.findByUserIdAndSourceId(a.value(),workout.getId()).orElseThrow().getId();
        var bid=workouts.findByUserIdAndSourceId(b.value(),workout.getId()).orElseThrow().getId();
        assertThat(aid).isNotEqualTo(bid);
        workout.getStepCount().getFirst().setQty(new BigDecimal("20"));health.saveWorkouts(a,List.of(workout));
        assertThat(workouts.findByUserIdAndSourceId(a.value(),workout.getId()).orElseThrow().getId()).isEqualTo(aid);
        assertThat(db.<BigDecimal>queryForObject("SELECT qty FROM workout_time_series_data WHERE workout_id=?",BigDecimal.class,bid)).isEqualByComparingTo("10");
        assertThat(db.<BigDecimal>queryForObject("SELECT qty FROM workout_time_series_data WHERE workout_id=?",BigDecimal.class,aid)).isEqualByComparingTo("20");
    }
    @Test void calendarMembershipValidatesUsersAndDeletionAppliesToBothAssignedUsers() {
        calendars.metadata("fixture",new CalendarListEntry().setId("personal-test").setSummary("Shared calendar").setTimeZone("Europe/Zurich").setAccessRole("reader"));
        var cal=calendars.calendars().stream().filter(c -> c.googleId().equals("personal-test")).findFirst().orElseThrow();
        assignments.assign(cal.id(),List.of(a.value(),b.value()));
        calendars.update("UPDATE calendar_calendar SET active_generation='test' WHERE id=?",cal.id());
        var event=new com.google.api.services.calendar.model.Event().setId("one").setSummary("Fixture").setStart(new com.google.api.services.calendar.model.EventDateTime().setDate(new com.google.api.client.util.DateTime("1902-02-01"))).setEnd(new com.google.api.services.calendar.model.EventDateTime().setDate(new com.google.api.client.util.DateTime("1902-02-02")));
        calendars.page(calendars.calendar(cal.id()),"test",List.of(event));
        assertThat(occurrences.day(a,date,ZoneId.of("Europe/Zurich"))).hasSize(1);
        assertThat(occurrences.day(b,date,ZoneId.of("Europe/Zurich"))).hasSize(1);
        assertThatThrownBy(() -> sync.delete(new UserId(1),cal.id(),"one",null)).hasMessageContaining("404");
        db.update("INSERT INTO identity(id,short_name,is_group) VALUES(100013,'Group',TRUE) ON DUPLICATE KEY UPDATE role=NULL,is_group=TRUE");
        assertThatThrownBy(() -> assignments.assign(cal.id(),List.of(a.value(),100013L))).hasMessageContaining("404");
        assertThat(calendars.userIds(cal.id())).containsExactlyInAnyOrder(a.value(),b.value());
        sync.delete(a,cal.id(),"one",null);
        assertThat(occurrences.day(b,date,ZoneId.of("Europe/Zurich"))).isEmpty();
        assignments.assign(cal.id(),List.of());
        assertThat(calendars.calendars(a)).isEmpty();
        assertThat(calendars.events(calendars.calendar(cal.id()))).hasSize(1);
    }
}
