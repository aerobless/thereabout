ALTER TABLE configuration MODIFY config_value TEXT NOT NULL;
CREATE TABLE calendar_connection (
    id INT PRIMARY KEY,
    account VARCHAR(255),
    webhook_url TEXT,
    state VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED',
    error TEXT,
    pending_version BIGINT NOT NULL DEFAULT 0,
    processed_version BIGINT NOT NULL DEFAULT 0,
    next_safety_at TIMESTAMP NULL,
    retry_at TIMESTAMP NULL,
    failures INT NOT NULL DEFAULT 0
);
INSERT INTO calendar_connection (id) VALUES (1);
CREATE TABLE calendar_calendar (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account VARCHAR(255) NOT NULL,
    google_id VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    name TEXT NOT NULL,
    color VARCHAR(20),
    time_zone VARCHAR(100) NOT NULL,
    access_role VARCHAR(40) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    sync_token TEXT,
    active_generation VARCHAR(36),
    full_sync BOOLEAN NOT NULL DEFAULT FALSE,
    pending_version BIGINT NOT NULL DEFAULT 0,
    processed_version BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    error TEXT,
    imported_count INT NOT NULL DEFAULT 0,
    last_sync_at TIMESTAMP NULL,
    retry_at TIMESTAMP NULL,
    failures INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_calendar_account (account, google_id)
);
CREATE TABLE calendar_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    calendar_id BIGINT NOT NULL,
    generation VARCHAR(36) NOT NULL,
    google_id VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title TEXT,
    location TEXT,
    description LONGTEXT,
    start_time DATETIME(6),
    end_time DATETIME(6),
    start_date DATE,
    end_date DATE,
    time_zone VARCHAR(100),
    status VARCHAR(30),
    recurring_event_id VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin,
    original_start VARCHAR(100),
    recurrence TEXT,
    payload LONGTEXT NOT NULL,
    FOREIGN KEY (calendar_id) REFERENCES calendar_calendar(id) ON DELETE CASCADE,
    UNIQUE KEY uk_calendar_event (calendar_id, generation, google_id),
    INDEX idx_calendar_event_time (calendar_id, generation, start_time, end_time)
);
CREATE TABLE calendar_participant (
    event_id BIGINT NOT NULL,
    application_identity_id BIGINT NOT NULL,
    display_name TEXT,
    response_status VARCHAR(30),
    organizer BOOLEAN NOT NULL DEFAULT FALSE,
    self BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (event_id, application_identity_id),
    FOREIGN KEY (event_id) REFERENCES calendar_event(id) ON DELETE CASCADE,
    FOREIGN KEY (application_identity_id) REFERENCES identity_in_application(id)
);
CREATE TABLE calendar_channel (
    id VARCHAR(36) PRIMARY KEY,
    calendar_id BIGINT NULL,
    token_hash VARCHAR(64) NOT NULL,
    resource_id VARCHAR(512) COLLATE utf8mb4_bin,
    early_resource_id VARCHAR(512) COLLATE utf8mb4_bin,
    state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    renew_at TIMESTAMP NULL,
    last_notification_at TIMESTAMP NULL,
    FOREIGN KEY (calendar_id) REFERENCES calendar_calendar(id) ON DELETE CASCADE,
    INDEX idx_calendar_channel_resource (calendar_id, state)
);
