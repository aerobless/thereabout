package com.sixtymeters.thereabout.finance.splitwise;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class SplitwiseLedgerTest {
  @Autowired JdbcTemplate db;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired SplitwiseLedger ledger;
  @Autowired SplitwiseService jobs;
  @Autowired SplitwiseSources sourceReads;
  @Autowired SplitwiseSourceRepository sources;
  @Autowired SplitwiseConnectionRepository connections;
  @Autowired FinanceReadRepository reads;
  @Autowired TransactionService transactions;
  @Autowired AccountService accounts;
  @Autowired CounterpartyService counterparties;
  @Autowired FinanceAccountRepository accountRepository;
  @Autowired ReportService reports;
  @MockitoBean SplitwiseClient client;
  GenSplitwiseSettings settings;
  @BeforeEach void setup() {
    com.sixtymeters.thereabout.testing.TestUsers.owner(db);
    db.update("INSERT INTO identity(id,first_name,is_group,role) VALUES(2,'Heidi',FALSE,'USER') ON DUPLICATE KEY UPDATE role='USER',is_group=FALSE");
    db.update("DELETE FROM splitwise_source"); db.update("DELETE FROM splitwise_connection");
    db.update("DELETE FROM configuration WHERE config_key='SPLITWISE_API_KEY'");
    for (var table : List.of("import_hint","import_source","request","audit","valuation","posting","transaction","category","account","currency","rate","archive")) db.update("DELETE FROM finance_" + table);
    db.update("INSERT INTO finance_currency(code,name,symbol,decimal_places) VALUES('CHF','Franc','CHF',2),('EUR','Euro','EUR',2)");
    db.update("INSERT INTO finance_account(id,name,kind,currency,user_id) VALUES(1,'Theo virtual','CASH','CHF',1),(2,'Heidi virtual','CASH','CHF',2),(3,'Theo bank','CASH','CHF',1),(4,'Heidi bank','CASH','CHF',2),(5,'Shop','EXPENSE','CHF',NULL),(6,'Pay','REVENUE','CHF',NULL),(7,'Euro','CASH','EUR',1)");
    db.update("INSERT INTO finance_category(id,name) VALUES(1,'Food'),(2,'Other')");
    settings = new GenSplitwiseSettings().groupId(99L).members(new ArrayList<>(List.of(member(11,1,3,null),member(22,2,4,null)))).categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(10L).categoryId(1L)));
  }
  GenSplitwiseMemberMapping member(long external,long own,long bank,String start) { return new GenSplitwiseMemberMapping().memberId(external).accountId(own).bankAccountId(bank).startDate(start); }
  SplitwiseExpense expense(long id,String delta,String date,boolean deleted,boolean payment) {
    var a = new BigDecimal(delta); var cost = a.abs().multiply(BigDecimal.TWO);
    return new SplitwiseExpense(id,99,"Groceries " + id,"",date,"CHF",Instant.now().toString(),deleted?Instant.now().toString():null,payment,
        new SplitwiseExpense.Category(10,"Groceries"),List.of(new SplitwiseExpense.Share(11,a.signum()>0?cost.toPlainString():"0",a.abs().toPlainString()),new SplitwiseExpense.Share(22,a.signum()<0?cost.toPlainString():"0",a.abs().toPlainString())));
  }
  SplitwiseExpense expense(long id,String delta) { return expense(id,delta,"2026-09-01T12:00:00Z",false,false); }
  void apply(SplitwiseExpense e) { ledger.apply(e,settings,false,ledger.context()); }
  GenFinanceTransaction tx(long expense,long member) { var s = sources.findById(SplitwiseExpense.reference(expense,member)).orElseThrow(); return reads.transaction(new UserId(member==11?1:2),s.getTransactionId()); }
  BigDecimal balance(long user,long account) { return reads.balance(new UserId(user),account,LocalDateTime.of(2030,1,1,0,0)); }
  GenFinanceTransactionInput edit(GenFinanceTransaction t) {
    return new GenFinanceTransactionInput().id(t.getId()).version(t.getVersion()).requestKey(UUID.randomUUID().toString()).type(t.getType()).effect(t.getEffect()).description(t.getDescription()).date(t.getOccurredAt()).categoryId(t.getCategoryId()).notes(t.getNotes()).externalReference(t.getExternalReference()).sourceId(t.getSourceAccountId()).destinationId(t.getDestinationAccountId()).sourceAmount(t.getSourceAmount()).destinationAmount(t.getDestinationAmount()).sourceCurrency(t.getSourceCurrency()).destinationCurrency(t.getDestinationCurrency());
  }
  GenFinanceVersionedInput versioned(GenFinanceTransaction t) { return new GenFinanceVersionedInput().id(t.getId()).version(t.getVersion()).requestKey(UUID.randomUUID().toString()); }
  SplitwiseExpense named(SplitwiseExpense e, String description) {
    return new SplitwiseExpense(e.id(), e.groupId(), description, e.details(), e.date(), e.currency(), e.updatedAt(), e.deletedAt(), e.payment(), e.category(), e.users());
  }
  GenFinanceAccount merchant(String name, AccountKind kind, String currency) {
    return accounts.save(new UserId(1), new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString())
        .name(name).kind(GenFinanceAccountKind.valueOf(kind.name())).currency(currency)).getAccount();
  }
  @Test void descriptionsReuseCanonicalAliasesAcrossDirectionsAndCombinedAccounts() {
    var expense = merchant("Regression merchant", AccountKind.EXPENSE, "CHF");
    var canonical = counterparties.get(new UserId(1), accountRepository.findById(expense.getId()).orElseThrow().getCounterpartyId());
    counterparties.save(new UserId(1), canonical.getId(), new GenFinanceCounterpartyInput().requestKey("merchant-alias")
        .version(canonical.getVersion()).name(canonical.getName()).aliases(List.of("Regression shop")));
    merchant("Regression shop", AccountKind.EXPENSE, "CHF");
    var e = named(expense(100,"25.1234"), "  rEgReSsIoN ShOp  "); apply(e);
    assertThat(tx(100,22).getDestinationAccountId()).isEqualTo(expense.getId());
    assertThat(tx(100,11).getSourceCounterpartyId()).isEqualTo(canonical.getId());
    assertThat(tx(100,22).getDestinationCounterpartyId()).isEqualTo(canonical.getId());
    assertThat(tx(100,11).getEffect()).isEqualTo(GenFinanceEffect.EXPENSE_REIMBURSEMENT);
    assertThat(balance(1,1)).isEqualByComparingTo("25.1234");
    assertThat(balance(2,2)).isEqualByComparingTo("-25.1234");
    assertThat(tx(100,11).getSyncManaged()).isTrue();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_account WHERE name LIKE 'Splitwise %'", Integer.class)).isZero();
  }
  @Test void merchantChangesUpdateManagedTransactionsWithoutDuplicatesOrBalanceChanges() {
    apply(named(expense(100,"12.3456"),"Regression first shop"));
    var first = tx(100,11); var count = db.queryForObject("SELECT COUNT(*) FROM finance_account", Integer.class);
    apply(named(expense(100,"12.3456"),"Regression first shop"));
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_account", Integer.class)).isEqualTo(count);
    var changed = named(expense(100,"12.3456"),"Regression next shop"); apply(changed);
    var after = tx(100,11);
    assertThat(after.getId()).isEqualTo(first.getId());
    assertThat(after.getSourceCounterpartyId()).isNotEqualTo(first.getSourceCounterpartyId());
    assertThat(after.getSourceName()).isEqualTo(changed.description());
    assertThat(after.getExternalReference()).isEqualTo(first.getExternalReference());
    assertThat(after.getSyncManaged()).isTrue();
    assertThat(balance(1,1)).isEqualByComparingTo("12.3456");
    assertThat(balance(2,2)).isEqualByComparingTo("-12.3456");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction", Integer.class)).isEqualTo(2);
  }
  @Test void ambiguousAliasesRequireReviewWithoutCreatingOrMisassigningAccounts() {
    var expense = merchant("Regression ambiguous expense", AccountKind.EXPENSE, "CHF");
    var other = merchant("Regression ambiguous other expense", AccountKind.EXPENSE, "CHF");
    for (var account : List.of(expense,other)) {
      var c = counterparties.get(new UserId(1), accountRepository.findById(account.getId()).orElseThrow().getCounterpartyId());
      counterparties.save(new UserId(1),c.getId(),new GenFinanceCounterpartyInput().requestKey(UUID.randomUUID().toString())
          .version(c.getVersion()).name(c.getName()).aliases(List.of("Regression ambiguous alias")));
    }
    apply(named(expense(100,"25"),"Regression ambiguous alias"));
    assertThat(sources.findAll()).allSatisfy(source -> {
      assertThat(source.getState()).isEqualTo("PENDING");
      assertThat(source.getMessage()).contains("Several existing counterparties match");
      assertThat(source.getTransactionId()).isNull();
    });
    assertThat(balance(1,1)).isZero(); assertThat(balance(2,2)).isZero();
  }
  @Test void missingDescriptionsGetAnExpenseSpecificNameInsteadOfTheCatchAll() {
    apply(named(expense(100,"25")," "));
    assertThat(tx(100,11).getSourceName()).isEqualTo("Splitwise expense 100");
    assertThat(tx(100,22).getDestinationName()).isEqualTo("Splitwise expense 100");
  }
  @Test void merchantIdentityIsSharedButItsLedgerAccountsKeepTheirCurrency() {
    var chf = merchant("Regression currency shop", AccountKind.REVENUE, "CHF");
    var bank = merchant("Regression euro bank", AccountKind.CASH, "EUR");
    settings.setMembers(List.of(member(11,7,bank.getId(),null)));
    var e = named(expense(100,"15.1234"),"Regression currency shop");
    apply(new SplitwiseExpense(e.id(),e.groupId(),e.description(),e.details(),e.date(),"EUR",e.updatedAt(),e.deletedAt(),e.payment(),e.category(),e.users()));
    var after = tx(100,11);
    assertThat(after.getSourceAccountId()).isNotEqualTo(chf.getId());
    assertThat(after.getSourceCounterpartyId()).isEqualTo(accountRepository.findById(chf.getId()).orElseThrow().getCounterpartyId());
    assertThat(after.getSourceCurrency()).isEqualTo("EUR");
    assertThat(after.getDestinationCurrency()).isEqualTo("EUR");
    assertThat(balance(1,7)).isEqualByComparingTo("15.1234");
  }
  @Test void pairedSharesReduceExpensesAndPayerChangesWithoutDuplicating() {
    apply(expense(100,"25.1234"));
    assertThat(balance(1,1)).isEqualByComparingTo("25.1234"); assertThat(balance(2,2)).isEqualByComparingTo("-25.1234");
    assertThat(tx(100,11).getEffect()).isEqualTo(GenFinanceEffect.EXPENSE_REIMBURSEMENT);
    var report = reports.report(new UserId(1),new GenFinancePeriodQuery().from("2026-09-01").to("2026-09-30"));
    assertThat(report.getIncome()).isEqualTo("0"); assertThat(new BigDecimal(report.getExpenses())).isEqualByComparingTo("-25.1234");
    assertThat(new BigDecimal(report.getCategories().getFirst().getExpenses())).isEqualByComparingTo("-25.1234");
    var id = tx(100,11).getId(); apply(expense(100,"25.1234")); apply(expense(100,"-9.41"));
    assertThat(tx(100,11).getId()).isEqualTo(id); assertThat(tx(100,11).getType()).isEqualTo(GenFinanceTransactionType.WITHDRAWAL);
    assertThat(balance(1,1)).isEqualByComparingTo("-9.41"); assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isEqualTo(2);
  }
  @Test void zeroDeletionAndRestorationRetainSourceIdentity() {
    apply(expense(100,"25")); var id = tx(100,11).getId();
    apply(expense(100,"0")); assertThat(tx(100,11).getDeleted()).isTrue();
    apply(expense(100,"10")); assertThat(tx(100,11).getDeleted()).isFalse(); assertThat(tx(100,11).getId()).isEqualTo(id);
    apply(expense(100,"10","2026-09-01T12:00:00Z",true,false)); assertThat(tx(100,11).getDeleted()).isTrue();
    apply(expense(100,"15")); assertThat(balance(1,1)).isEqualByComparingTo("15"); assertThat(sources.count()).isEqualTo(2);
  }
  @Test void localEditAndBulkCategorizationPermanentlyDetachOnlyThatPerson() {
    apply(expense(100,"25"));
    var appliedHash = sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getAppliedHash();
    var result = transactions.save(new UserId(1),edit(tx(100,11)).description("My edit"));
    assertThat(result.getTransaction().getSyncManaged()).isFalse();
    apply(expense(100,"30"));
    assertThat(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getAppliedHash()).isEqualTo(appliedHash);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_audit WHERE operation='splitwise.source-observed'",Integer.class)).isEqualTo(1);
    assertThat(tx(100,11).getDescription()).isEqualTo("My edit"); assertThat(balance(1,1)).isEqualByComparingTo("25"); assertThat(balance(2,2)).isEqualByComparingTo("-30");
    var heidi = tx(100,22);
    transactions.categorize(new UserId(2), new GenFinanceBulkCategoryInput().requestKey(UUID.randomUUID().toString()).categoryId(2L).items(List.of(new GenFinanceSelection().id(heidi.getId()).version(heidi.getVersion()))));
    apply(expense(100,"40")); assertThat(tx(100,22).getCategoryId()).isEqualTo(2L); assertThat(balance(2,2)).isEqualByComparingTo("-30");
  }
  @Test void localDeletionAndRestorationNeverResumeAutomaticManagementAndNoOpSaveDoesNotDetach() {
    apply(expense(100,"25")); transactions.save(new UserId(1),edit(tx(100,11))); apply(expense(100,"30"));
    assertThat(balance(1,1)).isEqualByComparingTo("30"); assertThat(tx(100,11).getSyncManaged()).isTrue();
    transactions.setDeleted(new UserId(1),versioned(tx(100,11)),true); apply(expense(100,"40")); assertThat(tx(100,11).getDeleted()).isTrue();
    transactions.setDeleted(new UserId(1),versioned(tx(100,11)),false); apply(expense(100,"50")); assertThat(balance(1,1)).isEqualByComparingTo("30"); assertThat(tx(100,11).getSyncManaged()).isFalse();
  }
  @Test void differentCutoffsAdoptReferencesIncludingMetadataAndPreserveDeletedHistory() {
    var old = transactions.save(new UserId(1),new GenFinanceTransactionInput().requestKey("old").type(GenFinanceTransactionType.WITHDRAWAL).sourceId(1L).destinationId(5L).sourceAmount("10").destinationAmount("10").sourceCurrency("CHF").destinationCurrency("CHF").date("2025-01-01T13:00").description("Old"));
    db.update("UPDATE finance_transaction SET metadata=? WHERE id=?","{\"external_id\":\"splitwise:100:user:11\"}",old.getTransaction().getId());
    entityManager.clear();
    settings.getMembers().getFirst().startDate("2026-08-30");
    ledger.apply(expense(100,"-10","2025-01-01T12:00:00Z",false,false),settings,true,ledger.context());
    assertThat(tx(100,11).getId()).isEqualTo(old.getTransaction().getId()); assertThat(tx(100,11).getVersion()).isEqualTo(0);
    ledger.apply(expense(101,"12","2025-02-01T12:00:00Z",false,false),settings,true,ledger.context());
    assertThat(sources.findById(SplitwiseExpense.reference(101,11)).orElseThrow().getState()).isEqualTo("IGNORED");
    apply(expense(101,"30")); assertThat(balance(1,1)).isEqualByComparingTo("-10");
    apply(expense(102,"3","2025-03-01T12:00:00Z",false,false)); assertThat(balance(1,1)).isEqualByComparingTo("-7");
    assertThat(balance(2,2)).isEqualByComparingTo("-23");
  }
  @Test void pendingCurrencyAndCategoryDoNotStopOtherProjectionsAndFutureDatesRemain() {
    settings.getMembers().getFirst().accountId(7L); apply(expense(100,"10"));
    assertThat(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getState()).isEqualTo("PENDING"); assertThat(balance(2,2)).isEqualByComparingTo("-10");
    settings.getMembers().getFirst().accountId(1L); settings.categories(List.of()); apply(expense(101,"10"));
    assertThat(sources.findById(SplitwiseExpense.reference(101,22)).orElseThrow().getState()).isEqualTo("PENDING");
    settings.categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(10L)));
    apply(expense(101,"10","2027-07-01T12:00:00Z",false,false)); assertThat(tx(101,11).getCategoryId()).isNull(); assertThat(tx(101,11).getOccurredAt()).startsWith("2027-07-01");
  }
  @Test void settlementNeedsApprovalAndConvertsBankBookingToSingleTransfer() {
    apply(expense(100,"-50","2026-09-01T12:00:00Z",false,true)); assertThat(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getState()).isEqualTo("PENDING");
    var bank = transactions.save(new UserId(1),new GenFinanceTransactionInput().requestKey("bank").type(GenFinanceTransactionType.DEPOSIT).sourceId(6L).destinationId(3L).sourceAmount("50").destinationAmount("50").sourceCurrency("CHF").destinationCurrency("CHF").date("2026-09-01T14:00").description("Bank statement")).getTransaction();
    ledger.resolve(new GenSplitwiseResolveInput().sourceVersion(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getVersion()).expenseId(100L).memberId(11L).action(GenSplitwiseResolveInput.ActionEnum.LINK).transactionId(bank.getId()).transactionVersion(bank.getVersion()).requestKey("link"),settings);
    ledger.resolve(new GenSplitwiseResolveInput().sourceVersion(sources.findById(SplitwiseExpense.reference(100,22)).orElseThrow().getVersion()).expenseId(100L).memberId(22L).action(GenSplitwiseResolveInput.ActionEnum.CREATE).requestKey("create"),settings);
    apply(expense(100,"-50","2026-09-01T12:00:00Z",false,true)); assertThat(tx(100,11).getId()).isEqualTo(bank.getId()); assertThat(tx(100,11).getType()).isEqualTo(GenFinanceTransactionType.TRANSFER);
    assertThat(balance(1,3)).isEqualByComparingTo("50"); assertThat(balance(1,1)).isEqualByComparingTo("-50");
    assertThat(reports.report(new UserId(1),new GenFinancePeriodQuery().from("2026-09-01").to("2026-09-30")).getIncome()).isEqualTo("0");
    apply(expense(100,"-50","2026-09-01T12:00:00Z",false,true)); assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isEqualTo(2);
  }
  @Test void unequalSharesAndDeletedOrMismatchedReferencesNeverDuplicate() {
    var unequal = new SplitwiseExpense(100,99,"Shared meal","","2026-09-01T12:00:00Z","CHF","2026-09-02T12:00:00Z",null,false,new SplitwiseExpense.Category(10,"Food"), List.of(new SplitwiseExpense.Share(11,"100","30"), new SplitwiseExpense.Share(22,"0","70")));
    apply(unequal); assertThat(balance(1,1)).isEqualByComparingTo("70"); assertThat(balance(2,2)).isEqualByComparingTo("-70");
    var first=tx(100,11); transactions.setDeleted(new UserId(1),versioned(first),true);
    sources.deleteAll(); sources.flush();
    apply(unequal); assertThat(tx(100,11).getDeleted()).isTrue(); assertThat(tx(100,11).getSyncManaged()).isFalse();
    sources.deleteAll(); sources.flush();
    apply(expense(100,"80")); assertThat(sources.findById(SplitwiseExpense.reference(100,22)).orElseThrow().getState()).isEqualTo("PENDING");
    apply(expense(100,"80")); assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isEqualTo(2);
  }
  @Test void historicalSettlementMustBeClassifiedBeforeCreatingBankMovement() {
    var normal=expense(100,"-50");
    var source=new SplitwiseExpense(normal.id(),normal.groupId(),"Spliti Sync Neon",normal.details(),normal.date(),normal.currency(),normal.updatedAt(),null,false,normal.category(),normal.users());
    apply(source);
    var create=new GenSplitwiseResolveInput().sourceVersion(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getVersion()).expenseId(100L).memberId(11L).action(GenSplitwiseResolveInput.ActionEnum.CREATE).requestKey("create");
    assertThatThrownBy(()->ledger.resolve(create,settings)).hasMessageContaining("Classify");
    ledger.resolve(new GenSplitwiseResolveInput().sourceVersion(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getVersion()).expenseId(100L).memberId(11L).action(GenSplitwiseResolveInput.ActionEnum.AS_SETTLEMENT).requestKey("classify"),settings);
    apply(source); create.sourceVersion(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getVersion()); ledger.resolve(create,settings); apply(source);
    assertThat(tx(100,11).getType()).isEqualTo(GenFinanceTransactionType.TRANSFER);
  }
  @Test void adoptionObservesApprovedVersionAndSourceDateAndCategoryChangesFollowNormally() {
    var source=expense(100,"25"); apply(source); var first=tx(100,11); var context=ledger.context();
    var row=new GenSplitwiseRow().transactionId(first.getId()).transactionVersion(first.getVersion());
    transactions.save(new UserId(1),edit(first).description("Edited after approval"));
    sources.deleteAll(); sources.flush();
    ledger.apply(source,settings,true,ledger.approvedVersions(ledger.context(),List.of(row)));
    assertThat(tx(100,11).getSyncManaged()).isFalse(); assertThat(tx(100,11).getDescription()).isEqualTo("Edited after approval");
    settings.categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(12L).categoryId(2L)));
    var changed=new SplitwiseExpense(source.id(),99,"Changed",source.details(),"2026-09-05T12:00:00Z","CHF",Instant.now().toString(),null,false,new SplitwiseExpense.Category(12,"Other"),source.users());
    apply(changed); assertThat(tx(100,22).getOccurredAt()).startsWith("2026-09-05"); assertThat(tx(100,22).getCategoryId()).isEqualTo(2L);
  }
  @Test void pendingPayloadIsRetriedAfterMappingFixEvenWhenIncrementalResponseIsEmpty() {
    var fixture=expense(100,"25"); settings.categories(List.of()); configure(List.of(fixture));
    jobs.preview(); jobs.runPending(); jobs.initialize(new GenSplitwiseInitializeInput().previewId(jobs.status().getPreview().getId()).requestKey("init").correctionMembers(List.of())); jobs.runPending();
    assertThat(jobs.status().getRows()).hasSize(2);
    jobs.save(new GenSplitwiseSettingsInput().revision(2L).groupId(99L).enabled(false).members(settings.getMembers()).categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(10L).categoryId(1L))));
    when(client.expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class))).thenReturn(List.of());
    jobs.sync(); jobs.runPending(); assertThat(jobs.status().getRows()).isEmpty(); assertThat(balance(1,1)).isEqualByComparingTo("25");
  }
  @Test void overlappingWorkerInvocationDoesNotReadOrApplyAnotherBatch() throws Exception {
    var fixture=expense(100,"25"); configure(List.of(fixture));
    var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      when(client.expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class))).thenAnswer(invocation -> {pool.submit(jobs::runPending).get(1,java.util.concurrent.TimeUnit.SECONDS); return List.of(fixture);});
      jobs.preview(); jobs.runPending(); assertThat(jobs.status().getState()).isEqualTo("PREVIEW_READY");
      verify(client,times(1)).expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class));
      jobs.initialize(new GenSplitwiseInitializeInput().previewId(jobs.status().getPreview().getId()).requestKey("init").correctionMembers(List.of()));
      ledger.apply(fixture,jobs.settings(),true,ledger.context());
      var interrupted=connections.findById(1L).orElseThrow(); interrupted.setState("INITIALIZING"); connections.saveAndFlush(interrupted);
      jobs.runPending(); assertThat(jobs.status().getState()).isEqualTo("IDLE"); assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isEqualTo(2);
    } finally {pool.shutdownNow();}
  }
  @Test void credentialsCanBeRemovedEvenWhenMappedAccountsBecomeInactive() {
    configure(List.of()); db.update("UPDATE finance_account SET active=FALSE WHERE id=1");
    var saved=jobs.settings(); var result=jobs.save(new GenSplitwiseSettingsInput().revision(saved.getRevision()).groupId(saved.getGroupId()).members(saved.getMembers()).categories(saved.getCategories()).removeKey(true).enabled(false));
    assertThat(result.getConfigured()).isFalse(); assertThat(result.getTested()).isFalse(); assertThat(result.getEnabled()).isFalse(); assertThat(result.getMembers()).hasSize(2);
  }
  GenSplitwiseResolveInput choice(long expense, long member, GenSplitwiseResolveInput.ActionEnum action) {
    return new GenSplitwiseResolveInput().expenseId(expense).memberId(member).action(action)
        .sourceVersion(sources.findById(SplitwiseExpense.reference(expense,member)).orElseThrow().getVersion()).requestKey(UUID.randomUUID().toString());
  }
  GenFinanceTransaction local(long own, String amount, String key) {
    return transactions.save(new UserId(1),new GenFinanceTransactionInput().requestKey(key).type(GenFinanceTransactionType.WITHDRAWAL)
        .sourceId(own).destinationId(5L).sourceAmount(amount).destinationAmount(amount).sourceCurrency("CHF").destinationCurrency("CHF")
        .date("2025-01-01T13:00").description("Preserved bank history").externalReference("legacy:"+key)).getTransaction();
  }
  @Test void manualLinkPreservesIgnoredLegacyDatesAndReferencesAndNeverResumesSync() {
    settings.getMembers().getFirst().startDate("2026-08-30"); configure(List.of());
    var old = local(1,"25","legacy");
    var source=expense(100,"-25","2025-01-02T12:00:00Z",false,false);
    ledger.apply(source,settings,true,ledger.context());
    assertThat(sources.findById(SplitwiseExpense.reference(100,11)).orElseThrow().getState()).isEqualTo("IGNORED");
    var link=choice(100,11,GenSplitwiseResolveInput.ActionEnum.LINK_EXISTING).transactionId(old.getId()).transactionVersion(old.getVersion());
    var first=jobs.resolve(link); var repeated=jobs.resolve(link);
    assertThat(repeated).isEqualTo(first); assertThat(first.getSource().getLocallyManaged()).isTrue();
    assertThat(jobs.status().getState()).isEqualTo("IDLE");
    assertThat(tx(100,11).getOccurredAt()).isEqualTo(old.getOccurredAt()); assertThat(tx(100,11).getExternalReference()).isEqualTo("legacy:legacy");
    assertThat(tx(100,11).getVersion()).isEqualTo(old.getVersion());
    apply(expense(100,"-40")); assertThat(balance(1,1)).isEqualByComparingTo("-25"); assertThat(balance(2,2)).isEqualByComparingTo("40");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_audit WHERE operation='splitwise.resolve'",Integer.class)).isEqualTo(1);
  }
  @Test void deletedManualLinksStayDeletedAndRejectStaleOrDuplicateAndWrongPostings() {
    configure(List.of()); settings.categories(List.of()); apply(expense(100,"-25")); apply(expense(101,"-25"));
    var old=local(1,"25","deleted"); old=transactions.setDeleted(new UserId(1),versioned(old),true).getTransaction();
    var invalid=choice(100,11,GenSplitwiseResolveInput.ActionEnum.LINK_EXISTING).transactionId(old.getId()).transactionVersion(old.getVersion()+1);
    assertThatThrownBy(()->jobs.resolve(invalid)).hasMessageContaining("Record changed");
    invalid.transactionVersion(old.getVersion()).sourceVersion(99L); assertThatThrownBy(()->jobs.resolve(invalid)).hasMessageContaining("Record changed");
    var badAccount=local(2,"25","other-person");
    var wrong=choice(100,11,GenSplitwiseResolveInput.ActionEnum.LINK_EXISTING).transactionId(badAccount.getId()).transactionVersion(badAccount.getVersion());
    assertThatThrownBy(()->jobs.resolve(wrong)).hasMessageContaining("Virtual-account posting");
    var badAmount=local(1,"26","bad-amount"); wrong.transactionId(badAmount.getId()).transactionVersion(badAmount.getVersion());
    assertThatThrownBy(()->jobs.resolve(wrong)).hasMessageContaining("amount or currency");
    var linked=jobs.resolve(choice(100,11,GenSplitwiseResolveInput.ActionEnum.LINK_EXISTING).transactionId(old.getId()).transactionVersion(old.getVersion()));
    assertThat(linked.getTransaction().getDeleted()).isTrue(); apply(expense(100,"-30")); assertThat(tx(100,11).getDeleted()).isTrue();
    var duplicate=choice(101,11,GenSplitwiseResolveInput.ActionEnum.LINK_EXISTING).transactionId(old.getId()).transactionVersion(old.getVersion());
    assertThatThrownBy(()->jobs.resolve(duplicate)).hasMessageContaining("another Splitwise source");
  }
  @Test void categoriesSaveIsIndependentVersionedIdempotentAndInvalidatesPreviewOnly() {
    configure(List.of(expense(100,"25"))); jobs.preview(); jobs.runPending();
    var current=jobs.settings(); var c=connections.findById(1L).orElseThrow(); var cursor=c.getCursorAt();
    db.update("UPDATE finance_account SET active=FALSE WHERE id=1"); entityManager.clear();
    var input=new GenSplitwiseCategorySaveInput().revision(current.getRevision()).requestKey("category-save").categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(10L)));
    var saved=jobs.saveCategories(input); assertThat(jobs.saveCategories(input)).isEqualTo(saved);
    assertThat(saved.getMembers()).isEqualTo(current.getMembers()); assertThat(saved.getGroupId()).isEqualTo(current.getGroupId());
    assertThat(saved.getEnabled()).isEqualTo(current.getEnabled()); assertThat(saved.getConfigured()).isTrue();
    assertThat(saved.getCategories().getFirst().getCategoryId()).isNull(); assertThat(jobs.status().getPreview()).isNull();
    assertThat(connections.findById(1L).orElseThrow().getCursorAt()).isEqualTo(cursor);
    assertThatThrownBy(()->jobs.saveCategories(new GenSplitwiseCategorySaveInput().revision(current.getRevision()).requestKey("stale").categories(List.of()))).hasMessageContaining("Record changed");
    assertThatThrownBy(()->jobs.saveCategories(new GenSplitwiseCategorySaveInput().revision(saved.getRevision()).requestKey("invalid-category").categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(999L))))).hasMessageContaining("category once");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isZero();
  }
  @Test void sourceReadsFilterFullHistoryAndIncludeSharesVersionsAndDeletedLinks() {
    configure(List.of()); settings.getMembers().getFirst().startDate("2026-08-30");
    ledger.apply(expense(100,"-25","2025-01-01T23:30:00Z",false,false),settings,true,ledger.context());
    apply(expense(101,"10")); settings.categories(List.of()); apply(expense(102,"20"));
    var ignored=sourceReads.list(new GenSplitwiseSourceQuery().memberId(11L).state("IGNORED").from("2025-01-02").to("2025-01-02").q("grocer").pageSize(1));
    assertThat(ignored.getTotal()).isEqualTo(1); assertThat(ignored.getItems().getFirst().getAmount()).isEqualTo("-25");
    assertThat(sourceReads.list(new GenSplitwiseSourceQuery().pageSize(2).page(1)).getItems()).hasSize(2);
    var detail=sourceReads.get(new GenSplitwiseSourceKey().expenseId(101L).memberId(22L));
    assertThat(detail.getEvidence().getShares()).hasSize(2); assertThat(detail.getSource().getTransactionVersion()).isEqualTo(detail.getTransaction().getVersion());
    assertThat(sourceReads.list(new GenSplitwiseSourceQuery().expenseId(102L)).getItems()).allMatch(s->s.getState().equals("PENDING"));
    assertThatThrownBy(()->sourceReads.list(new GenSplitwiseSourceQuery().from("2026-10-02").to("2026-10-01"))).hasMessageContaining("Start date");
  }
  void configure(List<SplitwiseExpense> expenses) {
    var catalog = new GenSplitwiseCatalog().groups(List.of(new GenSplitwiseGroup().id(99L).name("Couple").members(List.of(new GenSplitwiseMember().id(11L).name("Theo"),new GenSplitwiseMember().id(22L).name("Heidi"))))).sourceCategories(List.of(new GenSplitwiseCategory().id(10L).name("Groceries").parentName("Food")));
    when(client.catalog(anyString())).thenReturn(catalog); when(client.expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class))).thenReturn(expenses);
    jobs.save(new GenSplitwiseSettingsInput().apiKey("test-key").revision(0L).members(List.of()).categories(List.of())); jobs.test();
    jobs.save(new GenSplitwiseSettingsInput().revision(1L).groupId(99L).members(settings.getMembers()).categories(settings.getCategories()).enabled(false));
  }
  @Test void firstImportIsPreviewedIdempotentAndIncrementalCursorDoesNotAdvanceOnRateLimit() {
    configure(List.of(expense(100,"25"))); jobs.preview(); assertThat(jobs.status().getState()).isEqualTo("PREVIEW_QUEUED"); jobs.runPending();
    assertThat(jobs.status().getState()).isEqualTo("PREVIEW_READY"); assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isZero();
    var input = new GenSplitwiseInitializeInput().previewId(jobs.status().getPreview().getId()).requestKey("initialize").correctionMembers(List.of()); jobs.initialize(input); jobs.runPending();
    assertThat(jobs.status().getState()).isEqualTo("IDLE"); assertThat(jobs.settings().getInitialized()).isTrue(); jobs.initialize(input); jobs.sync(); jobs.runPending();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isEqualTo(2);
    var cursor = jobs.status().getCursor(); when(client.expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class))).thenThrow(new SplitwiseClient.RemoteFailure(429,Duration.ofMinutes(20)));
    jobs.sync(); jobs.runPending(); assertThat(jobs.status().getCursor()).isEqualTo(cursor); assertThat(LocalDateTime.parse(jobs.status().getRetryAt())).isAfter(LocalDateTime.now().plusMinutes(19));
    assertThatThrownBy(jobs::sync).hasMessageContaining("retry time");
  }
  @Test void correctionIsExplicitAndIdempotentAndPreviewRejectsLocalDrift() {
    settings.getMembers().getFirst().startDate("2026-08-30"); configure(List.of(expense(100,"25","2025-01-01T12:00:00Z",false,false)));
    jobs.preview(); jobs.runPending(); var preview = jobs.status().getPreview(); assertThat(preview.getBalances().getFirst().getCorrectionDelta()).isEqualTo("25");
    var correction = preview.getBalances().getFirst(); ledger.correct(correction,settings.getMembers().getFirst(),"init"); ledger.correct(correction,settings.getMembers().getFirst(),"init");
    assertThat(balance(1,1)).isEqualByComparingTo("25"); assertThat(db.queryForObject("SELECT COUNT(*) FROM finance_transaction",Integer.class)).isEqualTo(1);
    assertThatThrownBy(() -> jobs.initialize(new GenSplitwiseInitializeInput().previewId(preview.getId()).requestKey("init").correctionMembers(List.of(11L)))).hasMessageContaining("Local transactions changed");
    assertThat(reports.report(new UserId(1),new GenFinancePeriodQuery().from("2026-01-01").to("2026-12-31")).getIncome()).isEqualTo("0");
  }
  @Test void dailyReconciliationDoesNotDeleteAnUnavailableKnownSourceAndAuthPauses() {
    configure(List.of(expense(100,"25"))); jobs.preview(); jobs.runPending(); jobs.initialize(new GenSplitwiseInitializeInput().previewId(jobs.status().getPreview().getId()).requestKey("init").correctionMembers(List.of())); jobs.runPending();
    var connection = connections.findById(1L).orElseThrow(); connection.setFullSyncAt(LocalDateTime.now().minusDays(2)); connections.saveAndFlush(connection);
    when(client.expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class))).thenReturn(List.of()); when(client.expense(anyString(),eq(100L))).thenThrow(new SplitwiseClient.RemoteFailure(404,null));
    jobs.sync(); jobs.runPending(); assertThat(balance(1,1)).isEqualByComparingTo("25"); assertThat(jobs.status().getRows()).hasSize(2);
    when(client.expenses(anyString(),eq(99L),nullable(Instant.class),any(Instant.class))).thenThrow(new SplitwiseClient.RemoteFailure(401,null)); jobs.sync(); jobs.runPending();
    assertThat(jobs.settings().getTested()).isFalse(); assertThat(jobs.status().getState()).isEqualTo("AUTH_ERROR"); assertThatThrownBy(jobs::sync).hasMessageContaining("Test the connection");
  }
}
