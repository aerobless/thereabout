package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("SELECT m FROM MessageEntity m JOIN FETCH m.sender JOIN FETCH m.receiver WHERE m.timestamp BETWEEN ?1 AND ?2 ORDER BY m.timestamp")
    List<MessageEntity> findAllByTimestampBetween(LocalDateTime from, LocalDateTime to);
}
