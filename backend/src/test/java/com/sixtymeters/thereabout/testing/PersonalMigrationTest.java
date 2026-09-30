package com.sixtymeters.thereabout.testing;

import db.migration.V27__Validate_legacy_owner;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Small populated upgrade, independent of the application's already migrated test schema. */
class PersonalMigrationTest {
    @Test void rejectsMissingOwnerBeforeDdlAndPreservesWorkoutSeriesAndCompleteWeightGoal() {
        String base=System.getenv().getOrDefault("THEREABOUT_TEST_DB_URL","jdbc:mariadb://127.0.0.1:3307/thereabout_test");
        TestDatabaseGuard.validate(base);
        String rootUrl=base.substring(0,base.lastIndexOf('/')+1);
        var root=new JdbcTemplate(new DriverManagerDataSource(rootUrl+"mysql",System.getenv().getOrDefault("THEREABOUT_TEST_DB_ROOT_USER","root"),System.getenv().getOrDefault("THEREABOUT_TEST_DB_ROOT_PASSWORD","test-root-only")));
        String schema="migration_"+UUID.randomUUID().toString().replace("-","")+"_test";
        root.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource(rootUrl+schema,System.getenv().getOrDefault("THEREABOUT_TEST_DB_ROOT_USER","root"),System.getenv().getOrDefault("THEREABOUT_TEST_DB_ROOT_PASSWORD","test-root-only"));
            var flyway=Flyway.configure().dataSource(ds).locations("filesystem:target/test-migrations").javaMigrations(new V27__Validate_legacy_owner());
            flyway.target("26").load().migrate();
            var db=new JdbcTemplate(ds);
            db.update("INSERT INTO identity(id,short_name,is_user,is_admin) VALUES(2,'Other',TRUE,FALSE)");
            assertThatThrownBy(() -> flyway.target("28").load().migrate()).hasStackTraceContaining("identity 1");
            assertThat(db.<Integer>queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='identity' AND column_name='role'",Integer.class)).isZero();
            flyway.load().repair();
            db.update("DELETE FROM identity WHERE id=2");
            db.update("INSERT INTO workout(id,start,end) VALUES('external','2020-01-01 12:00:00','2020-01-01 13:00:00')");
            db.update("INSERT INTO workout_time_series_data(workout_id,data_type,timestamp,qty) VALUES('external','stepCount','2020-01-01 12:00:00',12)");
            assertThatThrownBy(() -> flyway.target("28").load().migrate()).hasStackTraceContaining("identity 1");
            assertThat(db.<Integer>queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='identity' AND column_name='role'",Integer.class)).isZero();
            // A failed Java migration leaves no schema change; remove its failed history only in this disposable test.
            flyway.load().repair();
            db.update("INSERT INTO identity(id,short_name,is_user,is_admin) VALUES(1,'Owner',TRUE,TRUE),(2,'Other',TRUE,FALSE)");
            db.update("UPDATE configuration SET config_value='82.30' WHERE config_key='WEIGHT_GOAL_KG'");
            db.update("UPDATE configuration SET config_value='2001-01-02' WHERE config_key='WEIGHT_GOAL_STARTED_ON'");
            flyway.target("28").load().migrate();
            assertThat(db.queryForMap("SELECT w.user_id,w.source_id,d.qty FROM workout w JOIN workout_time_series_data d ON d.workout_id=w.id"))
                .containsEntry("user_id",1L).containsEntry("source_id","external");
            assertThat(db.<String>queryForObject("SELECT weight_goal_kg FROM user_preferences WHERE user_id=1",String.class)).isEqualTo("82.30");
            assertThat(db.<String>queryForObject("SELECT CAST(weight_goal_started_on AS CHAR) FROM user_preferences WHERE user_id=1",String.class)).isEqualTo("2001-01-02");
            assertThat(db.<String>queryForObject("SELECT role FROM identity WHERE id=2",String.class)).isEqualTo("USER");
            assertThat(db.<String>queryForObject("SELECT weight_goal_kg FROM user_preferences WHERE user_id=2",String.class)).isEqualTo("75.0");
        } finally { root.execute("DROP DATABASE "+schema); }
    }
}
