package com.sixtymeters.thereabout.finance.data;

import java.io.Serializable;
import lombok.*;
@Getter @Setter @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
public class FinanceRequestId implements Serializable { private Long userId; private String requestKey; }
