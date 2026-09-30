package com.sixtymeters.thereabout.finance;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.FinanceReadRepository;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class MultiUserFinanceTest {
    @Autowired JdbcTemplate db;
    @Autowired AccountService accounts;
    @Autowired TransactionService transactions;
    @Autowired FinanceReadRepository reads;
    @Autowired ReportService reports;
    private final UserId alice = new UserId(100001), bob = new UserId(100002), eve = new UserId(100003);
    private long a,b,a2,counter;
    @BeforeEach void setup() {
        for (var user : List.of(alice,bob,eve)) db.update("INSERT INTO identity(id,short_name,role) VALUES(?,?,'USER') ON DUPLICATE KEY UPDATE role='USER'",user.value(),"Finance fixture "+user.value());
        db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2),('EUR','Euro',2)");
        a=account(alice,"Alice cash",GenFinanceAccountKind.CASH,"CHF");
        a2=account(alice,"Alice savings",GenFinanceAccountKind.CASH,"CHF");
        b=account(bob,"Bob cash",GenFinanceAccountKind.CASH,"CHF");
        counter=account(alice,"Shared shop",GenFinanceAccountKind.EXPENSE,"CHF");
    }
    private long account(UserId user,String name,GenFinanceAccountKind kind,String currency) {
        return accounts.save(user,new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString()).name(name).kind(kind).currency(currency)).getAccount().getId();
    }
    private GenFinanceTransactionInput transfer(long from,long to,String amount) {
        return new GenFinanceTransactionInput().requestKey(UUID.randomUUID().toString()).type(GenFinanceTransactionType.TRANSFER).date("1901-02-01T12:00:00").description("Shared transfer").sourceId(from).destinationId(to).sourceAmount(amount).destinationAmount(amount).sourceCurrency("CHF").destinationCurrency("CHF");
    }
    private GenFinancePeriodQuery period() { return new GenFinancePeriodQuery().from("1901-02-01").to("1901-02-28"); }
    private void report(UserId user,String income,String expense) {
        var report=reports.report(user,period());
        assertThat(new BigDecimal(report.getIncome())).isEqualByComparingTo(income);
        assertThat(new BigDecimal(report.getExpenses())).isEqualByComparingTo(expense);
    }
    @Test void eitherParticipantCanCreateEditDeleteAndRestoreButNoThirdUserCanAccess() {
        var tx=transactions.save(bob,transfer(a,b,"10")).getTransaction();
        assertThat(reads.transaction(alice,tx.getId()).getType()).isEqualTo(GenFinanceTransactionType.TRANSFER);
        assertThat(reads.transaction(bob,tx.getId()).getId()).isEqualTo(tx.getId());
        assertThatThrownBy(() -> reads.transaction(eve,tx.getId())).hasMessageContaining("404");
        report(alice,"0","10");report(bob,"10","0");
        var edited=transactions.save(alice,transfer(a,b,"12").id(tx.getId()).version(tx.getVersion())).getTransaction();
        assertThatThrownBy(() -> transactions.save(bob,transfer(a,b,"13").id(tx.getId()).version(tx.getVersion()))).hasMessageContaining("409");
        assertThatThrownBy(() -> transactions.save(bob,transfer(a,a2,"12").id(tx.getId()).version(edited.getVersion()))).hasMessageContaining("404");
        var deleted=transactions.setDeleted(bob,new GenFinanceVersionedInput().requestKey(UUID.randomUUID().toString()).id(tx.getId()).version(edited.getVersion()),true).getTransaction();
        report(alice,"0","0");report(bob,"0","0");
        var restored=transactions.setDeleted(alice,new GenFinanceVersionedInput().requestKey(UUID.randomUUID().toString()).id(tx.getId()).version(deleted.getVersion()),false).getTransaction();
        report(alice,"0","12");report(bob,"12","0");
        assertThat(restored.getDeleted()).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_posting WHERE transaction_id=?",Integer.class,tx.getId())).isEqualTo(2);
        transactions.save(alice,transfer(b,a,"4"));
        report(alice,"4","12");report(bob,"12","4");
    }
    @Test void internalTransfersStayExcludedEvenWithSingleAccountFiltersAndSharedAccountsDoNotLeak() {
        transactions.save(alice,transfer(a,a2,"20"));
        assertThat(new BigDecimal(reports.report(alice,period().accountId(a)).getExpenses())).isZero();
        assertThat(new BigDecimal(reports.report(alice,period().accountId(a2)).getIncome())).isZero();
        transactions.save(alice,transfer(a,counter,"5").type(GenFinanceTransactionType.WITHDRAWAL));
        transactions.save(bob,transfer(b,counter,"7").type(GenFinanceTransactionType.WITHDRAWAL));
        var aliceCounter=reads.accounts(alice,new GenFinanceAccountQuery().scope(GenFinanceScope.COUNTERPARTY).q("Shared shop")).getItems().getFirst();
        var bobCounter=reads.accounts(bob,new GenFinanceAccountQuery().scope(GenFinanceScope.COUNTERPARTY).q("Shared shop")).getItems().getFirst();
        assertThat(new BigDecimal(aliceCounter.getBalance())).isEqualByComparingTo("5");
        assertThat(new BigDecimal(bobCounter.getBalance())).isEqualByComparingTo("7");
        assertThat(reads.transactions(bob,new GenFinanceTransactionQuery().accountId(counter)).getItems()).hasSize(1);
        assertThatThrownBy(() -> reads.account(alice,b)).hasMessageContaining("404");
        assertThatThrownBy(() -> reports.report(alice,period().accountId(b))).hasMessageContaining("404");
        assertThat(reads.transferAccounts(alice,"Bob cash",0,20,null).getItems()).singleElement().satisfies(choice -> {
            assertThat(choice.getUserId()).isEqualTo(bob.value());assertThat(choice.getCurrency()).isEqualTo("CHF");
        });
    }
    @Test void idempotencyIsScopedAndInvalidBulkChangesRollBackCompletely() {
        var input=transfer(a,b,"10");
        var first=transactions.save(alice,input).getTransaction();
        assertThat(transactions.save(alice,input).getTransaction().getId()).isEqualTo(first.getId());
        assertThat(transactions.save(bob,input).getTransaction().getId()).isNotEqualTo(first.getId());
        long third=account(eve,"Eve cash",GenFinanceAccountKind.CASH,"CHF");
        var foreign=transactions.save(eve,transfer(third,counter,"5").type(GenFinanceTransactionType.WITHDRAWAL)).getTransaction();
        assertThatThrownBy(() -> transactions.categorize(alice,new GenFinanceBulkCategoryInput().requestKey(UUID.randomUUID().toString()).items(List.of(new GenFinanceSelection().id(first.getId()).version(first.getVersion()),new GenFinanceSelection().id(foreign.getId()).version(foreign.getVersion()))))).hasMessageContaining("404");
        assertThat(reads.transaction(alice,first.getId()).getVersion()).isEqualTo(first.getVersion());
    }
    @Test void foreignCurrencyUsesOwnSideAndExistingConversionAndMissingRateWarnings() {
        long euro=account(bob,"Bob euro",GenFinanceAccountKind.CASH,"EUR");
        transactions.save(alice,transfer(a,euro,"10").destinationAmount("11").destinationCurrency("EUR"));
        report(alice,"0","10");
        var missing=reports.report(bob,period());
        assertThat(missing.getWarnings()).isNotEmpty();
        db.update("INSERT INTO finance_rate(from_currency,to_currency,rate,rate_date,source) VALUES('EUR','CHF',2,'1901-01-01','fixture')");
        report(bob,"22","0");
    }
}
