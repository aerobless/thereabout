package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_audit")
public class FinanceAuditEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String operation;
  private Long entityId;
  private LocalDateTime createdAt;

  @Column(columnDefinition = "LONGTEXT")
  private String beforeJson;

  @Column(columnDefinition = "LONGTEXT")
  private String afterJson;
}
