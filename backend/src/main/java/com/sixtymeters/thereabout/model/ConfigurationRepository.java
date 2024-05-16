package com.sixtymeters.thereabout.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigurationRepository extends JpaRepository<ConfigurationEntity, ConfigurationKey> {
}
