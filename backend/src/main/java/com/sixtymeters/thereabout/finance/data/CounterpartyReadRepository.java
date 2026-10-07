package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CounterpartyReadRepository {
  private final JdbcTemplate db;
  public record Selection(List<Long> ids, long total) {}

  public Selection list(String q, String kind, Boolean active, int page, int size) {
    var args = new ArrayList<Object>();
    String where = " WHERE c.merged_into_id IS NULL";
    if (!q.isBlank()) {
      where += " AND (c.name LIKE ? OR EXISTS(SELECT 1 FROM finance_counterparty_alias x WHERE x.counterparty_id=c.id AND x.alias LIKE ?))";
      args.add("%" + q + "%"); args.add("%" + q + "%");
    }
    if (kind != null || active != null) {
      where += " AND EXISTS(SELECT 1 FROM finance_account a WHERE a.counterparty_id=c.id AND a.deleted=FALSE";
      if (kind != null) { where += " AND a.kind=?"; args.add(kind); }
      if (active != null) { where += " AND a.active=?"; args.add(active); }
      where += ")";
    }
    long total = db.queryForObject("SELECT COUNT(*) FROM finance_counterparty c" + where, Long.class, args.toArray());
    args.add(size); args.add(page * size);
    var ids = db.queryForList("SELECT c.id FROM finance_counterparty c" + where + " ORDER BY c.name,c.id LIMIT ? OFFSET ?", Long.class, args.toArray());
    return new Selection(ids, total);
  }

  public long affectedTransactions(List<Long> ids) {
    String parameters = String.join(",", Collections.nCopies(ids.size(), "?"));
    return db.queryForObject("SELECT COUNT(DISTINCT p.transaction_id) FROM finance_posting p JOIN finance_account a ON a.id=p.account_id WHERE a.counterparty_id IN (" + parameters + ")", Long.class, ids.toArray());
  }
}
