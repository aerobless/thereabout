package com.sixtymeters.thereabout.client.data;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigurationRepository extends JpaRepository<ConfigurationEntity, ConfigurationKey> {
}
