package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_rate")
public class FinanceRateEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 51)
  private String fromCurrency;

  @Column(length = 51)
  private String toCurrency;

  private LocalDate rateDate;

  @Column(precision = 36, scale = 24)
  private BigDecimal rate;

  private String source;
  @Version private long version;
}
