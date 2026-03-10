package com.sixtymeters.thereabout.communication.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Per-chat sync checkpoint for resumable full-history backfill and continuous sync.
 */
@Getter
@Setter
@Entity
@Table(name = "telegram_sync_checkpoint")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TelegramSyncCheckpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private TelegramConnectionEntity connection;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "last_message_id", nullable = false)
    @Builder.Default
    private Long lastMessageId = 0L;

    @Column(name = "backfill_complete", nullable = false)
    @Builder.Default
    private Boolean backfillComplete = false;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
