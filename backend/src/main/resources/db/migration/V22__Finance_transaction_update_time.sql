-- The aggregate timestamp also makes posting-only edits participate in JPA optimistic locking.
ALTER TABLE finance_transaction ADD COLUMN updated_at DATETIME(6) NULL;
