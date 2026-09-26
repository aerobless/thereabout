ALTER TABLE finance_rate ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE finance_rate r SET version=(SELECT COALESCE(MAX(a.id),0) FROM finance_audit a WHERE a.operation='rates.save' AND a.entity_id=r.id) WHERE r.source='MANUAL';
