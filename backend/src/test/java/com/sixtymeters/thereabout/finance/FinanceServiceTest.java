package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;

import com.sixtymeters.thereabout.config.ThereaboutException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Import(FinanceTestDriver.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "thereabout.finances.enabled=true",
      "thereabout.finances.mcp-key=finance-integration-test-key-with-32-characters",
      "thereabout.calendar.worker-enabled=false",
      "thereabout.launcher.fetch-icons=false"
    })
@Transactional
class FinanceServiceTest {
  @Autowired FinanceTestDriver service;
  @Autowired JdbcTemplate db;
  @Autowired jakarta.persistence.EntityManager entityManager;

  @BeforeEach
  void setup() {
    for (String t :
        List.of(
            "request",
            "audit",
            "valuation",
            "posting",
            "transaction",
            "category",
            "account",
            "currency",
            "rate",
            "archive")) db.update("DELETE FROM finance_" + t);
    db.update(
        "INSERT INTO finance_currency(code,name,symbol,decimal_places)"
            + " VALUES('CHF','Franc','CHF',2),('EUR','Euro','€',2)");
    db.update(
        "INSERT INTO finance_account(id,name,kind,currency,include_net_worth)"
            + " VALUES(1,'Cash','CASH','CHF',1),(2,'Depot','INVESTMENT','CHF',1),(3,'Shop','EXPENSE','CHF',0),(4,'Employer','REVENUE','CHF',0),(5,'Euro"
            + " cash','CASH','EUR',1)");
    db.update("INSERT INTO finance_category(id,name) VALUES(1,'Food'),(2,'Other')");
  }

  private Map<String, Object> args(Object... pairs) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i].toString(), pairs[i + 1]);
    return m;
  }

  private Map<String, Object> write(String op, Map<String, Object> p) {
    p.putIfAbsent("requestKey", UUID.randomUUID().toString());
    return service.execute(op, p);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Object o) {
    return (List<Map<String, Object>>) o;
  }

  private Map<String, Object> tx(String type, long from, long to, String amount, String date) {
    return args(
        "type",
        type,
        "sourceId",
        from,
        "destinationId",
        to,
        "sourceAmount",
        amount,
        "destinationAmount",
        amount,
        "sourceCurrency",
        "CHF",
        "destinationCurrency",
        "CHF",
        "date",
        date,
        "description",
        "Fixture",
        "categoryId",
        1);
  }

  private BigDecimal balance(long id) {
    var result = service.execute("accounts.list", args("scope", "ALL", "id", id));
    return new BigDecimal(rows(result.get("items")).getFirst().get("balance").toString());
  }

  @Test
  void balancesTransfersAndOperatingReports() {
    write("transactions.save", tx("DEPOSIT", 4, 1, "100.10", "2026-01-01T12:00"));
    write("transactions.save", tx("TRANSFER", 1, 2, "40.00", "2026-01-02T12:00"));
    write("transactions.save", tx("WITHDRAWAL", 1, 3, "10.03", "2026-01-03T12:00"));
    assertThat(balance(1)).isEqualByComparingTo("50.07");
    assertThat(balance(2)).isEqualByComparingTo("40");
    var report = service.execute("reports", args("from", "2026-01-01", "to", "2026-12-31"));
    assertThat(new BigDecimal(report.get("income").toString())).isEqualByComparingTo("100.10");
    assertThat(new BigDecimal(report.get("expenses").toString())).isEqualByComparingTo("10.03");
  }

  @Test
  void exactDecimalsForeignTransferAndMissingRates() {
    var p = tx("TRANSFER", 1, 5, "9.41", "2026-01-03T12:00");
    p.put("destinationCurrency", "EUR");
    p.put("destinationAmount", "10.00");
    write("transactions.save", p);
    assertThat(balance(1)).isEqualByComparingTo("-9.41");
    assertThat(balance(5)).isEqualByComparingTo("10");
    assertThat(
            service
                .execute("overview", args("from", "2026-01-01", "to", "2026-01-31"))
                .get("complete"))
        .isEqualTo(false);
    write(
        "rates.save",
        args("fromCurrency", "EUR", "toCurrency", "CHF", "date", "2026-01-01", "rate", "0.94"));
    assertThat(
            service
                .execute("overview", args("from", "2026-01-01", "to", "2026-01-31"))
                .get("complete"))
        .isEqualTo(true);
  }

  @Test
  void idempotencyAndStaleVersions() {
    var input = tx("DEPOSIT", 4, 1, "100", "2026-01-01T12:00");
    input.put("requestKey", "same");
    var first = write("transactions.save", input);
    assertThat(write("transactions.save", input)).isEqualTo(first);
    assertThat(balance(1)).isEqualByComparingTo("100");
    input.put("sourceAmount", "200");
    assertThatThrownBy(() -> write("transactions.save", input))
        .isInstanceOf(ThereaboutException.class)
        .hasMessageContaining("409");
    var transaction = map(first.get("transaction"));
    var del = args("id", transaction.get("id"), "version", 1);
    assertThatThrownBy(() -> write("transactions.delete", del))
        .isInstanceOf(ThereaboutException.class)
        .hasMessageContaining("409");
  }

  @Test
  void filteredPaginationKeepsFullRunningBalanceAndStableOrder() {
    write("transactions.save", tx("DEPOSIT", 4, 1, "100", "2026-01-01T12:00"));
    write("transactions.save", tx("WITHDRAWAL", 1, 3, "10", "2026-01-02T12:00"));
    write("transactions.save", tx("WITHDRAWAL", 1, 3, "20", "2026-01-02T12:00"));
    var p = args("accountId", 1, "type", "WITHDRAWAL", "pageSize", 1);
    var first = rows(service.execute("transactions.list", p).get("items")).getFirst();
    assertThat(new BigDecimal(first.get("running_balance").toString())).isEqualByComparingTo("70");
    p.put("page", 1);
    var second = rows(service.execute("transactions.list", p).get("items")).getFirst();
    assertThat(new BigDecimal(second.get("running_balance").toString())).isEqualByComparingTo("90");
    assertThat(first.get("id")).isNotEqualTo(second.get("id"));
  }

  @Test
  void editsDeletesRestoresAndCategorizesWithoutChangingMoney() {
    var input = tx("WITHDRAWAL", 1, 3, "10", "2026-01-02T12:00");
    var first = map(write("transactions.save", input).get("transaction"));
    long id = ((Number) first.get("id")).longValue();
    input.remove("requestKey");
    input.put("id", id);
    input.put("version", 0);
    input.put("date", "2025-12-31T12:00");
    write("transactions.save", input);
    write(
        "transactions.categorize",
        args("items", List.of(args("id", id, "version", 1)), "categoryId", 2));
    assertThat(balance(1)).isEqualByComparingTo("-10");
    write("transactions.delete", args("id", id, "version", 2));
    assertThat(balance(1)).isEqualByComparingTo("0");
    write("transactions.restore", args("id", id, "version", 3));
    assertThat(balance(1)).isEqualByComparingTo("-10");
    assertThat(rows(service.execute("transactions.get", args("id", id)).get("history"))).hasSize(5);
  }

  @Test
  void valuationsAreLinkedAndSeparateFromCashflow() {
    write("transactions.save", tx("TRANSFER", 1, 2, "100", "2026-01-01T12:00"));
    var p = args("accountId", 2, "date", "2026-01-31T18:00", "reportedValue", "120");
    var preview = service.execute("valuations.preview", p);
    assertThat(new BigDecimal(preview.get("difference").toString())).isEqualByComparingTo("20");
    p.put("reference", "statement-1");
    p.put("expectedBalance", preview.get("previousBalance"));
    write("valuations.save", p);
    assertThat(balance(2)).isEqualByComparingTo("120");
    var report = service.execute("reports", args("from", "2026-01-01", "to", "2026-01-31"));
    assertThat(new BigDecimal(report.get("income").toString())).isEqualByComparingTo("0");
    var investment = rows(report.get("investments")).getFirst();
    assertThat(new BigDecimal(investment.get("valuationChange").toString()))
        .isEqualByComparingTo("20");
    assertThat(rows(service.execute("valuations.list", Map.of()).get("items"))).hasSize(1);
  }

  @Test
  void zeroValuationCreatesNoPostingAndRejectsStalePreview() {
    var p =
        args(
            "accountId",
            2,
            "date",
            "2026-01-31T18:00",
            "reportedValue",
            "0",
            "expectedBalance",
            "0",
            "reference",
            "zero");
    write("valuations.save", p);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction", Integer.class))
        .isZero();
    write("transactions.save", tx("TRANSFER", 1, 2, "10", "2026-01-01T12:00"));
    p.put("reference", "later");
    p.put("reportedValue", "50");
    p.remove("requestKey");
    assertThatThrownBy(() -> write("valuations.save", p))
        .isInstanceOf(ThereaboutException.class)
        .hasMessageContaining("409");
  }

  @Test
  void validatesBalanceDirectionAndCurrencyPrecision() {
    var p = tx("TRANSFER", 1, 2, "1.00", "2026-01-01T12:00");
    p.put("destinationAmount", "2.00");
    assertThatThrownBy(() -> write("transactions.save", p)).isInstanceOf(ThereaboutException.class);
    var wrong = tx("WITHDRAWAL", 4, 3, "1.00", "2026-01-01T12:00");
    assertThatThrownBy(() -> write("transactions.save", wrong))
        .isInstanceOf(ThereaboutException.class);
    var precision = tx("WITHDRAWAL", 1, 3, "1.001", "2026-01-01T12:00");
    assertThatThrownBy(() -> write("transactions.save", precision))
        .isInstanceOf(ThereaboutException.class);
  }

  @Test
  void importedSubcentAmountsSurviveMetadataEdits() {
    var input = tx("WITHDRAWAL", 1, 3, "10", "2026-01-02T12:00");
    var first = map(write("transactions.save", input).get("transaction"));
    long id = ((Number) first.get("id")).longValue();
    db.update(
        "UPDATE finance_posting SET amount=-10.000000000001 WHERE transaction_id=? AND"
            + " side='SOURCE'",
        id);
    input.remove("requestKey");
    input.put("id", id);
    input.put("version", 0);
    input.put("sourceAmount", "10.000000000001");
    input.put("description", "Metadata edit");
    write("transactions.save", input);
    assertThat(balance(1)).isEqualByComparingTo("-10.000000000001");
  }

  @Test
  void restoresImportedDeletedPostingFlags() {
    var first =
        map(
            write("transactions.save", tx("WITHDRAWAL", 1, 3, "7", "2026-01-02T12:00"))
                .get("transaction"));
    long id = ((Number) first.get("id")).longValue();
    db.update("UPDATE finance_transaction SET deleted=TRUE WHERE id=?", id);
    db.update("UPDATE finance_posting SET deleted=TRUE WHERE transaction_id=?", id);
    entityManager.clear();
    write("transactions.restore", args("id", id, "version", 0));
    assertThat(balance(1)).isEqualByComparingTo("-7");
  }

  @Test
  void reportDrillDownExcludesValuationsAndTransfers() {
    write("transactions.save", tx("WITHDRAWAL", 1, 3, "7", "2026-01-02T12:00"));
    write("transactions.save", tx("TRANSFER", 1, 2, "4", "2026-01-02T12:00"));
    var gain = tx("DEPOSIT", 4, 2, "2", "2026-01-02T12:00");
    gain.put("effect", "VALUATION");
    write("transactions.save", gain);
    assertThat(rows(service.execute("transactions.list", args("operatingOnly", true)).get("items")))
        .hasSize(1);
  }

  @Test
  void historicalRateNeverUsesFutureQuoteAndManualWinsSameDate() {
    db.update(
        "INSERT INTO finance_rate(from_currency,to_currency,rate_date,rate,source)"
            + " VALUES('EUR','CHF','2026-01-01',0.9,'ECB'),('EUR','CHF','2026-01-03',1.2,'ECB')");
    write(
        "rates.save",
        args("fromCurrency", "EUR", "toCurrency", "CHF", "date", "2026-01-01", "rate", "0.95"));
    var expense = tx("WITHDRAWAL", 5, 3, "10", "2026-01-02T12:00");
    expense.put("sourceCurrency", "EUR");
    expense.put("destinationCurrency", "EUR");
    write("transactions.save", expense);
    var report = service.execute("reports", args("from", "2026-01-01", "to", "2026-01-02"));
    assertThat(new BigDecimal(report.get("expenses").toString())).isEqualByComparingTo("9.5");
  }

  @Test
  void manualRateReplacementRejectsStaleRevision() {
    var rate =
        args("fromCurrency", "EUR", "toCurrency", "CHF", "date", "2026-01-01", "rate", "0.95");
    write("rates.save", rate);
    var existing =
        rows(service
                .execute("rates.list", args("fromCurrency", "EUR", "date", "2026-01-01"))
                .get("items"))
            .getFirst();
    rate.remove("requestKey");
    rate.put("rate", "0.96");
    rate.put("version", 999);
    assertThatThrownBy(() -> write("rates.save", rate)).hasMessageContaining("409");
    rate.remove("requestKey");
    rate.put("version", existing.get("version"));
    write("rates.save", rate);
    assertThat(
            db.queryForObject(
                "SELECT rate FROM finance_rate WHERE source='MANUAL'", BigDecimal.class))
        .isEqualByComparingTo("0.96");
    rate.remove("requestKey");
    rate.put("rate", "0.97");
    assertThatThrownBy(() -> write("rates.save", rate)).hasMessageContaining("409");
  }

  @Test
  void backdatedChangesDoNotRewriteLaterValuations() {
    write(
        "valuations.save",
        args(
            "accountId",
            2,
            "date",
            "2026-01-31T18:00",
            "reportedValue",
            "100",
            "expectedBalance",
            "0",
            "reference",
            "statement"));
    write("transactions.save", tx("TRANSFER", 1, 2, "20", "2026-01-01T12:00"));
    assertThat(balance(2)).isEqualByComparingTo("120");
    assertThat(rows(service.execute("valuations.list", Map.of()).get("items"))).hasSize(1);
  }
}
