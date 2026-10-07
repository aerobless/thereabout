package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_import_hint")
public class FinanceImportHintEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private Long accountId;
  @Column(nullable = false, length = 1000) private String text;
  @Version private long version;
  @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
}
