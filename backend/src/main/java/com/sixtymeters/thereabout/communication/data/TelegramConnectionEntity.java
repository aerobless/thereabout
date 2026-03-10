package com.sixtymeters.thereabout.communication.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Single instance-wide Telegram connection state.
 * No password or session token is stored here; TDLib keeps the authorized session in its database_directory.
 */
@Getter
@Setter
@Entity
@Table(name = "telegram_connection")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TelegramConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_status", nullable = false)
    private String authStatus;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

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
