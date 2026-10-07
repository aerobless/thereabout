package com.sixtymeters.thereabout.finance;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
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
class CounterpartyTest {
  @Autowired JdbcTemplate db;
  @Autowired AccountService accounts;
  @Autowired FinanceAccountRepository accountRecords;
  @Autowired CounterpartyService counterparties;
  @Autowired FinanceReadRepository reads;
  @Autowired TransactionService transactions;
  private final UserId user=new UserId(1);
  private long own,expense,revenue,euro;
  private String marker;
  @BeforeEach void setup() {
    com.sixtymeters.thereabout.testing.TestUsers.owner(db);
    db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2),('EUR','Euro',2)");
    marker=UUID.randomUUID().toString();
    own=account("Own "+marker,GenFinanceAccountKind.CASH,"CHF");
    expense=account("Shop variant "+marker,GenFinanceAccountKind.EXPENSE,"CHF");
    revenue=account("Refund variant "+marker,GenFinanceAccountKind.REVENUE,"CHF");
    euro=account("Euro variant "+marker,GenFinanceAccountKind.EXPENSE,"EUR");
  }
  private long account(String name,GenFinanceAccountKind kind,String currency) {
    return accounts.save(user,new GenFinanceAccountInput().name(name).kind(kind).currency(currency).requestKey(UUID.randomUUID().toString())).getAccount().getId();
  }
  private long canonical(long account) { return accountRecords.findById(account).orElseThrow().getCounterpartyId(); }
  private GenFinanceCounterpartyMergePreviewInput previewInput() {
    return new GenFinanceCounterpartyMergePreviewInput().ids(List.of(canonical(expense),canonical(revenue),canonical(euro)))
        .targetId(canonical(expense)).name("Canonical shop "+marker).websiteUrl("example.com/private?ignored=1");
  }
  private GenFinanceCounterpartyMergeInput mergeInput(GenFinanceCounterpartyMergeResult preview) {
    return new GenFinanceCounterpartyMergeInput().ids(preview.getSelected().stream().map(GenFinanceCounterparty::getId).toList())
        .targetId(preview.getCounterparty().getId()).name(preview.getCounterparty().getName()).websiteUrl(preview.getCounterparty().getWebsiteUrl())
        .versions(preview.getSelected().stream().map(c->new GenFinanceCounterpartyVersion().id(c.getId()).version(c.getVersion())).toList()).requestKey(UUID.randomUUID().toString());
  }
  private GenFinanceTransactionInput transaction(String date,String description) {
    return new GenFinanceTransactionInput().requestKey(UUID.randomUUID().toString()).type(GenFinanceTransactionType.WITHDRAWAL)
        .sourceId(own).destinationId(expense).sourceCurrency("CHF").destinationCurrency("CHF").sourceAmount("12.35").destinationAmount("12.35")
        .date(date).description(description).notes("Keep original note").externalReference("original:reference:"+marker);
  }
  @Test void combinesDirectionsAndCurrenciesWithoutRewritingAnyFinancialHistory() {
    var tx=transactions.save(user,transaction("1903-02-10T12:00:00","Shop product names")).getTransaction();
    var ledger=db.queryForList("SELECT * FROM finance_posting WHERE transaction_id=? ORDER BY id",tx.getId());
    var history=db.queryForList("SELECT * FROM finance_transaction WHERE id=?",tx.getId());
    var balance=reads.balance(user,own,java.time.LocalDateTime.of(2000,1,1,0,0));
    var preview=counterparties.preview(user,previewInput());
    assertThat(preview.getAffectedTransactions()).isEqualTo(1);
    assertThat(preview.getCounterparty().getAccounts()).extracting(GenFinanceAccount::getKind).contains(GenFinanceAccountKind.EXPENSE,GenFinanceAccountKind.REVENUE);
    var input=mergeInput(preview); var merged=counterparties.merge(user,input);
    assertThat(counterparties.merge(user,input)).isEqualTo(merged);
    assertThat(merged.getCounterparty().getVersion()).isGreaterThan(preview.getCounterparty().getVersion());
    assertThat(merged.getAliases()).contains("Shop variant "+marker,"Refund variant "+marker,"Euro variant "+marker);
    assertThat(merged.getCounterparty().getWebsiteUrl()).isEqualTo("https://example.com/");
    assertThat(counterparties.get(user,preview.getMergedIds().getFirst()).getId()).isEqualTo(merged.getCounterparty().getId());
    assertThat(db.queryForList("SELECT * FROM finance_posting WHERE transaction_id=? ORDER BY id",tx.getId())).isEqualTo(ledger);
    assertThat(db.queryForList("SELECT * FROM finance_transaction WHERE id=?",tx.getId())).isEqualTo(history);
    assertThat(reads.balance(user,own,java.time.LocalDateTime.of(2000,1,1,0,0))).isEqualByComparingTo(balance);
    assertThat(reads.transaction(user,tx.getId()).getDestinationName()).isEqualTo("Canonical shop "+marker);
    assertThat(reads.transactions(user,new GenFinanceTransactionQuery().counterpartyId(merged.getCounterparty().getId())).getItems()).extracting(GenFinanceTransaction::getId).containsExactly(tx.getId());
    assertThat(counterparties.matchingAccounts("  REFUND VARIANT "+marker+"  ",AccountKind.REVENUE,"CHF")).extracting(FinanceAccountEntity::getId).containsExactly(revenue);
    assertThat(counterparties.matchingAccounts("Euro variant "+marker,AccountKind.EXPENSE,"EUR")).extracting(FinanceAccountEntity::getId).containsExactly(euro);
    assertThatThrownBy(()->counterparties.merge(user,input.name("Different payload"))).hasMessageContaining("409");
  }
  @Test void stalePreviewRejectsWholeCombination() {
    var preview=counterparties.preview(user,previewInput());var input=mergeInput(preview);var original=preview.getSelected().getFirst();
    counterparties.save(user,original.getId(),new GenFinanceCounterpartyInput().version(original.getVersion()).name(original.getName()+" changed").aliases(original.getAliases()).requestKey(UUID.randomUUID().toString()));
    assertThatThrownBy(()->counterparties.merge(user,input)).hasMessageContaining("409");
    assertThat(canonical(revenue)).isNotEqualTo(canonical(expense));
  }
  @Test void removingAliasChangesFutureMatchingWithoutChangingAccountAssociation() {
    var merged=counterparties.merge(user,mergeInput(counterparties.preview(user,previewInput()))).getCounterparty();
    counterparties.save(user,merged.getId(),new GenFinanceCounterpartyInput().version(merged.getVersion()).name(merged.getName()).aliases(List.of()).requestKey(UUID.randomUUID().toString()));
    assertThat(counterparties.matchingAccounts("Refund variant "+marker,AccountKind.REVENUE,"CHF")).isEmpty();
    assertThat(canonical(revenue)).isEqualTo(merged.getId());
  }
  @Test void dateRulesFilterCompletePopulationWithInclusiveCalendarDaysAndLegacyRanges() {
    for(String date:List.of("1903-02-09T23:59:59","1903-02-10T00:00:00","1903-02-10T23:59:59","1903-02-11T00:00:00")) transactions.save(user,transaction(date,"Date fixture"));
    var rule=new GenFinanceDateRule().mode(GenFinanceDateRule.ModeEnum.DATE_IS).date(java.time.LocalDate.of(1903,2,10));
    var filter=new GenFinanceDateFilter().operator(GenFinanceDateFilter.OperatorEnum.AND).rules(List.of(rule));
    var query=new GenFinanceTransactionQuery().accountId(own).pageSize(1).dateFilter(filter);
    assertThat(reads.transactions(user,query).getTotal()).isEqualTo(2);assertThat(reads.transactions(user,query).getItems()).hasSize(1);
    rule.setMode(GenFinanceDateRule.ModeEnum.DATE_IS_NOT);assertThat(reads.transactions(user,query).getTotal()).isEqualTo(2);
    rule.setMode(GenFinanceDateRule.ModeEnum.DATE_BEFORE);assertThat(reads.transactions(user,query).getTotal()).isEqualTo(1);
    rule.setMode(GenFinanceDateRule.ModeEnum.DATE_AFTER);assertThat(reads.transactions(user,query).getTotal()).isEqualTo(1);
    rule.setMode(GenFinanceDateRule.ModeEnum.DATE_ON_OR_AFTER);
    filter.setRules(List.of(rule,new GenFinanceDateRule().mode(GenFinanceDateRule.ModeEnum.DATE_ON_OR_BEFORE).date(java.time.LocalDate.of(1903,2,10))));
    assertThat(reads.transactions(user,query).getTotal()).isEqualTo(2);
    filter.setOperator(GenFinanceDateFilter.OperatorEnum.OR);assertThat(reads.transactions(user,query).getTotal()).isEqualTo(4);
    assertThat(reads.transactions(user,new GenFinanceTransactionQuery().accountId(own).from("1903-02-10").to("1903-02-10")).getTotal()).isEqualTo(2);
  }
}
