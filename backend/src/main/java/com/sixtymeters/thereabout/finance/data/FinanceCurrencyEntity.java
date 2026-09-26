package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_currency")
public class FinanceCurrencyEntity {
  @Id
  @Column(length = 51)
  private String code;

  private String name;

  @Column(length = 51)
  private String symbol;

  private int decimalPlaces;
  private boolean enabled = true;
  private Long sourceId;
}
