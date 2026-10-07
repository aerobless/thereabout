CREATE TABLE finance_import_hint (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  text VARCHAR(1000) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_import_hint_account FOREIGN KEY (account_id) REFERENCES finance_account(id),
  INDEX ix_import_hint_account (account_id, id)
);
