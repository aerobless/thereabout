CREATE TABLE trips
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    start DATE NOT NULL,
    end DATE NOT NULL,
    title VARCHAR(255),
    description VARCHAR(255),
    CONSTRAINT chk_end_after_start CHECK (end >= start)
);