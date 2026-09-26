package com.sixtymeters.thereabout.client.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ConfigurationRepository extends JpaRepository<ConfigurationEntity, ConfigurationKey> {
    /** Atomic first-start initialization: concurrent application instances retain the first key. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO configuration (config_key, config_value) VALUES ('FINANCE_MCP_KEY', :value)
            ON DUPLICATE KEY UPDATE config_key = config_key
            """, nativeQuery = true)
    void insertMcpKeyIfAbsent(@Param("value") String value);
}
