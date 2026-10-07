package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_counterparty")
public class FinanceCounterpartyEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, length = 1024) private String name;
  @Column(length = 2048) private String websiteUrl;
  private Long mergedIntoId;
  private Instant updatedAt = Instant.now();
  @Version private long version;
  @ElementCollection
  @CollectionTable(name = "finance_counterparty_alias", joinColumns = @JoinColumn(name = "counterparty_id"))
  @Column(name = "alias", nullable = false, length = 1024)
  private Set<String> aliases = new LinkedHashSet<>();
}
