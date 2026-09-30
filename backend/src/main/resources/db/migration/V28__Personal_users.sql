ALTER TABLE identity ADD COLUMN role VARCHAR(10);
UPDATE identity SET role=CASE WHEN id=1 THEN 'ADMIN' ELSE 'USER' END WHERE is_user=TRUE;
ALTER TABLE identity DROP COLUMN is_user, DROP COLUMN is_admin,
    ADD CONSTRAINT ck_identity_role CHECK (role IS NULL OR (is_group=FALSE AND role IN ('ADMIN','USER')));

ALTER TABLE launcher_group ADD COLUMN user_id BIGINT;
UPDATE launcher_group SET user_id=1;
ALTER TABLE launcher_group MODIFY user_id BIGINT NOT NULL,
    ADD FOREIGN KEY (user_id) REFERENCES identity(id), ADD INDEX idx_launcher_user (user_id,position,id);
ALTER TABLE health_metric ADD COLUMN user_id BIGINT;
UPDATE health_metric SET user_id=1;
ALTER TABLE health_metric MODIFY user_id BIGINT NOT NULL,
    ADD FOREIGN KEY (user_id) REFERENCES identity(id), ADD INDEX idx_health_user (user_id,metric_name,metric_date);
ALTER TABLE location_history_entry ADD COLUMN user_id BIGINT;
UPDATE location_history_entry SET user_id=1;
ALTER TABLE location_history_entry MODIFY user_id BIGINT NOT NULL,
    ADD FOREIGN KEY (user_id) REFERENCES identity(id), ADD INDEX idx_location_user (user_id,timestamp);

ALTER TABLE workout_time_series_data DROP FOREIGN KEY fk_workout_ts;
ALTER TABLE workout CHANGE id source_id VARCHAR(255) NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY, ADD COLUMN user_id BIGINT;
UPDATE workout SET user_id=1;
ALTER TABLE workout MODIFY user_id BIGINT NOT NULL,
    ADD FOREIGN KEY (user_id) REFERENCES identity(id), ADD UNIQUE KEY uk_workout_source (user_id,source_id);
ALTER TABLE workout_time_series_data ADD COLUMN internal_workout_id BIGINT;
UPDATE workout_time_series_data d JOIN workout w ON d.workout_id=w.source_id SET d.internal_workout_id=w.id;
ALTER TABLE workout_time_series_data DROP COLUMN workout_id,
    CHANGE internal_workout_id workout_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_workout_ts FOREIGN KEY (workout_id) REFERENCES workout(id) ON DELETE CASCADE,
    ADD INDEX idx_workout_user_series (workout_id,data_type);

ALTER TABLE choices_daily_score ADD COLUMN user_id BIGINT;
UPDATE choices_daily_score SET user_id=1;
ALTER TABLE choices_daily_score MODIFY user_id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id,score_date), ADD FOREIGN KEY (user_id) REFERENCES identity(id);
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    weight_goal_kg VARCHAR(1000) NOT NULL,
    weight_goal_started_on DATE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES identity(id)
);
INSERT INTO user_preferences SELECT id,
    COALESCE((SELECT config_value FROM configuration WHERE config_key='WEIGHT_GOAL_KG'),'75.0'),
    COALESCE(CAST((SELECT config_value FROM configuration WHERE config_key='WEIGHT_GOAL_STARTED_ON') AS DATE),CURRENT_DATE)
    FROM identity WHERE id=1 AND role IS NOT NULL AND is_group=FALSE;
INSERT INTO user_preferences SELECT id,75.0,CURRENT_DATE FROM identity WHERE role IS NOT NULL AND id NOT IN (SELECT user_id FROM user_preferences);
DELETE FROM configuration WHERE config_key IN ('WEIGHT_GOAL_KG','WEIGHT_GOAL_STARTED_ON');

ALTER TABLE finance_account ADD COLUMN user_id BIGINT;
UPDATE finance_account SET user_id=1 WHERE kind IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET');
ALTER TABLE finance_account ADD FOREIGN KEY (user_id) REFERENCES identity(id),
    ADD CONSTRAINT ck_finance_owner CHECK (
      (kind IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET') AND user_id IS NOT NULL)
      OR (kind NOT IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET') AND user_id IS NULL)),
    ADD INDEX idx_finance_user (user_id,kind,id);
ALTER TABLE finance_request ADD COLUMN user_id BIGINT;
UPDATE finance_request SET user_id=1;
ALTER TABLE finance_request MODIFY user_id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id,request_key), ADD FOREIGN KEY (user_id) REFERENCES identity(id);
ALTER TABLE finance_audit ADD COLUMN actor_id BIGINT, ADD COLUMN user_id BIGINT;

CREATE TABLE calendar_user (
    calendar_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    PRIMARY KEY (calendar_id,user_id),
    FOREIGN KEY (calendar_id) REFERENCES calendar_calendar(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES identity(id)
);
INSERT INTO calendar_user SELECT id,1 FROM calendar_calendar;
