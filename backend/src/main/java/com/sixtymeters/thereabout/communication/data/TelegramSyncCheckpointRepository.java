package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramSyncCheckpointRepository extends JpaRepository<TelegramSyncCheckpointEntity, Long> {

    Optional<TelegramSyncCheckpointEntity> findByConnectionAndChatId(TelegramConnectionEntity connection, Long chatId);

    List<TelegramSyncCheckpointEntity> findByConnection(TelegramConnectionEntity connection);

    List<TelegramSyncCheckpointEntity> findByConnectionAndBackfillComplete(TelegramConnectionEntity connection, boolean backfillComplete);

    @Modifying
    @Transactional
    long deleteByConnection(TelegramConnectionEntity connection);
}
