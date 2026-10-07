CREATE TABLE message_archive_user (
    message_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    PRIMARY KEY (message_id,user_id),
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES identity(id),
    INDEX idx_message_archive_user (user_id,message_id)
);
-- The existing shared ingestion belongs to the integration user, not to browser impersonation.
INSERT INTO message_archive_user SELECT m.id, i.id FROM message m JOIN identity i
    ON i.id=1 AND i.role IS NOT NULL AND i.is_group=FALSE;
ALTER TABLE identity ADD COLUMN membership_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE identity_group_member (
    group_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    PRIMARY KEY (group_id,user_id),
    FOREIGN KEY (group_id) REFERENCES identity(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES identity(id),
    INDEX idx_group_member_user (user_id,group_id)
);
