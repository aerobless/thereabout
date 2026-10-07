package com.sixtymeters.thereabout.finance.splitwise;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "splitwise_connection") @Getter @Setter
public class SplitwiseConnection {
  @Id private Long id = 1L;
  @Version private long version;
  private long revision;
  @Column(columnDefinition = "LONGTEXT") private String settingsJson;
  @Column(columnDefinition = "LONGTEXT") private String catalogJson;
  private boolean tested;
  private boolean initialized;
  private String state = "IDLE";
  private String error;
  private LocalDateTime cursorAt;
  private LocalDateTime lastSuccess;
  private LocalDateTime fullSyncAt;
  private LocalDateTime retryAt;
  private int failures;
  @Column(columnDefinition = "LONGTEXT") private String previewJson;
  @Column(columnDefinition = "LONGTEXT") private String snapshotJson;
  private String ledgerHash;
  private String previewId;
  private LocalDateTime previewExpires;
  private String initializeKey;
  @Column(columnDefinition = "LONGTEXT") private String correctionMembersJson;
  private long processed;
}
