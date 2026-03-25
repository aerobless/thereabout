package com.sixtymeters.thereabout.communication.data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TelegramSyncCheckpointRepositoryTest {

    @Autowired
    private TelegramConnectionRepository connectionRepository;

    @Autowired
    private TelegramSyncCheckpointRepository checkpointRepository;

    @Test
    void deleteByConnectionRemovesOnlyMatchingCheckpoints() {
        TelegramConnectionEntity firstConnection = connectionRepository.save(TelegramConnectionEntity.builder()
                .authStatus("READY")
                .enabled(true)
                .phoneNumber("+41000000001")
                .build());
        TelegramConnectionEntity secondConnection = connectionRepository.save(TelegramConnectionEntity.builder()
                .authStatus("READY")
                .enabled(true)
                .phoneNumber("+41000000002")
                .build());

        checkpointRepository.save(TelegramSyncCheckpointEntity.builder()
                .connection(firstConnection)
                .chatId(101L)
                .lastMessageId(1000L)
                .backfillComplete(true)
                .build());
        checkpointRepository.save(TelegramSyncCheckpointEntity.builder()
                .connection(firstConnection)
                .chatId(102L)
                .lastMessageId(2000L)
                .backfillComplete(false)
                .build());
        checkpointRepository.save(TelegramSyncCheckpointEntity.builder()
                .connection(secondConnection)
                .chatId(201L)
                .lastMessageId(3000L)
                .backfillComplete(false)
                .build());

        long deleted = checkpointRepository.deleteByConnection(firstConnection);

        assertThat(deleted).isEqualTo(2);
        assertThat(checkpointRepository.findByConnection(firstConnection)).isEmpty();
        assertThat(checkpointRepository.findByConnection(secondConnection))
                .extracting(TelegramSyncCheckpointEntity::getChatId)
                .containsExactly(201L);
    }
}
