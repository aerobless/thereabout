-- Reject invalid legacy names before adding or dropping any name columns.
ALTER TABLE identity ADD CONSTRAINT identity_legacy_name_must_not_be_blank
    CHECK (CHAR_LENGTH(TRIM(REGEXP_REPLACE(short_name, '[[:space:]]+', ' '))) > 0);

ALTER TABLE identity
    ADD first_name VARCHAR(255) NOT NULL DEFAULT '',
    ADD last_name VARCHAR(255) NOT NULL DEFAULT '';

UPDATE identity SET
    first_name = CASE WHEN is_group THEN TRIM(REGEXP_REPLACE(short_name, '[[:space:]]+', ' '))
        ELSE SUBSTRING_INDEX(TRIM(REGEXP_REPLACE(short_name, '[[:space:]]+', ' ')), ' ', 1) END,
    last_name = CASE WHEN is_group THEN ''
        ELSE TRIM(SUBSTRING(TRIM(REGEXP_REPLACE(short_name, '[[:space:]]+', ' ')),
            CHAR_LENGTH(SUBSTRING_INDEX(TRIM(REGEXP_REPLACE(short_name, '[[:space:]]+', ' ')), ' ', 1)) + 1)) END;

ALTER TABLE identity
    DROP CONSTRAINT identity_legacy_name_must_not_be_blank,
    DROP INDEX idx_identity_short_name,
    DROP COLUMN short_name,
    MODIFY first_name VARCHAR(255) NOT NULL,
    ADD INDEX idx_identity_name (first_name, last_name);
