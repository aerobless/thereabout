ALTER TABLE location_history_entry ADD COLUMN `ignore_entry` BOOLEAN DEFAULT FALSE;
ALTER TABLE location_history_entry ADD COLUMN `device_tag` VARCHAR(255);
ALTER TABLE location_history_entry ADD COLUMN `sensor_source` VARCHAR(255);