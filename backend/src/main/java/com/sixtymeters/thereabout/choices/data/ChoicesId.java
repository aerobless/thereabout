package com.sixtymeters.thereabout.choices.data;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.*;
@Getter @Setter @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
public class ChoicesId implements Serializable { private Long userId; private LocalDate scoreDate; }
