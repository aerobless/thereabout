CREATE TABLE finance_currency (
 code VARCHAR(51) PRIMARY KEY, name VARCHAR(255) NOT NULL, symbol VARCHAR(51) NOT NULL,
 decimal_places INT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, source_id BIGINT UNIQUE
);
CREATE TABLE finance_account (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT UNIQUE, name VARCHAR(1024) NOT NULL,
 kind VARCHAR(30) NOT NULL, currency VARCHAR(51), active BOOLEAN NOT NULL DEFAULT TRUE,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, include_net_worth BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0, metadata LONGTEXT,
 FOREIGN KEY (currency) REFERENCES finance_currency(code)
);
CREATE TABLE finance_category (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT UNIQUE, name VARCHAR(1024) NOT NULL,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0, metadata LONGTEXT
);
CREATE TABLE finance_transaction (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT UNIQUE, type VARCHAR(30) NOT NULL,
 effect VARCHAR(30) NOT NULL DEFAULT 'OPERATING', description VARCHAR(1024) NOT NULL,
 occurred_at DATETIME(6) NOT NULL, category_id BIGINT, notes LONGTEXT,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0,
 external_reference VARCHAR(1024), metadata LONGTEXT,
 FOREIGN KEY (category_id) REFERENCES finance_category(id), INDEX finance_tx_date (occurred_at,id)
);
CREATE TABLE finance_posting (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT UNIQUE,
 transaction_id BIGINT NOT NULL, account_id BIGINT NOT NULL, side VARCHAR(15) NOT NULL,
 amount DECIMAL(36,24) NOT NULL, currency VARCHAR(51) NOT NULL,
 foreign_amount DECIMAL(36,24), foreign_currency VARCHAR(51), deleted BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY (transaction_id) REFERENCES finance_transaction(id),
 FOREIGN KEY (account_id) REFERENCES finance_account(id), FOREIGN KEY (currency) REFERENCES finance_currency(code),
 FOREIGN KEY (foreign_currency) REFERENCES finance_currency(code),
 UNIQUE KEY finance_posting_side (transaction_id,side), INDEX finance_posting_account (account_id,transaction_id)
);
CREATE TABLE finance_valuation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, account_id BIGINT NOT NULL, occurred_at DATETIME(6) NOT NULL,
 reported_value DECIMAL(36,24) NOT NULL, previous_balance DECIMAL(36,24) NOT NULL,
 transaction_id BIGINT UNIQUE, reference VARCHAR(255) NOT NULL, origin VARCHAR(30) NOT NULL,
 FOREIGN KEY (account_id) REFERENCES finance_account(id), FOREIGN KEY (transaction_id) REFERENCES finance_transaction(id),
 UNIQUE KEY finance_valuation_reference (account_id,reference)
);
CREATE TABLE finance_rate (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, from_currency VARCHAR(51) NOT NULL, to_currency VARCHAR(51) NOT NULL,
 rate_date DATE NOT NULL, rate DECIMAL(36,24) NOT NULL, source VARCHAR(30) NOT NULL,
 UNIQUE KEY finance_rate_unique (from_currency,to_currency,rate_date,source)
);
CREATE TABLE finance_archive (
 source_table VARCHAR(100) NOT NULL, source_key VARCHAR(255) NOT NULL, payload LONGTEXT NOT NULL,
 PRIMARY KEY (source_table,source_key)
);
CREATE TABLE finance_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, operation VARCHAR(80) NOT NULL, entity_id BIGINT,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), before_json LONGTEXT, after_json LONGTEXT
);
CREATE TABLE finance_request (
 request_key VARCHAR(100) PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL, result_json LONGTEXT
);
CREATE TABLE finance_write_lock (id INT PRIMARY KEY);
INSERT INTO finance_write_lock VALUES (1);
