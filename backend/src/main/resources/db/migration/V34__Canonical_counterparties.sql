CREATE TABLE finance_counterparty (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(1024) NOT NULL,
    website_url VARCHAR(2048),
    merged_into_id BIGINT,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (merged_into_id) REFERENCES finance_counterparty(id)
);
CREATE TABLE finance_counterparty_alias (
    counterparty_id BIGINT NOT NULL,
    alias VARCHAR(1024) NOT NULL,
    FOREIGN KEY (counterparty_id) REFERENCES finance_counterparty(id),
    INDEX idx_counterparty_alias (counterparty_id),
    INDEX idx_alias_name (alias(100))
);
ALTER TABLE finance_account ADD COLUMN counterparty_id BIGINT,
    ADD FOREIGN KEY (counterparty_id) REFERENCES finance_counterparty(id),
    ADD INDEX idx_account_counterparty (counterparty_id);
INSERT INTO finance_counterparty (id,name,website_url)
    SELECT id,name,website_url FROM finance_account WHERE kind IN ('EXPENSE','REVENUE');
INSERT INTO finance_counterparty_alias (counterparty_id,alias)
    SELECT id,name FROM finance_counterparty;
UPDATE finance_account SET counterparty_id=id WHERE kind IN ('EXPENSE','REVENUE');
