ALTER TABLE finance_valuation
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE finance_valuation v JOIN finance_transaction t ON t.id = v.transaction_id
SET v.deleted = t.deleted;
