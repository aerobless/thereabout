CREATE TABLE calendar_local_deletion (
    calendar_id BIGINT NOT NULL,
    google_id VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_start VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    source_event_id VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deleted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (calendar_id, google_id, original_start),
    FOREIGN KEY (calendar_id) REFERENCES calendar_calendar(id) ON DELETE CASCADE
);
