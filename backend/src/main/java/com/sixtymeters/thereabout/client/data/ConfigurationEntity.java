package com.sixtymeters.thereabout.client.data;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "configuration")
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
