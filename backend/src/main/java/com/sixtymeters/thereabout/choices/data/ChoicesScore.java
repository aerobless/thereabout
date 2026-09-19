package com.sixtymeters.thereabout.choices.data;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "choices_daily_score")
@Getter
@NoArgsConstructor
public class ChoicesScore {
    @Id
    private LocalDate scoreDate;
    @Column(nullable = false)
    private int score;
}
