ALTER TABLE identity ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing users could manage users before roles existed; keep that capability.
UPDATE identity SET is_admin = TRUE WHERE is_user = TRUE;
