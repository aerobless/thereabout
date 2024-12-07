package com.sixtymeters.thereabout.model;

import com.sixtymeters.thereabout.domain.statistics.CountryVisitInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistoryEntity, Long> {

    @Query("select l from LocationHistoryEntity l where l.timestamp between ?1 and ?2 and l.ignoreEntry = false")
    List<LocationHistoryEntity> findAllByTimestampBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT new com.sixtymeters.thereabout.domain.statistics.CountryVisitInfo(
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
