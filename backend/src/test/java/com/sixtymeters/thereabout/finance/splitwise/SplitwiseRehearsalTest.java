package com.sixtymeters.thereabout.finance.splitwise;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/** Opt-in rehearsal against a separately restored backup with fixtures from read-only API requests. */
@SpringBootTest(properties="spring.flyway.locations=classpath:db/migration") @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="THEREABOUT_SPLITWISE_REHEARSAL",matches="true")
class SplitwiseRehearsalTest {
  @Autowired SplitwiseService jobs;
  @Autowired SplitwiseLedger ledger;
  @Autowired SplitwiseSourceRepository sources;
  @Autowired AccountService accounts;
  @Autowired FinanceReadRepository reads;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @MockitoBean SplitwiseClient client;
  @Test void previewAndCompleteImportedPopulationMatchAndRepeatIsIdempotent() throws Exception {
    try(var connection=db.getDataSource().getConnection()) {assertThat(connection.getMetaData().getURL().split("\\?",2)[0]).matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[A-Za-z0-9_]+_test");}
    assertThat(db.queryForObject("SELECT DATABASE()",String.class)).endsWith("_test"); assertThat(sources.count()).isZero();
    var original=ledger.context();
    var snapshot=Arrays.asList(json.readValue(Files.readString(Path.of(System.getenv("THEREABOUT_SPLITWISE_SNAPSHOT"))),SplitwiseExpense[].class));
    var raw=json.readTree(Files.readString(Path.of(System.getenv("THEREABOUT_SPLITWISE_CATALOG"))));
    var catalog=new GenSplitwiseCatalog().groups(new ArrayList<>()).sourceCategories(new ArrayList<>());
    for(var group:raw.path("groups")) {
      if(group.path("id").asLong()==0) continue;
      var g=new GenSplitwiseGroup().id(group.path("id").asLong()).name(group.path("name").asString()).members(new ArrayList<>());
      for(var member:group.path("members")) g.addMembersItem(new GenSplitwiseMember().id(member.path("id").asLong()).name(member.path("first_name").asString()));
      catalog.addGroupsItem(g);
    }
    for(var parent:raw.path("categories")) for(var leaf:parent.path("subcategories")) catalog.addSourceCategoriesItem(new GenSplitwiseCategory().id(leaf.path("id").asLong()).name(leaf.path("name").asString()).parentName(parent.path("name").asString()));
    when(client.catalog(anyString())).thenReturn(catalog);
    when(client.expenses(anyString(),eq(53533798L),nullable(Instant.class),any(Instant.class))).thenAnswer(invocation -> {
      if(Boolean.TRUE.equals(jobs.settings().getInitialized())) try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        assertThat(pool.submit(jobs::sync).get(3,java.util.concurrent.TimeUnit.SECONDS).getState()).isEqualTo("SYNCING");
        pool.submit(jobs::runPending).get(3,java.util.concurrent.TimeUnit.SECONDS);
      }
      return snapshot;
    });
    long heidiVirtual=accounts.save(new UserId(2),new GenFinanceAccountInput().requestKey("rehearsal-heidi-virtual").name("Splitwise rehearsal Heidi").kind(GenFinanceAccountKind.CASH).currency("CHF")).getAccount().getId();
    long heidiBank=accounts.save(new UserId(2),new GenFinanceAccountInput().requestKey("rehearsal-heidi-bank").name("Bank rehearsal Heidi").kind(GenFinanceAccountKind.CASH).currency("CHF")).getAccount().getId();
    jobs.save(new GenSplitwiseSettingsInput().apiKey("isolated-rehearsal-fixture").revision(0L).members(List.of()).categories(List.of())); jobs.test();
    var mappings=catalog.getSourceCategories().stream().map(c->{var matches=reads.categories().getItems().stream().filter(v->v.getName().equalsIgnoreCase(c.getName())).toList();return new GenSplitwiseCategoryMapping().sourceCategoryId(c.getId()).categoryId(matches.size()==1?matches.getFirst().getId():null);}).toList();
    jobs.save(new GenSplitwiseSettingsInput().revision(1L).groupId(53533798L).enabled(false).categories(mappings).members(List.of(
      new GenSplitwiseMemberMapping().memberId(64656365L).accountId(787L).bankAccountId(1L).startDate("2026-08-30"),
      new GenSplitwiseMemberMapping().memberId(12611829L).accountId(heidiVirtual).bankAccountId(heidiBank))));
    jobs.preview(); jobs.runPending(); assertThat(jobs.status().getState()).isEqualTo("PREVIEW_READY"); var preview=jobs.status().getPreview();
    int created=(int)preview.getRows().stream().filter(r->r.getAction().equals("CREATE")).count();
    int adopted=(int)preview.getRows().stream().filter(r->r.getAction().equals("ADOPT")).count();
    int pending=(int)preview.getRows().stream().filter(r->r.getAction().equals("PENDING")).count();
    jobs.initialize(new GenSplitwiseInitializeInput().previewId(preview.getId()).requestKey("rehearsal-initialize").correctionMembers(List.of())); jobs.runPending();
    assertThat(jobs.status().getState()).isEqualTo("IDLE"); assertThat(jobs.settings().getEnabled()).isFalse(); assertThat(jobs.status().getRows()).hasSize(pending);
    var after=ledger.context(); assertThat(after.existing()).hasSize(original.existing().size()+created);
    var oldById=new HashMap<Long,SplitwiseLedger.Existing>(); after.existing().forEach(t->oldById.put(t.id(),t));
    for(var t:original.existing()) assertThat(oldById.get(t.id())).as("Preserved legacy transaction %s",t.id()).isEqualTo(t);
    for(var row:preview.getRows()) {
      var source=sources.findById(SplitwiseExpense.reference(row.getExpenseId(),row.getMemberId())).orElseThrow();
      if(!Set.of("CREATE","ADOPT").contains(row.getAction())) continue;
      var t=reads.transaction(new UserId(row.getMemberId()==64656365L?1:2),source.getTransactionId());
      var amount=t.getSourceAccountId().equals(row.getAccountId())?new BigDecimal(t.getSourceAmount()).negate():new BigDecimal(t.getDestinationAmount());
      assertThat(amount).as("Source amount %s",source.getId()).isEqualByComparingTo(row.getAmount()); assertThat(t.getOccurredAt().substring(0,10)).isEqualTo(row.getDate().substring(0,10));
      if(row.getAction().equals("CREATE")) { assertThat(t.getExternalReference()).isEqualTo(source.getId()); assertThat(t.getCategoryId()).isEqualTo(mappings.stream().filter(c->c.getSourceCategoryId().equals(row.getSourceCategoryId())).findFirst().orElseThrow().getCategoryId()); }
    }
    for(var b:preview.getBalances()) { long own=b.getMemberId()==64656365L?1:2,account=own==1?787:heidiVirtual; assertThat(reads.balance(new UserId(own),account,LocalDateTime.parse(preview.getExpiresAt()).minusDays(1))).isEqualByComparingTo(b.getProjectedBalance()); }
    jobs.sync(); jobs.runPending(); assertThat(jobs.status().getState()).isEqualTo("IDLE"); assertThat(ledger.context().existing()).isEqualTo(after.existing());
    verify(client,times(2)).expenses(anyString(),eq(53533798L),nullable(Instant.class),any(Instant.class));
    var summary=new LinkedHashMap<String,Object>(); summary.put("sourceExpenses",snapshot.size()); summary.put("previewRows",preview.getRows().size()); summary.put("created",created); summary.put("adopted",adopted); summary.put("pending",pending); summary.put("preservedLegacyTransactions",original.existing().size()); summary.put("repeatedRunCreated",0); summary.put("concurrentManualSyncCoalesced",true); summary.put("balances",preview.getBalances());
    if(System.getenv("THEREABOUT_SPLITWISE_REPORT")!=null) Files.writeString(Path.of(System.getenv("THEREABOUT_SPLITWISE_REPORT")),json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    System.out.println("Splitwise rehearsal: "+snapshot.size()+" source expenses, "+created+" created, "+adopted+" adopted, "+pending+" deferred; all legacy rows preserved; repeated import creates zero transactions.");
  }
}
