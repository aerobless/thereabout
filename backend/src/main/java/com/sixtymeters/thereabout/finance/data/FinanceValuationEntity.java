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
@Table(name = "finance_valuation")
public class FinanceValuationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long accountId;
  private LocalDateTime occurredAt;

  @Column(precision = 36, scale = 24)
  private BigDecimal reportedValue;

  @Column(precision = 36, scale = 24)
  private BigDecimal previousBalance;

  private Long transactionId;
  private String reference;
  private String origin;
}
