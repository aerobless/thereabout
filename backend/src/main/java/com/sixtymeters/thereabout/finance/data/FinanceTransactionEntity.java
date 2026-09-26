package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_transaction")
public class FinanceTransactionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long sourceId;

  @Enumerated(EnumType.STRING)
  private TransactionType type;

  @Enumerated(EnumType.STRING)
  private FinancialEffect effect = FinancialEffect.OPERATING;

  @Column(length = 1024)
  private String description;

  private LocalDateTime occurredAt;
  private LocalDateTime updatedAt;
  private Long categoryId;

  @Column(columnDefinition = "LONGTEXT")
  private String notes;

  private boolean deleted;
  @Version private long version;

  @Column(length = 1024)
  private String externalReference;

  @Column(columnDefinition = "LONGTEXT")
  private String metadata;

  @OneToMany(
      mappedBy = "transaction",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  private List<FinancePostingEntity> postings = new ArrayList<>();

  /** Mark the aggregate dirty even when only a posting changes, so @Version protects both legs. */
  public void touch(Clock clock) {
    var now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    updatedAt = updatedAt == null || now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
  }

  public FinancePostingEntity posting(PostingSide side) {
    return postings.stream()
        .filter(p -> p.getSide() == side)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Missing " + side + " posting for transaction " + id));
  }

  public void addPosting(FinancePostingEntity posting) {
    posting.setTransaction(this);
    postings.add(posting);
  }
}
