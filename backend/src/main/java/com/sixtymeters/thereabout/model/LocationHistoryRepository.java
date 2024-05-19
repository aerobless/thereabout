package com.sixtymeters.thereabout.model;

import com.sixtymeters.thereabout.domain.statistics.CountryVisitInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistoryEntity, Long> {

    List<LocationHistoryEntity> findAllByTimestampBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT new com.sixtymeters.thereabout.domain.statistics.CountryVisitInfo(\
            l.estimatedIsoCountryCode, \
            COUNT(DISTINCT FUNCTION('DATE', l.timestamp)), \
            MIN(FUNCTION('DATE', l.timestamp)), \
            MAX(FUNCTION('DATE', l.timestamp))) \
            FROM LocationHistoryEntity l \
            GROUP BY l.estimatedIsoCountryCode""")
    List<CountryVisitInfo> countDaysSpentInCountries();
}
