-- Health metrics base table
CREATE TABLE health_metric
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_name VARCHAR(255) NOT NULL,
    metric_date DATE         NOT NULL,
    timestamp   DATETIME(6),
    units       VARCHAR(50),
    qty         DECIMAL(10, 2),
    source      VARCHAR(255),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_metric_date_name (metric_name, metric_date),
    INDEX idx_metric_date (metric_date),
    INDEX idx_metric_timestamp (timestamp)
);

-- Blood pressure detail table
CREATE TABLE health_metric_blood_pressure
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id  BIGINT        NOT NULL,
    systolic         DECIMAL(10, 2) NOT NULL,
    diastolic        DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY fk_hm_bp (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Heart rate detail table
CREATE TABLE health_metric_heart_rate
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT        NOT NULL,
    min_value       DECIMAL(10, 2) NOT NULL,
    avg_value       DECIMAL(10, 2) NOT NULL,
    max_value       DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY fk_hm_hr (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Sleep detail table
CREATE TABLE health_metric_sleep
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT,
    total_sleep     DECIMAL(10, 2),
    asleep          DECIMAL(10, 2),
    awake           DECIMAL(10, 2),
    core            DECIMAL(10, 2),
    deep            DECIMAL(10, 2),
    rem             DECIMAL(10, 2),
    sleep_start     DATETIME(6),
    sleep_end       DATETIME(6),
    in_bed          DECIMAL(10, 2),
    in_bed_start    DATETIME(6),
    in_bed_end      DATETIME(6),
    FOREIGN KEY fk_hm_sleep (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Blood glucose detail table
CREATE TABLE health_metric_blood_glucose
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT       NOT NULL,
    meal_time       VARCHAR(50),
    FOREIGN KEY fk_hm_bg (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Sexual activity detail table
CREATE TABLE health_metric_sexual_activity
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id    BIGINT NOT NULL,
    unspecified        INT,
    protection_used    INT,
    protection_not_used INT,
    FOREIGN KEY fk_hm_sa (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Handwashing detail table
CREATE TABLE health_metric_handwashing
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT       NOT NULL,
    value           VARCHAR(50),
    FOREIGN KEY fk_hm_hw (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Toothbrushing detail table
CREATE TABLE health_metric_toothbrushing
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT       NOT NULL,
    value           VARCHAR(50),
    FOREIGN KEY fk_hm_tb (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Insulin detail table
CREATE TABLE health_metric_insulin
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT       NOT NULL,
    reason          VARCHAR(50),
    FOREIGN KEY fk_hm_insulin (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Heart rate notification detail table
CREATE TABLE health_metric_heart_rate_notification
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_id BIGINT        NOT NULL,
    start           DATETIME(6)    NOT NULL,
    end             DATETIME(6)    NOT NULL,
    threshold       DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY fk_hm_hrn (health_metric_id) REFERENCES health_metric (id) ON DELETE CASCADE
);

-- Heart rate notification data table (for heartRate array)
CREATE TABLE health_metric_heart_rate_notification_data
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    health_metric_hrn_id  BIGINT        NOT NULL,
    hr                    DECIMAL(10, 2) NOT NULL,
    units                 VARCHAR(50),
    timestamp_start       DATETIME(6),
    timestamp_end         DATETIME(6),
    interval_duration     DECIMAL(10, 2),
    interval_units        VARCHAR(50),
    FOREIGN KEY fk_hm_hrn_data (health_metric_hrn_id) REFERENCES health_metric_heart_rate_notification (id) ON DELETE CASCADE
);

-- Workout table
CREATE TABLE workout
(
    id                        VARCHAR(255) PRIMARY KEY,
    name                      VARCHAR(255),
    start                     DATETIME(6)  NOT NULL,
    end                       DATETIME(6)  NOT NULL,
    duration                  INT,
    location                  VARCHAR(50),
    active_energy_burned_qty  DECIMAL(10, 2),
    active_energy_burned_units VARCHAR(50),
    intensity_qty             DECIMAL(10, 2),
    intensity_units           VARCHAR(50),
    distance_qty              DECIMAL(10, 2),
    distance_units            VARCHAR(50),
    temperature_qty           DECIMAL(10, 2),
    temperature_units         VARCHAR(50),
    humidity_qty              DECIMAL(5, 2),
    humidity_units             VARCHAR(50),
    avg_speed_qty             DECIMAL(10, 2),
    avg_speed_units            VARCHAR(50),
    max_speed_qty             DECIMAL(10, 2),
    max_speed_units            VARCHAR(50),
    elevation_up_qty           DECIMAL(10, 2),
    elevation_up_units         VARCHAR(50),
    elevation_down_qty         DECIMAL(10, 2),
    elevation_down_units       VARCHAR(50),
    lap_length_qty             DECIMAL(10, 2),
    lap_length_units           VARCHAR(50),
    stroke_style               VARCHAR(50),
    swolf_score                INT,
    salinity                   VARCHAR(50),
    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workout_start (start),
    INDEX idx_workout_end (end)
);

-- Workout time series data table
CREATE TABLE workout_time_series_data
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    workout_id VARCHAR(255)  NOT NULL,
    data_type  VARCHAR(50)   NOT NULL,
    timestamp  DATETIME(6)   NOT NULL,
    qty        DECIMAL(10, 2),
    units      VARCHAR(50),
    source     VARCHAR(255),
    min_value  DECIMAL(10, 2),
    avg_value  DECIMAL(10, 2),
    max_value  DECIMAL(10, 2),
    FOREIGN KEY fk_workout_ts (workout_id) REFERENCES workout (id) ON DELETE CASCADE,
    INDEX idx_workout_ts_workout (workout_id, data_type)
);
