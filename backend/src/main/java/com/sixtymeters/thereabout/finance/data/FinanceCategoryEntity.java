package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_category")
public class FinanceCategoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long sourceId;

  @Column(length = 1024)
  private String name;

  private boolean deleted;
  @Version private long version;

  @Column(columnDefinition = "LONGTEXT")
  private String metadata;
}
