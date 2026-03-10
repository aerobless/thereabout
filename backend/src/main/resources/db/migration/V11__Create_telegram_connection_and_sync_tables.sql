-- Single instance-wide Telegram connection state (no password or token stored here; TDLib keeps session in its database_directory).
CREATE TABLE telegram_connection
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    auth_status     VARCHAR(50)  NOT NULL,
    phone_number    VARCHAR(50),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_sync_at    TIMESTAMP(6) NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_telegram_connection_enabled (enabled)
);

-- At most one row: one Telegram account per instance.
INSERT INTO telegram_connection (auth_status, enabled)
VALUES ('DISCONNECTED', FALSE);

-- Per-chat sync checkpoint for resumable full-history backfill and continuous sync.
CREATE TABLE telegram_sync_checkpoint
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id     BIGINT       NOT NULL,
    chat_id           BIGINT       NOT NULL,
    last_message_id   BIGINT       NOT NULL DEFAULT 0,
    backfill_complete BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY fk_telegram_checkpoint_connection (connection_id) REFERENCES telegram_connection (id) ON DELETE CASCADE,
    UNIQUE INDEX idx_telegram_checkpoint_connection_chat (connection_id, chat_id),
    INDEX idx_telegram_checkpoint_connection (connection_id)
);
