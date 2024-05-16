package com.sixtymeters.thereabout.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationHistoryRepository extends JpaRepository<LocationHistoryEntity, Long> {

    long countBySource(LocationHistorySource source);

    List<LocationHistoryEntity> findAllByTimestampBetween(LocalDateTime from, LocalDateTime to);
}
