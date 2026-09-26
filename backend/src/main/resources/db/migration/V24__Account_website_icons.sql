ALTER TABLE finance_account ADD COLUMN website_url VARCHAR(2048) NULL;

CREATE TABLE website_icon (
    id VARCHAR(64) PRIMARY KEY,
    image_data MEDIUMBLOB NULL,
    content_type VARCHAR(50) NULL,
    checked_at DATETIME(6) NOT NULL
);
