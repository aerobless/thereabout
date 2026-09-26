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
@Table(name = "finance_posting")
public class FinancePostingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long sourceId;
  private Long accountId;

  @Enumerated(EnumType.STRING)
  private PostingSide side;

  @Column(precision = 36, scale = 24)
  private BigDecimal amount;

  @Column(length = 51)
  private String currency;

  @Column(precision = 36, scale = 24)
  private BigDecimal foreignAmount;

  @Column(length = 51)
  private String foreignCurrency;

  private boolean deleted;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id")
  private FinanceTransactionEntity transaction;
}
