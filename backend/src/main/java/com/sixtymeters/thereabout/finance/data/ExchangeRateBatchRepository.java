package com.sixtymeters.thereabout.finance.data;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The deliberate SQL write exception: bulk storage of downloaded reference rates. */
@Repository
@RequiredArgsConstructor
public class ExchangeRateBatchRepository {
  private final JdbcTemplate db;

  /** Batch-only path; invoked inside the write coordinator after the network request completes. */
  public void saveEcb(List<ReferenceRate> rates) {
    db.batchUpdate(
        "INSERT INTO finance_rate(from_currency,to_currency,rate_date,rate,source)"
            + " VALUES(?,?,?,?,'ECB') ON DUPLICATE KEY UPDATE rate=VALUES(rate),version=version+1",
        rates,
        1000,
        (p, r) -> {
          p.setString(1, r.from());
          p.setString(2, r.to());
          p.setObject(3, r.date());
          p.setBigDecimal(4, r.rate());
        });
  }
}
