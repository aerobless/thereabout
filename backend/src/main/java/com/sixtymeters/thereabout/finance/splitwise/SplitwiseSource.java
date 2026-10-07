package com.sixtymeters.thereabout.finance.splitwise;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "splitwise_source") @Getter @Setter
public class SplitwiseSource {
  @Id private String id;
  private long expenseId;
  private long memberId;
  private long groupId;
  private long accountId;
  private Long transactionId;
  @Column(columnDefinition = "LONGTEXT") private String sourceJson;
  private String appliedHash;
  private Long appliedVersion;
  private String state;
  private boolean locallyManaged;
  private String classification;
  private String message;
  @Column(columnDefinition = "LONGTEXT") private String rowJson;
  @Version private long version;
}
