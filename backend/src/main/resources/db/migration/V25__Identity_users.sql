ALTER TABLE identity ADD COLUMN is_user BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE identity ADD CONSTRAINT chk_identity_user_person CHECK (NOT is_user OR NOT is_group);
