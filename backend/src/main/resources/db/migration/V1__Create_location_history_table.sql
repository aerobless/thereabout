CREATE TABLE location_history_entry
(
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME(6) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    horizontal_accuracy INT,
    vertical_accuracy INT,
    altitude INT,
    heading INT,
    velocity INT,
    source VARCHAR(255) NOT NULL,
    INDEX timestamp_idx (timestamp),
    INDEX lat_long_idx (latitude, longitude)
);