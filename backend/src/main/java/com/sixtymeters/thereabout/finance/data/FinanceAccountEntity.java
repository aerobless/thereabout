package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_account")
public class FinanceAccountEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long sourceId;

  @Column(length = 1024)
  private String name;

  @Enumerated(EnumType.STRING)
  private AccountKind kind;

  @Column(length = 51)
  private String currency;

  private boolean active = true;
  private boolean deleted;
  private boolean includeNetWorth;
  @Version private long version;

  @Column(columnDefinition = "LONGTEXT")
  private String metadata;
}
