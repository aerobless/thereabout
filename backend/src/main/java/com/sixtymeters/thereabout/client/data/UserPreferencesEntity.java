package com.sixtymeters.thereabout.client.data;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="user_preferences") @Getter @Setter @NoArgsConstructor
public class UserPreferencesEntity {
    @Id private Long userId;
    @Convert(converter=WeightGoalConverter.class) @Column(nullable=false, length=1000) private BigDecimal weightGoalKg;
    @Column(nullable=false) private LocalDate weightGoalStartedOn;
}
