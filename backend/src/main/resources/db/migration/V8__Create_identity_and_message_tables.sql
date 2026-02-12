-- Identity table
CREATE TABLE identity
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_name   VARCHAR(50)  NOT NULL,
    first_name   VARCHAR(255),
    last_name    VARCHAR(255),
    email        VARCHAR(255),
    relationship VARCHAR(100),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_identity_short_name (short_name),
    INDEX idx_identity_email (email)
);

-- Identity in application table (e.g. phone number in WhatsApp, username in Telegram)
CREATE TABLE identity_in_application
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    identity_id   BIGINT       NOT NULL,
    application   VARCHAR(100) NOT NULL,
    identifier    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY fk_identity_app_identity (identity_id) REFERENCES identity (id) ON DELETE CASCADE,
    INDEX idx_identity_app_identity (identity_id),
    INDEX idx_identity_app_application (application),
    UNIQUE INDEX idx_identity_app_unique (application, identifier)
);

-- Message table
CREATE TABLE message
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    type        VARCHAR(50)  NOT NULL,
    source      VARCHAR(50)  NOT NULL,
    source_identifier VARCHAR(255),
    sender_id   BIGINT       NOT NULL,
    receiver_id BIGINT       NOT NULL,
    subject     VARCHAR(500),
    body        TEXT,
    timestamp   DATETIME(6)  NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY fk_message_sender (sender_id) REFERENCES identity_in_application (id),
    FOREIGN KEY fk_message_receiver (receiver_id) REFERENCES identity_in_application (id),
    INDEX idx_message_timestamp (timestamp),
    INDEX idx_message_type (type),
    INDEX idx_message_source (source),
    INDEX idx_message_source_identifier (source_identifier),
    INDEX idx_message_sender (sender_id),
    INDEX idx_message_receiver (receiver_id)
);
