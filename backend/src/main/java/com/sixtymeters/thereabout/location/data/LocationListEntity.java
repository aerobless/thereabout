package com.sixtymeters.thereabout.location.data;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "location_history_list")
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class LocationListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @OneToMany
    @JoinTable(
            name = "location_history_list_entries",
            joinColumns = @JoinColumn(name = "location_history_list_id"),
            inverseJoinColumns = @JoinColumn(name = "location_history_entry_id")
    )
    private List<LocationHistoryEntity> locationHistoryEntries;
}
