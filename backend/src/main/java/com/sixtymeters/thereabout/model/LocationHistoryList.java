package com.sixtymeters.thereabout.model;

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
public class LocationHistoryList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @OneToMany
    private List<LocationHistoryEntity> locationHistoryEntries;
}
