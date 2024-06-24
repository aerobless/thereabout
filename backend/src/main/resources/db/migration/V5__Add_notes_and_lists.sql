CREATE TABLE thereabout.location_history_list
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE thereabout.location_history_list_entries
(
    location_history_list_id  BIGINT,
    location_history_entry_id BIGINT,
    PRIMARY KEY (location_history_list_id, location_history_entry_id),
    CONSTRAINT fk_location_history_list FOREIGN KEY (location_history_list_id) REFERENCES thereabout.location_history_list (id) ON DELETE CASCADE,
    CONSTRAINT fk_location_history_entry FOREIGN KEY (location_history_entry_id) REFERENCES thereabout.location_history_entry (id) ON DELETE CASCADE
);

ALTER TABLE thereabout.location_history_entry
    ADD COLUMN note VARCHAR(255);