package com.sixtymeters.thereabout.location.data;

import com.sixtymeters.thereabout.location.service.statistics.CountryVisitInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistoryEntity, Long> {

    @Query("select l from LocationHistoryEntity l where l.timestamp between ?1 and ?2 and l.ignoreEntry = false order by l.timestamp")
    List<LocationHistoryEntity> findAllByTimestampBetween(LocalDateTime from, LocalDateTime to);

    @Query(
        value = """
                SELECT *
                FROM   location_history_entry l
                WHERE  l.timestamp BETWEEN :from AND :to
                  AND  l.ignore_entry = false
                  AND  RAND() < :sampleRatio        -- e.g. 0.05 = 5 %
                ORDER  BY l.timestamp
                """,
        nativeQuery = true)
      List<LocationHistoryEntity> findAllByTimestampBetweenSparseSample(LocalDateTime from, LocalDateTime to, double sampleRatio);

    @Query("""
        SELECT new com.sixtymeters.thereabout.location.service.statistics.CountryVisitInfo(
            l.estimatedIsoCountryCode,
            COUNT(DISTINCT CAST(l.timestamp AS date)),
            MIN(CAST(l.timestamp AS date)),
            MAX(CAST(l.timestamp AS date))
        )
        FROM LocationHistoryEntity l
        GROUP BY l.estimatedIsoCountryCode
    """)
    List<CountryVisitInfo> countDaysSpentInCountries();

}
