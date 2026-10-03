package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_import_source")
public class FinanceImportSourceEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long accountId;

  @Column(length = 64, nullable = false)
  private String fingerprint;

  private Long transactionId;

  @Column(length = 255, nullable = false)
  private String fileName;

  @Column(columnDefinition = "LONGTEXT", nullable = false)
  private String sourceRow;

  private boolean duplicateOverride;
}
