package com.sixtymeters.thereabout.communication.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "identity")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shortName;

    @Builder.Default
    private boolean isGroup = false;

    private String relationship;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @OneToMany(mappedBy = "identity", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IdentityInApplicationEntity> identityInApplications = new ArrayList<>();

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
