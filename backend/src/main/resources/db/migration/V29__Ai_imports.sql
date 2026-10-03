ALTER TABLE configuration MODIFY config_value TEXT NOT NULL;
CREATE TABLE finance_import_source (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  fingerprint CHAR(64) NOT NULL,
  transaction_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  source_row LONGTEXT NOT NULL,
  duplicate_override BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_import_account FOREIGN KEY (account_id) REFERENCES finance_account(id),
  CONSTRAINT fk_import_transaction FOREIGN KEY (transaction_id) REFERENCES finance_transaction(id),
  INDEX ix_import_fingerprint (account_id, fingerprint)
);
