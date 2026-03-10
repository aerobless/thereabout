package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramSyncCheckpointRepository extends JpaRepository<TelegramSyncCheckpointEntity, Long> {

    Optional<TelegramSyncCheckpointEntity> findByConnectionAndChatId(TelegramConnectionEntity connection, Long chatId);

    List<TelegramSyncCheckpointEntity> findByConnection(TelegramConnectionEntity connection);

    List<TelegramSyncCheckpointEntity> findByConnectionAndBackfillComplete(TelegramConnectionEntity connection, boolean backfillComplete);
}
