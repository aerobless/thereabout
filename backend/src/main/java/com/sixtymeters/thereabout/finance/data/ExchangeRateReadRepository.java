package com.sixtymeters.thereabout.finance.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExchangeRateReadRepository {
  private final JdbcTemplate db;

  public record Quote(BigDecimal rate, LocalDate date, String source) {}

  public Optional<Quote> quote(String from, LocalDate date) {
    if (from.equals("CHF")) return Optional.of(new Quote(BigDecimal.ONE, date, "IDENTITY"));
    return db
        .query(
            "SELECT rate,rate_date,source FROM finance_rate WHERE from_currency=? AND"
                + " to_currency='CHF' AND rate_date<=? ORDER BY rate_date DESC,CASE source WHEN"
                + " 'MANUAL' THEN 0 WHEN 'ECB' THEN 1 ELSE 2 END LIMIT 1",
            (r, n) ->
                new Quote(
                    r.getBigDecimal("rate"),
                    r.getDate("rate_date").toLocalDate(),
                    r.getString("source")),
            from,
            date)
        .stream()
        .findFirst();
  }
}
