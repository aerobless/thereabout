package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Validate before any non-transactional MariaDB schema change. Never manufacture an owner. */
public class V27__Validate_legacy_owner extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement()) {
            var invalid = statement.executeQuery("SELECT COUNT(*) FROM identity WHERE (is_user=TRUE AND is_group=TRUE) OR (is_admin=TRUE AND is_user=FALSE)");
            invalid.next();
            if (invalid.getInt(1) != 0) throw new IllegalStateException("Legacy user roles are inconsistent: groups cannot be users and administrators must be users. Correct identity data before upgrading.");
            var rows = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM identity WHERE is_user=TRUE) OR EXISTS(SELECT 1 FROM launcher_group) OR EXISTS(SELECT 1 FROM health_metric) OR EXISTS(SELECT 1 FROM workout) OR EXISTS(SELECT 1 FROM location_history_entry) OR EXISTS(SELECT 1 FROM choices_daily_score) OR EXISTS(SELECT 1 FROM finance_account WHERE kind IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET')) OR EXISTS(SELECT 1 FROM calendar_calendar) OR EXISTS(SELECT 1 FROM finance_request)");
            rows.next();
            if (!rows.getBoolean(1)) return;
            rows = statement.executeQuery("SELECT COUNT(*) FROM identity WHERE id=1 AND is_user=TRUE AND is_group=FALSE");
            rows.next();
            if (rows.getInt(1) != 1) throw new IllegalStateException("Legacy users or personal data require identity 1 to be an existing non-group Thereabout user. Assign it before upgrading.");
        }
    }
}
