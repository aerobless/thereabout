package com.sixtymeters.thereabout.finance.data;

import static com.sixtymeters.thereabout.finance.data.FinanceRowMappers.*;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.access.UserId;
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
      String categoryName,
      boolean crossUserTransfer) {}

  public List<LedgerEntry> ledger(UserId user) {
    return db.query("""
        SELECT p.account_id,p.amount,p.currency,t.occurred_at,t.type,t.effect,t.category_id,c.name category_name,
          EXISTS(SELECT 1 FROM finance_posting other JOIN finance_account oa ON oa.id=other.account_id
            WHERE other.transaction_id=t.id AND oa.user_id IS NOT NULL AND oa.user_id<>a.user_id) cross_user_transfer
        FROM finance_posting p JOIN finance_transaction t ON t.id=p.transaction_id
        JOIN finance_account a ON a.id=p.account_id LEFT JOIN finance_category c ON c.id=t.category_id
        WHERE a.user_id=? AND p.deleted=FALSE AND t.deleted=FALSE ORDER BY t.occurred_at,t.id
        """, (r,n) -> new LedgerEntry(r.getLong("account_id"),r.getBigDecimal("amount"),r.getString("currency"),
            r.getTimestamp("occurred_at").toLocalDateTime(),TransactionType.valueOf(r.getString("type")),
            FinancialEffect.valueOf(r.getString("effect")),r.getObject("category_id",Long.class),
            r.getString("category_name"),r.getBoolean("cross_user_transfer")),user.value());
  }

  public List<GenFinanceUser> users() {
    return db.query("SELECT id,first_name FROM identity WHERE role IS NOT NULL AND is_group=FALSE ORDER BY first_name,id",
        (r,n) -> new GenFinanceUser().id(r.getLong("id")).name(r.getString("first_name")));
  }

  private static final String ACCOUNT_OWNER = "(SELECT i.first_name FROM identity i WHERE i.id=a.user_id) user_name";

  public UserId accountOwner(UserId user, long id) {
    var account = account(user, id);
    if (account.getUserId() == null) throw missing("Own account");
    return new UserId(account.getUserId());
  }

  public List<GenFinanceAccount> ownAccounts(UserId user) {
    return db.query(
        "SELECT a.*," + ACCOUNT_OWNER + ",0 balance FROM finance_account a WHERE user_id=? AND deleted=FALSE AND kind IN"
            + " ('CASH','INVESTMENT','REAL_ESTATE','OTHER_ASSET') ORDER BY kind,name,id",
        ACCOUNT, user.value());
  }

  public GenFinanceAccount account(UserId user, long id) {
    return db.query("SELECT a.*," + ACCOUNT_OWNER + ",0 balance FROM finance_account a WHERE id=?", ACCOUNT, id).stream()
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

  public List<GenFinanceAudit> history(UserId user, long id) {
    transaction(user, id);
    return db.query(
        "SELECT * FROM finance_audit WHERE entity_id=? AND operation LIKE 'transactions.%' ORDER BY"
            + " id DESC",
        AUDIT, id);
  }

  public GenFinanceValuation valuation(UserId user, long id) {
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

  public GenFinanceValuationList valuations(UserId user, Long accountId) {
    long id = accountId == null ? 0 : accountId;
    if (id != 0) user = accountOwner(user, id);
    return new GenFinanceValuationList()
        .items(
            db.query(
                "SELECT v.*,a.name,a.currency FROM finance_valuation v JOIN finance_account a ON"
                    + " a.id=v.account_id WHERE a.user_id=? AND (?=0 OR a.id=?) ORDER BY v.occurred_at DESC,v.id"
                    + " DESC LIMIT 200",
                VALUATION,
                user.value(),
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

  public String latestTransaction(UserId user) {
    return db.queryForObject(
        "SELECT MAX(t.occurred_at) FROM finance_transaction t WHERE t.deleted=FALSE AND " + OWN_TRANSACTION, String.class, user.value());
  }

  public String earliestTransaction(UserId user) {
    return db.queryForObject(
        "SELECT MIN(t.occurred_at) FROM finance_transaction t WHERE t.deleted=FALSE AND " + OWN_TRANSACTION, String.class, user.value());
  }

  private static final String OWN_TRANSACTION = "EXISTS(SELECT 1 FROM finance_posting visible JOIN finance_account owner ON owner.id=visible.account_id WHERE visible.transaction_id=t.id AND owner.user_id=?)";

  public GenFinanceTransferAccountPage transferAccounts(UserId user, String q, Integer pageNumber, Integer pageLength, Long id) {
    int page=page(pageNumber), size=pageSize(pageLength);
    var args=new ArrayList<Object>();
    String where=" WHERE a.user_id IS NOT NULL AND a.deleted=FALSE AND (a.active=TRUE OR a.id=?)";
    args.add(id == null ? 0L : id);
    if (id != null) { where+=" AND a.id=?"; args.add(id); }
    if (!text(q).isBlank()) { where+=" AND (a.name LIKE ? OR i.first_name LIKE ?)"; args.add("%"+q+"%");args.add("%"+q+"%"); }
    String from=" FROM finance_account a JOIN identity i ON i.id=a.user_id";
    long total=count("SELECT COUNT(*)"+from+where,args);
    args.add(size);args.add(page*size);
    var items=db.query("SELECT a.id,a.name,a.currency,a.user_id,i.first_name"+from+where+" ORDER BY i.first_name,a.name,a.id LIMIT ? OFFSET ?",
        (r,n)->new GenFinanceTransferAccount().id(r.getLong("id")).name(r.getString("name"))
            .currency(r.getString("currency")).userId(r.getLong("user_id")).userName(r.getString("first_name")), args.toArray());
    return new GenFinanceTransferAccountPage().items(items).total(total).page(page).pageSize(size);
  }

  private long count(String sql, List<Object> args) {
    return db.queryForObject(sql, Long.class, args.toArray());
  }

  public GenFinanceAccountPage accounts(UserId user, GenFinanceAccountQuery p) {
    String scope = p.getScope() == null ? "OWN" : p.getScope().getValue();
    String q = text(p.getQ());
    int page = page(p.getPage()), size = pageSize(p.getPageSize());
    List<Object> args = new ArrayList<>();
    String where = " WHERE 1=1";
    Long owner = p.getUserId() != null ? p.getUserId()
        : (scope.equals("OWN") && p.getId() == null ? user.value() : null);
    if (owner != null) { where += " AND a.user_id=?"; args.add(owner); }
    if (!Boolean.TRUE.equals(p.getIncludeDeleted())) where += " AND a.deleted=FALSE";
    if (p.getActive() != null) {
      where += " AND a.active=?";
      args.add(p.getActive());
    } else if (!Boolean.TRUE.equals(p.getIncludeInactive())) where += " AND a.active=TRUE";
    if (scope.equals("OWN") || scope.equals("ALL_OWN"))
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
    queryArgs.add(user.value());
    queryArgs.addAll(args);
    queryArgs.add(size);
    queryArgs.add(page * size);
    var items =
        db.query(
            "SELECT a.*," + ACCOUNT_OWNER + ",COALESCE((SELECT SUM(b.amount) FROM finance_posting b JOIN"
                + " finance_transaction t ON t.id=b.transaction_id WHERE b.account_id=a.id AND"
                + " b.deleted=FALSE AND t.deleted=FALSE AND t.occurred_at<=? AND " + "(a.user_id IS NOT NULL OR " + OWN_TRANSACTION + ")),0) balance FROM"
                + " finance_account a"
                + where
                + " ORDER BY a.kind,a.name,a.id LIMIT ? OFFSET ?",
            ACCOUNT,
            queryArgs.toArray());
    return new GenFinanceAccountPage().items(items).total(total).page(page).pageSize(size);
  }

  private static final String TX_SELECT =
      "SELECT (SELECT 'SPLITWISE' FROM splitwise_source ss WHERE ss.transaction_id=t.id LIMIT 1) sync_source, "
          + "EXISTS(SELECT 1 FROM splitwise_source ss WHERE ss.transaction_id=t.id AND ss.locally_managed=FALSE) sync_managed, t.*,s.account_id source_id_account,d.account_id destination_id_account,sa.counterparty_id source_counterparty_id,da.counterparty_id destination_counterparty_id,COALESCE(sc.name,sa.name)"
          + " source_name,COALESCE(dc.name,da.name) destination_name,sa.kind source_kind,da.kind"
          + " destination_kind,ABS(s.amount) source_amount,ABS(d.amount)"
          + " destination_amount,s.currency source_currency,d.currency"
          + " destination_currency,s.foreign_amount,s.foreign_currency,c.name category_name,(SELECT"
          + " v.id FROM finance_valuation v WHERE v.transaction_id=t.id) valuation_id ";
  private static final String TX_FROM =
      " FROM finance_transaction t JOIN finance_posting s ON s.transaction_id=t.id AND"
          + " s.side='SOURCE' JOIN finance_posting d ON d.transaction_id=t.id AND"
          + " d.side='DESTINATION' JOIN finance_account sa ON sa.id=s.account_id JOIN"
          + " finance_account da ON da.id=d.account_id LEFT JOIN finance_category c ON"
          + " c.id=t.category_id LEFT JOIN finance_counterparty sc ON sc.id=sa.counterparty_id LEFT JOIN finance_counterparty dc ON dc.id=da.counterparty_id ";

  public GenFinanceTransaction transaction(UserId user, long id) {
    return db.query(TX_SELECT + TX_FROM + " WHERE t.id=?", TRANSACTION, id).stream()
        .findFirst()
        .orElseThrow(() -> missing("Transaction"));
  }

  public GenFinanceTransactionPage transactions(UserId user, GenFinanceTransactionQuery p) {
    List<Object> args = new ArrayList<>();
    long account = p.getAccountId() == null ? 0 : p.getAccountId();
    // Main account details are shared; the unfiltered feed and counterparties retain the current user's perspective.
    boolean mainAccount = account != 0 && account(user, account).getUserId() != null;
    String where = mainAccount ? " WHERE 1=1" : " WHERE (sa.user_id=? OR da.user_id=?)";
    if (!mainAccount) { args.add(user.value()); args.add(user.value()); }
    if (!Boolean.TRUE.equals(p.getIncludeDeleted()))
      where += " AND t.deleted=FALSE AND s.deleted=FALSE AND d.deleted=FALSE";
    if (account != 0) {
      account(user, account);
      where += " AND (s.account_id=? OR d.account_id=?)";
      args.add(account);
      args.add(account);
    }
    if (p.getCounterpartyId() != null) {
      where += " AND (sa.counterparty_id=? OR da.counterparty_id=?)";
      args.add(p.getCounterpartyId()); args.add(p.getCounterpartyId());
    }
    where += FinanceDateSearch.where(p.getDateFilter(), args);
    String q = text(p.getQ());
    if (!q.isBlank()) {
      where += " AND (t.description LIKE ? OR sa.name LIKE ? OR da.name LIKE ? OR t.notes LIKE ? OR sc.name LIKE ? OR dc.name LIKE ?)";
      for (int i = 0; i < 6; i++) args.add("%" + q + "%");
    }
    var categories = p.getCategoryIds();
    if (categories == null || categories.isEmpty())
      categories = p.getCategoryId() == null ? List.of() : List.of(p.getCategoryId());
    require(categories.size() <= 100 && categories.stream().allMatch(id -> id != null && id >= 0),
        "Choose up to 100 valid categories");
    if (!categories.isEmpty()) {
      where += " AND (COALESCE(t.category_id,0) IN ("
          + String.join(",", Collections.nCopies(categories.size(), "?")) + "))";
      args.addAll(categories);
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
      where += " AND t.effect='OPERATING' AND (t.type IN ('WITHDRAWAL','DEPOSIT') OR "
          + "(t.type='TRANSFER' AND sa.user_id IS NOT NULL AND da.user_id IS NOT NULL AND sa.user_id<>da.user_id))";
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
    String order = p.getSort() == null ? "DATE_DESC" : p.getSort().getValue();
    String orderBy = switch (order) {
      case "DATE_ASC" -> "t.occurred_at ASC,t.id ASC";
      case "DESCRIPTION_ASC" -> "t.description ASC,t.id DESC";
      case "DESCRIPTION_DESC" -> "t.description DESC,t.id DESC";
      default -> "t.occurred_at DESC,t.id DESC";
    };
    var rows =
        db.query(
            TX_SELECT + TX_FROM + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
            TRANSACTION,
            args.toArray());
    if (account != 0)
      for (var row : rows) {
        BigDecimal balance =
            db.queryForObject(
                "SELECT COALESCE(SUM(p.amount),0) FROM finance_posting p JOIN finance_transaction t"
                    + " ON t.id=p.transaction_id WHERE p.account_id=? AND p.deleted=FALSE AND"
                    + " t.deleted=FALSE AND (? OR " + OWN_TRANSACTION + ") AND (t.occurred_at<? OR (t.occurred_at=? AND t.id<=?))",
                BigDecimal.class,
                account,
                mainAccount,
                user.value(),
                dateTime(row.getOccurredAt(), null, false),
                dateTime(row.getOccurredAt(), null, false),
                row.getId());
        row.setRunningBalance(money(balance));
      }
    return new GenFinanceTransactionPage().items(rows).total(total).page(page).pageSize(size);
  }

  public BigDecimal balance(UserId user, long account, LocalDateTime date) {
    boolean mainAccount = account(user, account).getUserId() != null;
    return db.queryForObject(
        "SELECT COALESCE(SUM(p.amount),0) FROM finance_posting p JOIN finance_transaction t ON"
            + " t.id=p.transaction_id WHERE p.account_id=? AND p.deleted=FALSE AND t.deleted=FALSE"
            + " AND t.occurred_at<=? AND (? OR " + OWN_TRANSACTION + ")",
        BigDecimal.class,
        account,
        date,
        mainAccount,
        user.value());
  }
}
