package com.sixtymeters.thereabout.testing;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IdentityNamesMigrationTest {
  @Test
  void splitsPeopleKeepsGroupsAndPreservesLinksAndRoles() {
    withLegacySchema(
        (db, flyway) -> {
          db.update(
              "INSERT INTO identity(id,short_name,is_group,role,relationship) VALUES"
                  + "(1,'  Theo   Winter  ',FALSE,'ADMIN','friend'),"
                  + "(2,'Anna van der Meer',FALSE,'USER',NULL),"
                  + "(3,'Björk',FALSE,NULL,NULL),(4,'Family Group Chat',TRUE,NULL,NULL),"
                  + "(5,'\tÉlodie\tMüller\n',FALSE,NULL,NULL)");
          db.update(
              "INSERT INTO identity_in_application(id,identity_id,application,identifier)"
                  + " VALUES(22,1,'TELEGRAM','123')");
          flyway.target("30").load().migrate();
          assertThat(
                  db.queryForList(
                      "SELECT CONCAT(first_name,'/',last_name) FROM identity ORDER BY id",
                      String.class))
              .containsExactly(
                  "Theo/Winter",
                  "Anna/van der Meer",
                  "Björk/",
                  "Family Group Chat/",
                  "Élodie/Müller");
          assertThat(db.queryForMap("SELECT id,role,relationship FROM identity WHERE id=1"))
              .containsEntry("id", 1L)
              .containsEntry("role", "ADMIN")
              .containsEntry("relationship", "friend");
          assertThat(
                  db.queryForObject(
                      "SELECT identity_id FROM identity_in_application WHERE id=22", Long.class))
              .isEqualTo(1L);
          assertThat(
                  db.queryForObject(
                      "SELECT COUNT(*) FROM information_schema.columns WHERE"
                          + " table_schema=DATABASE() AND table_name='identity' AND"
                          + " column_name='short_name'",
                      Integer.class))
              .isZero();
        });
  }

  @Test
  void rejectsBlankLegacyNamesBeforeChangingColumns() {
    withLegacySchema(
        (db, flyway) -> {
          db.update("INSERT INTO identity(short_name) VALUES(' \t ')");
          assertThatThrownBy(() -> flyway.target("30").load().migrate())
              .hasStackTraceContaining("identity_legacy_name_must_not_be_blank");
          assertThat(
                  db.queryForObject(
                      "SELECT COUNT(*) FROM information_schema.columns WHERE"
                          + " table_schema=DATABASE() AND table_name='identity' AND"
                          + " column_name='first_name'",
                      Integer.class))
              .isZero();
          assertThat(db.queryForObject("SELECT short_name FROM identity", String.class))
              .isEqualTo(" \t ");
        });
  }

  private void withLegacySchema(
      BiConsumer<JdbcTemplate, org.flywaydb.core.api.configuration.FluentConfiguration> test) {
    String base =
        System.getenv()
            .getOrDefault(
                "THEREABOUT_TEST_DB_URL", "jdbc:mariadb://127.0.0.1:3307/thereabout_test");
    TestDatabaseGuard.validate(base);
    String rootUrl = base.substring(0, base.lastIndexOf('/') + 1);
    String user = System.getenv().getOrDefault("THEREABOUT_TEST_DB_ROOT_USER", "root");
    String password =
        System.getenv().getOrDefault("THEREABOUT_TEST_DB_ROOT_PASSWORD", "test-root-only");
    var root = new JdbcTemplate(new DriverManagerDataSource(rootUrl + "mysql", user, password));
    String schema = "names_" + UUID.randomUUID().toString().replace("-", "") + "_test";
    root.execute("CREATE DATABASE " + schema);
    try {
      var ds = new DriverManagerDataSource(rootUrl + schema, user, password);
      var flyway =
          Flyway.configure()
              .dataSource(ds)
              .locations("filesystem:target/test-migrations")
              .javaMigrations(new db.migration.V27__Validate_legacy_owner());
      flyway.target("29").load().migrate();
      test.accept(new JdbcTemplate(ds), flyway);
    } finally {
      root.execute("DROP DATABASE " + schema);
    }
  }
}
