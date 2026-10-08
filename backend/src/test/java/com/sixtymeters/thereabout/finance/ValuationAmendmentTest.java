package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;
import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ValuationAmendmentTest {
  @Autowired AccountService accounts;
  @Autowired ValuationService valuations;
  @Autowired TransactionService transactions;
  @Autowired FinanceReadRepository reads;
  @Autowired FinanceTransactionRepository transactionRecords;
  @Autowired FinanceValuationRepository valuationRecords;
  @Autowired ReportService reports;
  @Autowired JdbcTemplate db;
  private final UserId user = new UserId(1);
  private long asset, revenue;
  @BeforeEach void setup() {
    com.sixtymeters.thereabout.testing.TestUsers.owner(db);
    db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2)");
    asset = account(GenFinanceAccountKind.INVESTMENT); revenue = account(GenFinanceAccountKind.REVENUE);
    deposit("100");
  }
  private long account(GenFinanceAccountKind kind) {
    return accounts.save(user,new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString())
        .name("Valuation regression " + UUID.randomUUID()).kind(kind).currency("CHF")).getAccount().getId();
  }
  private void deposit(String amount) {
    transactions.save(user,new GenFinanceTransactionInput().requestKey(UUID.randomUUID().toString())
        .type(GenFinanceTransactionType.DEPOSIT).description("Initial capital").date("1900-01-01T12:00:00")
        .sourceId(revenue).destinationId(asset).sourceAmount(amount).destinationAmount(amount).sourceCurrency("CHF").destinationCurrency("CHF"));
  }
  private GenFinanceValuationInput input(GenFinanceValuation old, String date, String total) {
    var p = new GenFinanceValuationPreviewInput().accountId(asset).date(date).reportedValue(total);
    if (old != null) p.id(old.getId()).version(old.getVersion());
    var preview = valuations.preview(user,p);
    return new GenFinanceValuationInput().requestKey(UUID.randomUUID().toString()).id(p.getId()).version(p.getVersion())
        .accountId(asset).date(date).reportedValue(total).expectedBalance(preview.getPreviousBalance())
        .reference(old == null ? UUID.randomUUID().toString() : old.getReference());
  }
  private GenFinanceValuation create(String date, String total) { return valuations.save(user,input(null,date,total)).getValuation(); }
  private BigDecimal balance() { return reads.balance(user,asset,LocalDateTime.of(1901,1,1,0,0)); }
  private GenFinanceVersionedInput versioned(GenFinanceValuation v) { return new GenFinanceVersionedInput().id(v.getId()).version(v.getVersion()).requestKey(UUID.randomUUID().toString()); }
  @Test void amendmentsReplaceTheSameCorrectionAndKeepValuationsOutOfOperatingIncome() {
    var original = create("1900-01-02T12:00:00","120");
    var request = input(original,"1900-01-02T12:00:00","90");
    var result = valuations.save(user,request); var amended = result.getValuation();
    assertThat(valuations.save(user,request)).isEqualTo(result);
    assertThat(amended.getTransactionId()).isEqualTo(original.getTransactionId());
    assertThat(amended.getVersion()).isGreaterThan(original.getVersion());
    assertThat(amended.getPreviousBalance()).isEqualTo("100"); assertThat(balance()).isEqualByComparingTo("90");
    var transaction = reads.transaction(user,amended.getTransactionId());
    assertThat(transaction.getType()).isEqualTo(GenFinanceTransactionType.WITHDRAWAL);
    assertThat(transaction.getEffect()).isEqualTo(GenFinanceEffect.VALUATION);
    assertThat(transaction.getSourceAmount()).isEqualTo("10");
    var report = reports.report(user,new GenFinancePeriodQuery().accountId(asset).from("1900-01-01").to("1900-01-31"));
    assertThat(report.getIncome()).isEqualTo("100"); assertThat(report.getExpenses()).isEqualTo("0");
    assertThatThrownBy(() -> valuations.save(user,request.requestKey(UUID.randomUUID().toString()))).hasMessageContaining("Record changed");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_audit WHERE operation='valuations.amend' AND entity_id=?",Integer.class,asset)).isEqualTo(1);
  }
  @Test void zeroAmendmentRemovesTheMovementAndASecondAmendmentReusesItsHistory() {
    var original = create("1900-01-02T12:00:00","120");
    var zero = valuations.save(user,input(original,"1900-01-02T12:00:00","100")).getValuation();
    assertThat(zero.getDeleted()).isFalse(); assertThat(balance()).isEqualByComparingTo("100");
    assertThat(reads.transaction(user,zero.getTransactionId()).getDeleted()).isTrue();
    var deleted = valuations.setDeleted(user,versioned(zero),true);
    var restored = valuations.setDeleted(user,versioned(deleted),false);
    assertThat(restored.getDeleted()).isFalse(); assertThat(balance()).isEqualByComparingTo("100");
    assertThat(reads.transaction(user,zero.getTransactionId()).getDeleted()).isTrue();
    var newValue = valuations.save(user,input(restored,"1900-01-02T12:00:00","130")).getValuation();
    assertThat(newValue.getTransactionId()).isEqualTo(original.getTransactionId());
    assertThat(reads.transaction(user,newValue.getTransactionId()).getDeleted()).isFalse();
    assertThat(balance()).isEqualByComparingTo("130");
  }
  @Test void deletionThroughEitherEntryPointKeepsTheValuationAndPostingsTogether() {
    var v = create("1900-01-02T12:00:00","120");
    var request = versioned(v); var deleted = valuations.setDeleted(user,request,true);
    assertThat(valuations.setDeleted(user,request,true)).isEqualTo(deleted);
    assertThat(deleted.getDeleted()).isTrue(); assertThat(balance()).isEqualByComparingTo("100");
    assertThatThrownBy(() -> valuations.setDeleted(user,versioned(v),false)).hasMessageContaining("Record changed");
    var restored = valuations.setDeleted(user,versioned(deleted),false);
    assertThat(restored.getDeleted()).isFalse(); assertThat(balance()).isEqualByComparingTo("120");
    var tx = reads.transaction(user,v.getTransactionId());
    transactions.setDeleted(user,new GenFinanceVersionedInput().id(tx.getId()).version(tx.getVersion()).requestKey(UUID.randomUUID().toString()),true);
    assertThat(reads.valuation(user,v.getId()).getDeleted()).isTrue(); assertThat(balance()).isEqualByComparingTo("100");
  }
  @Test void laterValuationsAreWarnedAboutButNeverSilentlyRewritten() {
    var first = create("1900-01-02T12:00:00","120"); var later = create("1900-01-03T12:00:00","150");
    var laterTransaction = reads.transaction(user,later.getTransactionId());
    var result = valuations.save(user,input(first,"1900-01-02T12:00:00","130"));
    assertThat(result.getPreview().getLaterValuations()).isTrue();
    assertThat(reads.valuation(user,later.getId())).isEqualTo(later);
    assertThat(reads.transaction(user,later.getTransactionId())).isEqualTo(laterTransaction);
    assertThat(balance()).isEqualByComparingTo("160");
    var moved = valuations.save(user,input(result.getValuation(),"1900-01-04T12:00:00","140"));
    assertThat(moved.getPreview().getPreviousBalance()).isEqualTo("130");
    assertThat(moved.getPreview().getLaterValuations()).isTrue();
    assertThat(balance()).isEqualByComparingTo("140");
    assertThat(reads.valuation(user,later.getId())).isEqualTo(later);
  }
  @Test void staleBalancesAndDuplicateReferencesDoNotPartiallyAmendTheLedger() {
    var v = create("1900-01-02T12:00:00","120"); var request = input(v,"1900-01-02T12:00:00","125");
    deposit("5");
    assertThatThrownBy(() -> valuations.save(user,request)).hasMessageContaining("Balance changed since preview");
    assertThat(reads.valuation(user,v.getId())).isEqualTo(v); assertThat(balance()).isEqualByComparingTo("125");
    var other = create("1900-01-03T12:00:00","150");
    var duplicate = input(v,"1900-01-02T12:00:00","125").reference(other.getReference());
    assertThatThrownBy(() -> valuations.save(user,duplicate)).hasMessageContaining("already been recorded");
    assertThat(reads.valuation(user,v.getId())).isEqualTo(v);
  }
  @Test void aValuationWithoutAnyAdjustmentCanAlsoBeDeletedAndRestored() {
    var zero = create("1900-01-02T12:00:00","100"); assertThat(zero.getTransactionId()).isNull();
    var deleted = valuations.setDeleted(user,versioned(zero),true);
    assertThat(deleted.getDeleted()).isTrue();
    assertThat(valuations.setDeleted(user,versioned(deleted),false).getDeleted()).isFalse();
    assertThat(balance()).isEqualByComparingTo("100");
  }
  @Test void importedCorrectionsRetainTheirPrecisionOriginAndCompatibleCounterparty() {
    var v = create("1900-01-02T12:00:00", "120");
    var record = valuationRecords.findById(v.getId()).orElseThrow();
    record.setOrigin("FIREFLY_INFERRED"); record.setReportedValue(new BigDecimal("120.123456"));
    var tx = transactionRecords.findById(v.getTransactionId()).orElseThrow();
    tx.setDescription("Kursgewinn"); tx.setNotes("Imported statement notes");
    for (var posting : tx.getPostings()) {
      boolean source = posting.getSide() == PostingSide.SOURCE;
      if (source) posting.setAccountId(revenue);
      posting.setAmount(new BigDecimal(source ? "-20.123456" : "20.123456"));
    }
    transactionRecords.saveAndFlush(tx); valuationRecords.saveAndFlush(record);
    var original = reads.valuation(user, v.getId());
    var amended = valuations.save(user, input(original, "1900-01-02T11:00:00", original.getReportedValue())).getValuation();
    assertThat(amended.getOrigin()).isEqualTo("FIREFLY_INFERRED");
    assertThat(amended.getReportedValue()).isEqualTo("120.123456");
    assertThat(amended.getTransactionId()).isEqualTo(v.getTransactionId());
    var movement = reads.transaction(user, v.getTransactionId());
    assertThat(movement.getSourceAccountId()).isEqualTo(revenue);
    assertThat(movement.getDescription()).isEqualTo("Kursgewinn");
    assertThat(movement.getNotes()).isEqualTo("Imported statement notes");
    assertThat(movement.getExternalReference()).isEqualTo(v.getReference());
    assertThat(balance()).isEqualByComparingTo("120.123456");
  }
}
