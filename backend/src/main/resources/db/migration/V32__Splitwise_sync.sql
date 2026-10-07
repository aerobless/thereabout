CREATE TABLE splitwise_connection (
 id BIGINT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, revision BIGINT NOT NULL DEFAULT 0,
 settings_json LONGTEXT NOT NULL, catalog_json LONGTEXT,
 tested BOOLEAN NOT NULL DEFAULT FALSE, initialized BOOLEAN NOT NULL DEFAULT FALSE,
 state VARCHAR(30) NOT NULL DEFAULT 'IDLE', error VARCHAR(1024),
 cursor_at DATETIME(6), last_success DATETIME(6), full_sync_at DATETIME(6),
 retry_at DATETIME(6), failures INT NOT NULL DEFAULT 0,
 preview_json LONGTEXT, snapshot_json LONGTEXT, ledger_hash VARCHAR(64),
 preview_id VARCHAR(64), preview_expires DATETIME(6),
 initialize_key VARCHAR(100), correction_members_json LONGTEXT,
 processed BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE splitwise_source (
 id VARCHAR(100) PRIMARY KEY, expense_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 group_id BIGINT NOT NULL, account_id BIGINT NOT NULL, transaction_id BIGINT,
 source_json LONGTEXT NOT NULL, applied_hash VARCHAR(64), applied_version BIGINT,
 state VARCHAR(30) NOT NULL, locally_managed BOOLEAN NOT NULL DEFAULT FALSE,
 classification VARCHAR(30), message VARCHAR(1024), row_json LONGTEXT,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY splitwise_expense_member (expense_id,member_id),
 INDEX splitwise_transaction (transaction_id), INDEX splitwise_group_state (group_id,state),
 FOREIGN KEY (transaction_id) REFERENCES finance_transaction(id) ON DELETE SET NULL
);
