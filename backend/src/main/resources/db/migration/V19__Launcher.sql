-- Schema only. Personal bookmarks are imported through the runtime API.
CREATE TABLE launcher_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    section VARCHAR(160) NOT NULL,
    name VARCHAR(160) NOT NULL,
    position INT NOT NULL
);

CREATE TABLE launcher_shortcut (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    url VARCHAR(4096) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    emoji VARCHAR(32) NOT NULL DEFAULT '',
    position INT NOT NULL,
    icon_data MEDIUMBLOB,
    icon_type VARCHAR(50),
    icon_version BIGINT NOT NULL DEFAULT 0,
    icon_source VARCHAR(10) NOT NULL DEFAULT 'AUTO',
    icon_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (group_id) REFERENCES launcher_group(id),
    INDEX idx_launcher_shortcut_order (group_id, position, id)
);
