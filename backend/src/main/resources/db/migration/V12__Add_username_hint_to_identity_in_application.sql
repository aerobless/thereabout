ALTER TABLE identity_in_application ADD COLUMN username_hint VARCHAR(255) NULL;
ALTER TABLE identity_in_application ADD COLUMN is_group BOOLEAN NOT NULL DEFAULT FALSE;
