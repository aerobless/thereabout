package com.sixtymeters.thereabout.finance.data;

import static com.sixtymeters.thereabout.finance.data.FinanceRowMappers.*;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL read projections; writes and financial validation belong to application services. */
@Repository
@RequiredArgsConstructor
public class FinanceReadRepository {
  private final JdbcTemplate db;
  private final Clock financeClock;

  public record LedgerEntry(
      long accountId,
      BigDecimal amount,
      String currency,
      LocalDateTime occurredAt,
      TransactionType type,
      FinancialEffect effect,
      Long categoryId,
      String categoryName) {}

  public List<LedgerEntry> ledger() {
    return db.query(
        "SELECT p.account_id,p.amount,p.currency,t.occurred_at,t.type,t.effect,t.category_id,c.name"
            + " category_name FROM finance_posting p JOIN finance_transaction t ON"
            + " t.id=p.transaction_id LEFT JOIN finance_category c ON c.id=t.category_id WHERE"
            + " p.deleted=FALSE AND t.deleted=FALSE ORDER BY t.occurred_at,t.id",
        (r, n) ->
            new LedgerEntry(
                r.getLong("account_id"),
                r.getBigDecimal("amount"),
                r.getString("currency"),
                r.getTimestamp("occurred_at").toLocalDateTime(),
                TransactionType.valueOf(r.getString("type")),
                FinancialEffect.valueOf(r.getString("effect")),
                r.getObject("category_id", Long.class),
                r.getString("category_name")));
  }

  public List<GenFinanceAccount> ownAccounts() {
    return db.query(
        "SELECT a.*,0 balance FROM finance_account a WHERE deleted=FALSE AND kind IN"
            + " ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET') ORDER BY kind,name,id",
        ACCOUNT);
  }

  public GenFinanceAccount account(long id) {
    return db.query("SELECT a.*,0 balance FROM finance_account a WHERE id=?", ACCOUNT, id).stream()
        .findFirst()
        .orElseThrow(() -> missing("Account"));
  }

  public GenFinanceCategory category(long id) {
    return db.query("SELECT * FROM finance_category WHERE id=?", CATEGORY, id).stream()
        .findFirst()
        .orElseThrow(() -> missing("Category"));
  }

  public GenFinanceCategoryList categories() {
    return new GenFinanceCategoryList()
        .items(
            db.query(
                "SELECT * FROM finance_category WHERE deleted=FALSE ORDER BY name,id", CATEGORY));
  }

  public GenFinanceCurrencyList currencies() {
    return new GenFinanceCurrencyList()
        .items(db.query("SELECT * FROM finance_currency ORDER BY code", CURRENCY));
  }

  public List<GenFinanceAudit> history(long id) {
    return db.query(
        "SELECT * FROM finance_audit WHERE entity_id=? AND operation LIKE 'transactions.%' ORDER BY"
            + " id DESC",
        AUDIT, id);
  }

  public GenFinanceValuation valuation(long id) {
    return db
        .query(
            "SELECT v.*,a.name,a.currency FROM finance_valuation v JOIN finance_account a ON"
                + " a.id=v.account_id WHERE v.id=?",
            VALUATION,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> missing("Valuation"));
  }

  public GenFinanceValuationList valuations(Long accountId) {
    long id = accountId == null ? 0 : accountId;
    return new GenFinanceValuationList()
        .items(
            db.query(
                "SELECT v.*,a.name,a.currency FROM finance_valuation v JOIN finance_account a ON"
                    + " a.id=v.account_id WHERE (?=0 OR a.id=?) ORDER BY v.occurred_at DESC,v.id"
                    + " DESC LIMIT 200",
                VALUATION,
                id,
                id));
  }

  public GenFinanceRate rate(long id) {
    return db.query("SELECT * FROM finance_rate WHERE id=?", RATE, id).stream()
        .findFirst()
        .orElseThrow(() -> missing("Rate"));
  }

  public GenFinanceRateList rates(GenFinanceRateQuery query) {
    return new GenFinanceRateList()
        .items(
            db.query(
                "SELECT * FROM finance_rate WHERE (?='' OR from_currency=?) AND (?='' OR"
                    + " rate_date=?) ORDER BY rate_date DESC,id DESC LIMIT 300",
                RATE,
                text(query.getFromCurrency()),
                text(query.getFromCurrency()),
                text(query.getDate()),
                text(query.getDate())));
  }

  public String latestTransaction() {
    return db.queryForObject(
        "SELECT MAX(occurred_at) FROM finance_transaction WHERE deleted=FALSE", String.class);
  }

  public String earliestTransaction() {
    return db.queryForObject(
        "SELECT MIN(occurred_at) FROM finance_transaction WHERE deleted=FALSE", String.class);
  }

  private long count(String sql, List<Object> args) {
    return db.queryForObject(sql, Long.class, args.toArray());
  }

  public GenFinanceAccountPage accounts(GenFinanceAccountQuery p) {
    String scope = p.getScope() == null ? "OWN" : p.getScope().getValue();
    String q = text(p.getQ());
    int page = page(p.getPage()), size = pageSize(p.getPageSize());
    List<Object> args = new ArrayList<>();
    String where = " WHERE 1=1";
    if (!Boolean.TRUE.equals(p.getIncludeDeleted())) where += " AND a.deleted=FALSE";
    if (!Boolean.TRUE.equals(p.getIncludeInactive())) where += " AND a.active=TRUE";
    if (scope.equals("OWN"))
      where += " AND a.kind IN ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET')";
    if (scope.equals("COUNTERPARTY")) where += " AND a.kind IN ('EXPENSE','REVENUE')";
    if (!q.isBlank()) {
      where += " AND a.name LIKE ?";
      args.add("%" + q + "%");
    }
    if (p.getKind() != null) {
      where += " AND a.kind=?";
      args.add(p.getKind().getValue());
    }
    if (p.getId() != null) {
      where += " AND a.id=?";
      args.add(p.getId());
    }
    long total = count("SELECT COUNT(*) FROM finance_account a" + where, args);
    List<Object> queryArgs = new ArrayList<>();
    queryArgs.add(dateTime(p.getAsOf(), LocalDateTime.now(financeClock), false));
    queryArgs.addAll(args);
    queryArgs.add(size);
    queryArgs.add(page * size);
    var items =
        db.query(
            "SELECT a.*,COALESCE((SELECT SUM(b.amount) FROM finance_posting b JOIN"
                + " finance_transaction t ON t.id=b.transaction_id WHERE b.account_id=a.id AND"
                + " b.deleted=FALSE AND t.deleted=FALSE AND t.occurred_at<=?),0) balance FROM"
                + " finance_account a"
                + where
                + " ORDER BY a.kind,a.name,a.id LIMIT ? OFFSET ?",
            ACCOUNT,
            queryArgs.toArray());
    return new GenFinanceAccountPage().items(items).total(total).page(page).pageSize(size);
  }

  private static final String TX_SELECT =
      "SELECT t.*,s.account_id source_id_account,d.account_id destination_id_account,sa.name"
          + " source_name,da.name destination_name,sa.kind source_kind,da.kind"
          + " destination_kind,ABS(s.amount) source_amount,ABS(d.amount)"
          + " destination_amount,s.currency source_currency,d.currency"
          + " destination_currency,s.foreign_amount,s.foreign_currency,c.name category_name,(SELECT"
          + " v.id FROM finance_valuation v WHERE v.transaction_id=t.id) valuation_id ";
  private static final String TX_FROM =
      " FROM finance_transaction t JOIN finance_posting s ON s.transaction_id=t.id AND"
          + " s.side='SOURCE' JOIN finance_posting d ON d.transaction_id=t.id AND"
          + " d.side='DESTINATION' JOIN finance_account sa ON sa.id=s.account_id JOIN"
          + " finance_account da ON da.id=d.account_id LEFT JOIN finance_category c ON"
          + " c.id=t.category_id ";

  public GenFinanceTransaction transaction(long id) {
    return db.query(TX_SELECT + TX_FROM + " WHERE t.id=?", TRANSACTION, id).stream()
        .findFirst()
        .orElseThrow(() -> missing("Transaction"));
  }

  public GenFinanceTransactionPage transactions(GenFinanceTransactionQuery p) {
    List<Object> args = new ArrayList<>();
    String where = " WHERE 1=1";
    if (!Boolean.TRUE.equals(p.getIncludeDeleted()))
      where += " AND t.deleted=FALSE AND s.deleted=FALSE AND d.deleted=FALSE";
    long account = p.getAccountId() == null ? 0 : p.getAccountId();
    if (account != 0) {
      where += " AND (s.account_id=? OR d.account_id=?)";
      args.add(account);
      args.add(account);
    }
    String q = text(p.getQ());
    if (!q.isBlank()) {
      where += " AND (t.description LIKE ? OR sa.name LIKE ? OR da.name LIKE ? OR t.notes LIKE ?)";
      for (int i = 0; i < 4; i++) args.add("%" + q + "%");
    }
    if (p.getCategoryId() != null) {
      long category = p.getCategoryId();
      if (category == 0) where += " AND t.category_id IS NULL";
      else {
        where += " AND t.category_id=?";
        args.add(category);
      }
    }
    if (p.getType() != null) {
      where += " AND t.type=?";
      args.add(p.getType().getValue());
    }
    if (p.getEffect() != null) {
      where += " AND t.effect=?";
      args.add(p.getEffect().getValue());
    }
    if (Boolean.TRUE.equals(p.getOperatingOnly()))
      where += " AND t.effect='OPERATING' AND t.type IN ('WITHDRAWAL','DEPOSIT')";
    if (!text(p.getFrom()).isBlank()) {
      where += " AND t.occurred_at>=?";
      args.add(dateTime(p.getFrom(), LocalDateTime.MIN, false));
    }
    if (!text(p.getTo()).isBlank()) {
      where += " AND t.occurred_at<=?";
      args.add(dateTime(p.getTo(), LocalDateTime.now(financeClock), true));
    }
    long total = count("SELECT COUNT(*)" + TX_FROM + where, args);
    int page = page(p.getPage()), size = pageSize(p.getPageSize());
    args.add(size);
    args.add(page * size);
    var rows =
        db.query(
            TX_SELECT + TX_FROM + where + " ORDER BY t.occurred_at DESC,t.id DESC LIMIT ? OFFSET ?",
            TRANSACTION,
            args.toArray());
    if (account != 0)
      for (var row : rows) {
        BigDecimal balance =
            db.queryForObject(
                "SELECT COALESCE(SUM(p.amount),0) FROM finance_posting p JOIN finance_transaction t"
                    + " ON t.id=p.transaction_id WHERE p.account_id=? AND p.deleted=FALSE AND"
                    + " t.deleted=FALSE AND (t.occurred_at<? OR (t.occurred_at=? AND t.id<=?))",
                BigDecimal.class,
                account,
                dateTime(row.getOccurredAt(), null, false),
                dateTime(row.getOccurredAt(), null, false),
                row.getId());
        row.setRunningBalance(money(balance));
      }
    return new GenFinanceTransactionPage().items(rows).total(total).page(page).pageSize(size);
  }

  public BigDecimal balance(long account, LocalDateTime date) {
    return db.queryForObject(
        "SELECT COALESCE(SUM(p.amount),0) FROM finance_posting p JOIN finance_transaction t ON"
            + " t.id=p.transaction_id WHERE p.account_id=? AND p.deleted=FALSE AND t.deleted=FALSE"
            + " AND t.occurred_at<=?",
        BigDecimal.class,
        account,
        date);
  }
}
