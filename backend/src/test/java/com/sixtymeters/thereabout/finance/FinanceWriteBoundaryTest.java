package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sixtymeters.thereabout.finance.data.ReferenceRate;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "thereabout.finances.enabled=true",
      "thereabout.finances.mcp-key=finance-integration-test-key-with-32-characters",
      "thereabout.calendar.worker-enabled=false",
      "thereabout.launcher.fetch-icons=false"
    })
class FinanceWriteBoundaryTest {
  @Autowired TransactionService transactions;
  @Autowired CategoryService categories;
  @Autowired ExchangeRateService rates;
  @Autowired JdbcTemplate db;
  @MockitoBean EcbRateClient ecb;

  @BeforeEach
  void fixtures() {
    for (String table :
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
            "archive")) db.update("DELETE FROM finance_" + table);
    db.update(
        "INSERT INTO finance_currency(code,name,symbol,decimal_places)"
            + " VALUES('CHF','Franc','CHF',2),('EUR','Euro','EUR',2)");
    db.update(
        "INSERT INTO finance_account(id,name,kind,currency)"
            + " VALUES(1,'Cash','CASH','CHF'),(2,'Shop','EXPENSE','CHF')");
    db.update("INSERT INTO finance_category(id,name) VALUES(1,'Original'),(2,'Other')");
  }

  private GenFinanceTransactionInput expense(String amount) {
    return new GenFinanceTransactionInput()
        .requestKey(UUID.randomUUID().toString())
        .type(GenFinanceTransactionType.WITHDRAWAL)
        .effect(GenFinanceEffect.OPERATING)
        .date("2026-01-01T12:00:00")
        .description("Fixture")
        .sourceId(1L)
        .destinationId(2L)
        .sourceAmount(amount)
        .destinationAmount(amount)
        .sourceCurrency("CHF")
        .destinationCurrency("CHF")
        .categoryId(1L);
  }

  @Test
  void postingOnlyChangeAdvancesAggregateVersion() {
    var original = transactions.save(expense("10.00")).getTransaction();
    var updated =
        transactions
            .save(expense("12.00").id(original.getId()).version(original.getVersion()))
            .getTransaction();
    assertThat(updated.getVersion()).isGreaterThan(original.getVersion());
    assertThat(new BigDecimal(updated.getSourceAmount())).isEqualByComparingTo("12.00");
    assertThatThrownBy(
            () ->
                transactions.save(
                    expense("15.00").id(original.getId()).version(original.getVersion())))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM finance_posting WHERE transaction_id=?",
                Integer.class,
                original.getId()))
        .isEqualTo(2);
  }

  @Test
  void concurrentEditorsCannotBothOverwriteTheSameVersion() throws Exception {
    var original = transactions.save(expense("10.00")).getTransaction();
    var results =
        race(
            () -> attempt(expense("12.00").id(original.getId()).version(original.getVersion())),
            () -> attempt(expense("14.00").id(original.getId()).version(original.getVersion())));
    assertThat(results).containsExactlyInAnyOrder(200, 409);
  }

  private int attempt(GenFinanceTransactionInput input) {
    try {
      transactions.save(input);
      return 200;
    } catch (ResponseStatusException conflict) {
      return conflict.getStatusCode().value();
    }
  }

  @Test
  void concurrentRetriesCreateExactlyOneTransaction() throws Exception {
    String key = UUID.randomUUID().toString();
    var results =
        race(
            () -> transactions.save(expense("10.00").requestKey(key)).getTransaction().getId(),
            () -> transactions.save(expense("10.00").requestKey(key)).getTransaction().getId());
    assertThat(results.get(0)).isEqualTo(results.get(1));
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction", Integer.class))
        .isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_posting", Integer.class))
        .isEqualTo(2);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_audit", Integer.class)).isEqualTo(1);
  }

  private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return first.call();
              });
      var b =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return second.call();
              });
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void failedBulkWriteRollsBackEntitiesAuditAndRequestReservation() {
    var a = transactions.save(expense("10.00")).getTransaction();
    var b = transactions.save(expense("20.00")).getTransaction();
    String key = UUID.randomUUID().toString();
    var input =
        new GenFinanceBulkCategoryInput()
            .requestKey(key)
            .categoryId(2L)
            .items(
                List.of(
                    new GenFinanceSelection().id(a.getId()).version(a.getVersion()),
                    new GenFinanceSelection().id(b.getId()).version(999L)));
    assertThatThrownBy(() -> transactions.categorize(input))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(db.queryForList("SELECT category_id FROM finance_transaction", Long.class))
        .containsOnly(1L);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM finance_audit WHERE operation='transactions.categorize'",
                Integer.class))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM finance_request WHERE request_key=?", Integer.class, key))
        .isZero();
    input.getItems().get(1).setVersion(b.getVersion());
    assertThat(transactions.categorize(input).getUpdated()).isEqualTo(2);
  }

  @Test
  void rateDownloadIsOutsideTransactionAndReplayDoesNotDownloadAgain() {
    when(ecb.download())
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new EcbRateClient.Download(
                  List.of(
                      new ReferenceRate(
                          "EUR", "CHF", LocalDate.of(2026, 1, 1), new BigDecimal("0.94"))),
                  LocalDate.of(2026, 1, 1));
            });
    var input = new GenFinanceRefreshInput().requestKey(UUID.randomUUID().toString());
    var first = rates.refresh(input);
    var replay = rates.refresh(input);
    assertThat(replay).isEqualTo(first);
    verify(ecb, times(1)).download();
  }
}
