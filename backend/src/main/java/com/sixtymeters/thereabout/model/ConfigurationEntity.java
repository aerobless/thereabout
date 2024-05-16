package com.sixtymeters.thereabout.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity(name = "configuration")
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ConfigurationEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private ConfigurationKey configKey;

    @Column
    private String configValue;
}
