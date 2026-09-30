package com.sixtymeters.thereabout.testing;

import db.migration.V27__Validate_legacy_owner;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in destructive migration of an independently restored, disposable backup copy. */
@EnabledIfEnvironmentVariable(named="THEREABOUT_REHEARSAL_URL", matches="jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[A-Za-z0-9_]+_test")
class MigrationRehearsalTest {
    @Test void migrationPreservesAllRowsLedgerBalancesAndRelationships() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("THEREABOUT_REHEARSAL_URL"),System.getenv("THEREABOUT_REHEARSAL_USER"),System.getenv("THEREABOUT_REHEARSAL_PASSWORD"));
        var db = new JdbcTemplate(ds);
        TestDatabaseGuard.validate(ds.getUrl());
        var tables=db.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name",String.class);
        var countsPath=System.getenv("THEREABOUT_REHEARSAL_COUNTS");
        if (countsPath != null) {
            var expected=new tools.jackson.databind.json.JsonMapper().readTree(java.nio.file.Files.readString(java.nio.file.Path.of(countsPath)));
            for (String table:expected.propertyNames()) assertThat(db.<Long>queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class)).as("Restored "+table).isEqualTo(expected.get(table).asLong());
        }
        var before=new TreeMap<String,Long>();
        tables.stream().filter(t -> !Set.of("flyway_schema_history","configuration").contains(t)).forEach(t -> before.put(t,db.queryForObject("SELECT COUNT(*) FROM `"+t+"`",Long.class)));
        var balances=db.queryForList("SELECT p.account_id,p.currency,SUM(p.amount) amount FROM finance_posting p JOIN finance_transaction t ON t.id=p.transaction_id WHERE p.deleted=FALSE AND t.deleted=FALSE GROUP BY p.account_id,p.currency ORDER BY p.account_id,p.currency");
        var series=db.queryForList("SELECT d.workout_id source_id,COUNT(*) n FROM workout_time_series_data d GROUP BY d.workout_id ORDER BY d.workout_id");
        var countries=db.queryForList("SELECT estimated_iso_country_code,COUNT(*) n FROM location_history_entry GROUP BY estimated_iso_country_code ORDER BY estimated_iso_country_code");
        var goal=db.queryForObject("SELECT config_value FROM configuration WHERE config_key='WEIGHT_GOAL_KG'",String.class);
        var start=db.queryForObject("SELECT config_value FROM configuration WHERE config_key='WEIGHT_GOAL_STARTED_ON'",String.class);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        before.forEach((table,count) -> assertThat(db.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class)).as(table).isEqualTo(count));
        assertThat(db.queryForList("SELECT p.account_id,p.currency,SUM(p.amount) amount FROM finance_posting p JOIN finance_transaction t ON t.id=p.transaction_id WHERE p.deleted=FALSE AND t.deleted=FALSE GROUP BY p.account_id,p.currency ORDER BY p.account_id,p.currency")).isEqualTo(balances);
        assertThat(db.queryForList("SELECT w.source_id,COUNT(*) n FROM workout_time_series_data d JOIN workout w ON w.id=d.workout_id GROUP BY w.source_id ORDER BY w.source_id")).isEqualTo(series);
        assertThat(db.queryForList("SELECT estimated_iso_country_code,COUNT(*) n FROM location_history_entry GROUP BY estimated_iso_country_code ORDER BY estimated_iso_country_code")).isEqualTo(countries);
        assertThat(db.queryForObject("SELECT role FROM identity WHERE id=1",String.class)).isEqualTo("ADMIN");
        assertThat(db.queryForObject("SELECT weight_goal_kg FROM user_preferences WHERE user_id=1",String.class)).isEqualTo(goal);
        assertThat(db.queryForObject("SELECT CAST(weight_goal_started_on AS CHAR) FROM user_preferences WHERE user_id=1",String.class)).isEqualTo(start);
        for (var table:List.of("launcher_group","health_metric","workout","location_history_entry","choices_daily_score","finance_request")) assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id<>1 OR user_id IS NULL",Long.class)).as(table+" owner").isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_account WHERE kind IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET') AND user_id<>1",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_account WHERE kind NOT IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET') AND user_id IS NOT NULL",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM calendar_user",Long.class)).isEqualTo(before.get("calendar_calendar"));
        System.out.println("Migration rehearsal reconciled "+before.size()+" legacy tables, "+before.values().stream().mapToLong(Long::longValue).sum()+" rows and "+balances.size()+" account/currency balances.");
    }
}
