package com.sixtymeters.thereabout.location.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LocationHistoryRepository extends JpaRepository<LocationHistoryEntity, Long> {
    @Query("select l from LocationHistoryEntity l where l.userId=?1 and l.timestamp between ?2 and ?3 and l.ignoreEntry=false order by l.timestamp")
    List<LocationHistoryEntity> findAllByUserIdAndTimestampBetween(long userId, LocalDateTime from, LocalDateTime to);
    @Query(value="SELECT * FROM location_history_entry WHERE user_id=:userId AND timestamp BETWEEN :from AND :to AND ignore_entry=false AND RAND()<:sampleRatio ORDER BY timestamp", nativeQuery=true)
    List<LocationHistoryEntity> findAllByUserIdAndTimestampBetweenSparseSample(long userId, LocalDateTime from, LocalDateTime to, double sampleRatio);
    Optional<LocationHistoryEntity> findByIdAndUserId(long id, long userId);
    List<LocationHistoryEntity> findByIdInAndUserId(List<Long> ids, long userId);
}
