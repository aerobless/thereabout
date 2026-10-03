package com.sixtymeters.thereabout.testing;

import org.springframework.jdbc.core.JdbcTemplate;

/** Stable personal owner for isolated integration fixtures; never used outside tests. */
public final class TestUsers {
    private TestUsers() {}
    public static void owner(JdbcTemplate db) {
        db.update("INSERT INTO identity(id,first_name,is_group,role) VALUES(1,'Test owner',FALSE,'ADMIN') ON DUPLICATE KEY UPDATE role='ADMIN',is_group=FALSE");
    }
}
