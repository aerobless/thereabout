package com.sixtymeters.thereabout.choices.data;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface ChoicesRepository extends JpaRepository<ChoicesScore, ChoicesId> {
    List<ChoicesScore> findByUserIdAndScoreDateBetweenOrderByScoreDate(long userId, LocalDate from, LocalDate to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO choices_daily_score (user_id, score_date, score) VALUES (:userId, :date, :delta) " +
            "ON DUPLICATE KEY UPDATE score = score + :delta", nativeQuery = true)
    void adjust(@Param("userId") long userId, @Param("date") LocalDate date, @Param("delta") int delta);
}
