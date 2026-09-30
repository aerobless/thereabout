package com.sixtymeters.thereabout.choices.data;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "choices_daily_score")
@IdClass(ChoicesId.class)
@Getter
@NoArgsConstructor
public class ChoicesScore {
    @Id private Long userId;
    @Id
    private LocalDate scoreDate;
    @Column(nullable = false)
    private int score;
}
