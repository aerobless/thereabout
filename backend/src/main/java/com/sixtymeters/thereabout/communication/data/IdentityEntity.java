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

    @Column(nullable = false, length = 255)
    private String firstName;

    @Column(nullable = false, length = 255)
    @Builder.Default
    private String lastName = "";

    @Transient
    public String getFullName() {
        return java.util.stream.Stream.of(firstName, lastName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip).collect(java.util.stream.Collectors.joining(" "));
    }

    @Builder.Default
    private boolean isGroup = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private UserRole role;

    public boolean isUser() { return role != null; }
    public boolean isAdmin() { return role == UserRole.ADMIN; }

    @Version
    private long membershipVersion;

    @ElementCollection
    @CollectionTable(name = "identity_group_member", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private java.util.Set<Long> memberUserIds = new java.util.HashSet<>();

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
