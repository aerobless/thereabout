package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "finance_request")
public class FinanceRequestEntity {
  @Id
  @Column(length = 100)
  private String requestKey;

  @Column(length = 64)
  private String fingerprint;

  @Column(columnDefinition = "LONGTEXT")
  private String resultJson;
}
