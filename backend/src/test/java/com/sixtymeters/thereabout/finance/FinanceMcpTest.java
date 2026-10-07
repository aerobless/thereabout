package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;
import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.finance.splitwise.*;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.finance.data.FinanceReadRepository;
import com.sixtymeters.thereabout.access.UserId;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FinanceMcpTest {
  /** Browser writes submit the XSRF cookie value in a header; any value proves same-origin script access. */
  private static final String CSRF = "finance-test-csrf";

  @LocalServerPort int port;
  @org.springframework.beans.factory.annotation.Autowired tools.jackson.databind.ObjectMapper json;
  @org.springframework.test.context.bean.override.mockito.MockitoBean SplitwiseClient splitwiseClient;
  @org.springframework.beans.factory.annotation.Autowired SplitwiseService splitwiseJobs;
  @org.springframework.beans.factory.annotation.Autowired CategoryService categoryService;
  @org.springframework.beans.factory.annotation.Autowired SplitwiseConnectionRepository splitwiseConnections;
  @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @org.junit.jupiter.api.BeforeEach void owner() { com.sixtymeters.thereabout.testing.TestUsers.owner(db); }


  @org.springframework.beans.factory.annotation.Autowired
  com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService keys;

  @Test
  void authenticatesAndUsesRealMcpClientForDiscoveryReadWriteAndErrors() {
    var transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
            .endpoint("/mcp/finances")
            .requestBuilder(
                HttpRequest.newBuilder()
                    .header(
                        "Authorization", "Bearer " + keys.getKey()))
            .build();
    try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
      assertThat(client.initialize().serverInfo().name()).isEqualTo("thereabout-finances");
      var tools = client.listTools().tools();
      assertThat(tools).hasSize(37);
      assertThat(tools)
          .extracting(McpSchema.Tool::name)
          .contains("finance_splitwise_get", "finance_splitwise_sources_list", "finance_splitwise_sources_get",
              "finance_splitwise_resolve", "finance_splitwise_categories_save", "finance_splitwise_sync",
              "finance_transactions_save", "finance_valuations_preview",
              "finance_import_hints_list", "finance_import_hints_add", "finance_import_hints_remove");
      var read =
          client.callTool(
              McpSchema.CallToolRequest.builder("finance_categories_list")
                  .arguments(Map.of())
                  .build());
      assertThat(read.isError()).isFalse();
      var args =
          Map.<String, Object>of(
              "name", "MCP integration fixture", "requestKey", UUID.randomUUID().toString());
      var first =
          client.callTool(
              McpSchema.CallToolRequest.builder("finance_categories_save").arguments(args).build());
      assertThat(first.isError()).isFalse();
      var repeated =
          client.callTool(
              McpSchema.CallToolRequest.builder("finance_categories_save").arguments(args).build());
      assertThat(repeated.structuredContent()).isEqualTo(first.structuredContent());
      var category = (Map<?, ?>) ((Map<?, ?>) first.structuredContent()).get("category");
      var conflict =
          client.callTool(
              McpSchema.CallToolRequest.builder("finance_categories_save")
                  .arguments(
                      Map.of(
                          "id",
                          category.get("id"),
                          "version",
                          99,
                          "name",
                          "Stale write",
                          "requestKey",
                          UUID.randomUUID().toString()))
                  .build());
      assertThat(conflict.isError()).isTrue();
      assertThat(conflict.content().toString()).contains("409");
      var invalid =
          client.callTool(
              McpSchema.CallToolRequest.builder("finance_categories_save")
                  .arguments(Map.of("name", "Missing key"))
                  .build());
      assertThat(invalid.isError()).isTrue();
    }
  }

  @org.springframework.beans.factory.annotation.Autowired SplitwiseLedger splitwise;
  @org.springframework.beans.factory.annotation.Autowired SplitwiseSourceRepository splitwiseSources;
  @org.springframework.beans.factory.annotation.Autowired AccountService accounts;
  @org.springframework.beans.factory.annotation.Autowired FinanceReadRepository reads;
  @Test void restAndRealMcpEditsBothEndSplitwiseManagement() throws Exception {
    db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2)");
    long own=accounts.save(new UserId(1),new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString()).name("Splitwise protocol fixture").kind(GenFinanceAccountKind.CASH).currency("CHF")).getAccount().getId();
    long bank=accounts.save(new UserId(1),new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString()).name("Bank protocol fixture").kind(GenFinanceAccountKind.CASH).currency("CHF")).getAccount().getId();
    var config=new GenSplitwiseSettings().groupId(99L).members(List.of(new GenSplitwiseMemberMapping().memberId(11L).accountId(own).bankAccountId(bank))).categories(List.of(new GenSplitwiseCategoryMapping().sourceCategoryId(10L)));
    long sourceId=88000000000001L+Math.abs(UUID.randomUUID().getLeastSignificantBits()%1000000000L);
    var source=new SplitwiseExpense(sourceId,99,"Protocol meal","","2026-09-01T12:00:00Z","CHF","2026-09-02T12:00:00Z",null,false,new SplitwiseExpense.Category(10,"Food"),List.of(new SplitwiseExpense.Share(11,"20","10")));
    splitwise.apply(source,config,false,splitwise.context());
    long id=splitwiseSources.findById(SplitwiseExpense.reference(sourceId,11)).orElseThrow().getTransactionId();
    var transaction=reads.transaction(new UserId(1),id); var json=new tools.jackson.databind.json.JsonMapper();
    var edit=new GenFinanceTransactionInput().requestKey(UUID.randomUUID().toString()).id(id).version(transaction.getVersion()).description("REST edit").date(transaction.getOccurredAt()).type(transaction.getType()).effect(transaction.getEffect()).externalReference(transaction.getExternalReference()).sourceId(transaction.getSourceAccountId()).destinationId(transaction.getDestinationAccountId()).sourceAmount(transaction.getSourceAmount()).destinationAmount(transaction.getDestinationAmount()).sourceCurrency("CHF").destinationCurrency("CHF");
    var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/finances/transactions/"+id)).header("Content-Type","application/json").header("Cookie","XSRF-TOKEN="+CSRF).header("X-XSRF-TOKEN",CSRF).PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(edit))).build(),HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200); assertThat(json.readTree(response.body()).at("/transaction/syncManaged").asBoolean()).isFalse();
    var second=new SplitwiseExpense(sourceId+1,99,"MCP meal","","2026-09-02T12:00:00Z","CHF","2026-09-02T12:00:00Z",null,false,new SplitwiseExpense.Category(10,"Food"),source.users());
    splitwise.apply(second,config,false,splitwise.context()); long secondId=splitwiseSources.findById(SplitwiseExpense.reference(sourceId+1,11)).orElseThrow().getTransactionId();
    var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp/finances").requestBuilder(HttpRequest.newBuilder().header("Authorization","Bearer "+keys.getKey())).build();
    try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
      client.initialize();
      var result=client.callTool(McpSchema.CallToolRequest.builder("finance_transactions_delete").arguments(Map.of("id",secondId,"version",reads.transaction(new UserId(1),secondId).getVersion(),"requestKey",UUID.randomUUID().toString())).build());
      assertThat(result.isError()).isFalse();
    }
    splitwise.apply(source,config,false,splitwise.context()); splitwise.apply(second,config,false,splitwise.context());
    assertThat(reads.transaction(new UserId(1),id).getDescription()).isEqualTo("REST edit"); assertThat(reads.transaction(new UserId(1),secondId).getDeleted()).isTrue();
    assertThat(reads.transaction(new UserId(1),secondId).getSyncManaged()).isFalse();
    splitwiseSources.deleteAll(splitwiseSources.findByGroupId(99L).stream().filter(v->v.getExpenseId()>=sourceId).toList());
  }

  @Test void splitwiseToolsSupportMultipleDecisionsThenOneIdempotentSync() {
    long group=System.currentTimeMillis(), paymentId=group*10, foodId=paymentId+1;
    splitwiseConnections.deleteAll(); splitwiseConnections.flush();
    db.update("INSERT INTO identity(id,first_name,is_group,role) VALUES(2,'Heidi',FALSE,'USER') ON DUPLICATE KEY UPDATE role='USER',is_group=FALSE");
    db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2)");
    var members=new ArrayList<GenSplitwiseMemberMapping>();
    for(int owner=1;owner<=2;owner++) {
      long own=accounts.save(new UserId(owner),new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString()).name("MCP virtual "+owner).kind(GenFinanceAccountKind.CASH).currency("CHF").userId((long)owner)).getAccount().getId();
      long bank=accounts.save(new UserId(owner),new GenFinanceAccountInput().requestKey(UUID.randomUUID().toString()).name("MCP bank "+owner).kind(GenFinanceAccountKind.CASH).currency("CHF").userId((long)owner)).getAccount().getId();
      members.add(new GenSplitwiseMemberMapping().memberId(owner==1?11L:22L).accountId(own).bankAccountId(bank));
    }
    long category=categoryService.save(new UserId(1),new GenFinanceCategoryInput().name("MCP groceries "+UUID.randomUUID()).requestKey(UUID.randomUUID().toString())).getCategory().getId();
    var catalog=new GenSplitwiseCatalog().groups(List.of(new GenSplitwiseGroup().id(group).name("MCP couple").members(List.of(new GenSplitwiseMember().id(11L).name("Theo"),new GenSplitwiseMember().id(22L).name("Heidi")))))
        .sourceCategories(List.of(new GenSplitwiseCategory().id(10L).name("Groceries").parentName("Food")));
    org.mockito.Mockito.when(splitwiseClient.catalog(org.mockito.ArgumentMatchers.anyString())).thenReturn(catalog);
    var shares=List.of(new SplitwiseExpense.Share(11,"0","50"),new SplitwiseExpense.Share(22,"100","50"));
    var payment=new SplitwiseExpense(paymentId,group,"Payment","","2026-09-01T12:00:00Z","CHF","2026-09-01T12:00:00Z",null,true,new SplitwiseExpense.Category(10,"Groceries"),shares);
    var food=new SplitwiseExpense(foodId,group,"Meal","","2026-09-02T12:00:00Z","CHF","2026-09-02T12:00:00Z",null,false,new SplitwiseExpense.Category(10,"Groceries"),shares);
    org.mockito.Mockito.when(splitwiseClient.expenses(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(group),org.mockito.ArgumentMatchers.nullable(java.time.Instant.class),org.mockito.ArgumentMatchers.any(java.time.Instant.class))).thenReturn(List.of(payment,food));
    splitwiseJobs.save(new GenSplitwiseSettingsInput().apiKey("mcp-fixture-secret-"+group).revision(0L).members(List.of()).categories(List.of())); splitwiseJobs.test();
    splitwiseJobs.save(new GenSplitwiseSettingsInput().revision(1L).groupId(group).members(members).categories(List.of()).enabled(false));
    splitwiseJobs.preview(); splitwiseJobs.runPending();
    splitwiseJobs.initialize(new GenSplitwiseInitializeInput().previewId(splitwiseJobs.status().getPreview().getId()).requestKey("mcp-init-"+group).correctionMembers(List.of())); splitwiseJobs.runPending();
    var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp/finances").requestBuilder(HttpRequest.newBuilder().header("Authorization","Bearer "+keys.getKey())).build();
    try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
      client.initialize();
      var context=call(client,"finance_splitwise_get",Map.of());
      assertThat(context.toString()).doesNotContain("mcp-fixture-secret-"+group);
      var page=call(client,"finance_splitwise_sources_list",Map.of("memberId",11,"pageSize",1));
      assertThat(((Number)page.get("total")).intValue()).isEqualTo(2); assertThat((List<?>)page.get("items")).hasSize(1);
      var mapping=Map.<String,Object>of("revision",splitwiseJobs.settings().getRevision(),"requestKey","mcp-mapping-"+group,"categories",List.of(Map.of("sourceCategoryId",10,"categoryId",category)));
      var first=call(client,"finance_splitwise_categories_save",mapping); assertThat(call(client,"finance_splitwise_categories_save",mapping)).isEqualTo(first);
      var nullableMapping=new HashMap<String,Object>(); nullableMapping.put("sourceCategoryId",10); nullableMapping.put("categoryId",null);
      call(client,"finance_splitwise_categories_save",Map.of("revision",splitwiseJobs.settings().getRevision(),"requestKey","mcp-null-mapping-"+group,"categories",List.of(nullableMapping)));
      assertThat(splitwiseJobs.settings().getCategories().getFirst().getCategoryId()).isNull();
      call(client,"finance_splitwise_categories_save",Map.of("revision",splitwiseJobs.settings().getRevision(),"requestKey","mcp-mapping-again-"+group,"categories",List.of(Map.of("sourceCategoryId",10,"categoryId",category))));
      var counter=call(client,"finance_accounts_save",Map.of("name","MCP meal shop "+UUID.randomUUID(),"kind","EXPENSE","currency","CHF","requestKey","mcp-shop-"+group));
      var shop=(Map<?,?>)counter.get("account");
      var prepared=call(client,"finance_transactions_save",Map.ofEntries(
          Map.entry("requestKey","mcp-prepared-"+group),Map.entry("type","WITHDRAWAL"),Map.entry("effect","OPERATING"),Map.entry("date","2026-09-03T14:00"),
          Map.entry("sourceId",members.getFirst().getAccountId()),Map.entry("destinationId",shop.get("id")),Map.entry("sourceAmount","50"),Map.entry("destinationAmount","50"),
          Map.entry("sourceCurrency","CHF"),Map.entry("destinationCurrency","CHF"),Map.entry("description","Manually aligned meal"),Map.entry("externalReference","legacy:meal")));
      var local=(Map<?,?>)prepared.get("transaction");
      var meal=(Map<?,?>)call(client,"finance_splitwise_sources_get",Map.of("expenseId",foodId,"memberId",11)).get("source");
      var linked=call(client,"finance_splitwise_resolve",Map.of("expenseId",foodId,"memberId",11,"sourceVersion",meal.get("version"),"action","LINK_EXISTING",
          "transactionId",local.get("id"),"transactionVersion",local.get("version"),"requestKey","mcp-manual-link-"+group));
      assertThat(((Map<?,?>)linked.get("transaction")).get("externalReference")).isEqualTo("legacy:meal");
      assertThat(((Map<?,?>)linked.get("source")).get("locallyManaged")).isEqualTo(true);
      for(long member:List.of(11L,22L)) {
        var detail=call(client,"finance_splitwise_sources_get",Map.of("expenseId",paymentId,"memberId",member));
        var source=(Map<?,?>)detail.get("source");
        assertThat((List<?>)((Map<?,?>)detail.get("evidence")).get("shares")).hasSize(2);
        call(client,"finance_splitwise_resolve",Map.of("expenseId",paymentId,"memberId",member,"sourceVersion",source.get("version"),"action","CREATE","requestKey","mcp-create--"+group+member));
      }
      assertThat(splitwiseJobs.status().getState()).isEqualTo("IDLE");
      var stale=client.callTool(McpSchema.CallToolRequest.builder("finance_splitwise_categories_save").arguments(Map.of("revision",0,"requestKey","mcp-stale-"+group,"categories",List.of())).build());
      assertThat(stale.isError()).isTrue(); assertThat(stale.content().toString()).contains("409");
      call(client,"finance_splitwise_sync",Map.of()); call(client,"finance_splitwise_sync",Map.of()); splitwiseJobs.runPending();
      assertThat(splitwiseJobs.status().getRows()).isEmpty();
      var imported=call(client,"finance_splitwise_sources_list",Map.of("state","ACTIVE")); assertThat(((Number)imported.get("total")).intValue()).isEqualTo(3);
      assertThat(((Number)call(client,"finance_splitwise_sources_list",Map.of("state","LOCAL")).get("total")).intValue()).isEqualTo(1);
      var ids=splitwiseSources.findByGroupId(group).stream().map(SplitwiseSource::getTransactionId).toList();
      call(client,"finance_splitwise_sync",Map.of()); splitwiseJobs.runPending();
      assertThat(splitwiseSources.findByGroupId(group).stream().map(SplitwiseSource::getTransactionId).toList()).containsExactlyElementsOf(ids);
    }
  }
  @SuppressWarnings("unchecked") private Map<String,Object> call(io.modelcontextprotocol.client.McpSyncClient client,String name,Map<String,Object> input) {
    var result=client.callTool(McpSchema.CallToolRequest.builder(name).arguments(input).build());
    assertThat(result.isError()).as(result.content().toString()).isFalse(); return (Map<String,Object>)result.structuredContent();
  }

  @Test
  void browserImpersonationCannotChangeMcpOwnerAndAuditRetainsRequestActor() {
    db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2)");
    db.update("INSERT INTO identity(id,first_name,role) VALUES(100031,'MCP target','USER') ON DUPLICATE KEY UPDATE role='USER'");
    var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp/finances")
      .requestBuilder(HttpRequest.newBuilder().header("Authorization","Bearer "+keys.getKey()).header("X-Thereabout-Impersonate-User","100031")).build();
    try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
      client.initialize();
      var result=client.callTool(McpSchema.CallToolRequest.builder("finance_accounts_save").arguments(Map.of("name","MCP owner fixture","kind","CASH","currency","CHF","requestKey",UUID.randomUUID().toString())).build());
      assertThat(result.isError()).isFalse();
      var account=(Map<?,?>)((Map<?,?>)result.structuredContent()).get("account");
      long id=((Number)account.get("id")).longValue();
      assertThat(db.<Long>queryForObject("SELECT user_id FROM finance_account WHERE id=?",Long.class,id)).isEqualTo(1);
      assertThat(db.queryForMap("SELECT actor_id,user_id FROM finance_audit WHERE entity_id=? AND operation='accounts.save' ORDER BY id DESC LIMIT 1",id))
          .containsEntry("actor_id",1L).containsEntry("user_id",1L);
    }
  }

  @Test
  void restAndMcpShareTypedResultsAndIdempotency() throws Exception {
    var json = new tools.jackson.databind.json.JsonMapper();
    var http = HttpClient.newHttpClient();
    var args = Map.of("name", "REST and MCP parity", "requestKey", UUID.randomUUID().toString());
    var response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/finances/categories"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + CSRF)
                .header("X-XSRF-TOKEN", CSRF)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(args)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    var transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
            .endpoint("/mcp/finances")
            .requestBuilder(
                HttpRequest.newBuilder()
                    .header(
                        "Authorization", "Bearer " + keys.getKey()))
            .build();
    try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
      client.initialize();
      var repeated =
          client.callTool(
              McpSchema.CallToolRequest.builder("finance_categories_save")
                  .arguments(new HashMap<>(args))
                  .build());
      assertThat(repeated.isError()).isFalse();
      assertThat((tools.jackson.databind.JsonNode) json.valueToTree(repeated.structuredContent()))
          .isEqualTo(json.readTree(response.body()));
    }
  }

  @Test
  void restRejectsNumericAmountsAndInvalidReadFilters() throws Exception {
    var http = HttpClient.newHttpClient();
    String body =
        """
        {"requestKey":"numeric-is-invalid","type":"WITHDRAWAL","description":"Precision","date":"2026-01-01",
         "sourceId":1,"destinationId":2,"sourceAmount":10.01,"destinationAmount":"10.01","sourceCurrency":"CHF","destinationCurrency":"CHF"}
        """;
    var response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/finances/transactions"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + CSRF)
                .header("X-XSRF-TOKEN", CSRF)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("INVALID_INPUT");
    var invalidFilter =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/finances/transactions?page=-1"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(invalidFilter.statusCode()).isEqualTo(400);
  }

  @Test
  void revealsPersistedKeyOnlyThroughTheUncachedFinanceEndpoint() throws Exception {
    var response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/finances/configuration/mcp-key"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(keys.getKey());
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
  }

  @Test
  void rejectsMissingWrongTokenAndForeignBrowserOrigins() throws Exception {
    var client = HttpClient.newHttpClient();
    URI mcp = URI.create("http://127.0.0.1:" + port + "/mcp/finances");
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(mcp).GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(mcp)
                        .header("Authorization", "Bearer wrong")
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
    var req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/finances/categories"))
            .header("Content-Type", "application/json")
            .header("Origin", "https://example.org")
            .POST(HttpRequest.BodyPublishers.ofString("{}"));
    assertThat(client.send(req.build(), HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(403);
  }
  @Test void counterpartyPreviewAndMergeHaveRestParityVersionChecksAndIdempotentReceipts() throws Exception {
    String marker=UUID.randomUUID().toString();
    db.update("INSERT IGNORE INTO finance_currency(code,name,decimal_places) VALUES('CHF','Franc',2)");
    long expense=accounts.save(new UserId(1),new GenFinanceAccountInput().name("MCP shop "+marker).kind(GenFinanceAccountKind.EXPENSE).currency("CHF").requestKey(UUID.randomUUID().toString())).getAccount().getId();
    long revenue=accounts.save(new UserId(1),new GenFinanceAccountInput().name("MCP refund "+marker).kind(GenFinanceAccountKind.REVENUE).currency("CHF").requestKey(UUID.randomUUID().toString())).getAccount().getId();
    long first=db.queryForObject("SELECT counterparty_id FROM finance_account WHERE id=?",Long.class,expense);
    long second=db.queryForObject("SELECT counterparty_id FROM finance_account WHERE id=?",Long.class,revenue);
    var arguments=new LinkedHashMap<String,Object>();arguments.put("ids",List.of(first,second));arguments.put("targetId",first);arguments.put("name","MCP canonical "+marker);arguments.put("websiteUrl","example.com");
    var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp/finances").requestBuilder(HttpRequest.newBuilder().header("Authorization","Bearer "+keys.getKey())).build();
    try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
      client.initialize();
      var preview=call(client,"finance_counterparties_merge_preview",arguments);
      var rest=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/finances/counterparties/merge/preview"))
          .header("Content-Type","application/json").header("Cookie","XSRF-TOKEN="+CSRF).header("X-XSRF-TOKEN",CSRF)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(arguments))).build(),HttpResponse.BodyHandlers.ofString());
      assertThat(rest.statusCode()).isEqualTo(200);assertThat(json.readTree(rest.body())).isEqualTo(json.valueToTree(preview));
      var selected=(List<?>)preview.get("selected");var versions=new ArrayList<Map<String,Object>>();
      for(Object item:selected) { var c=(Map<?,?>)item;versions.add(Map.of("id",c.get("id"),"version",c.get("version"))); }
      arguments.put("versions",versions);arguments.put("requestKey",UUID.randomUUID().toString());
      var stale=new LinkedHashMap<>(arguments);stale.put("requestKey",UUID.randomUUID().toString());
      stale.put("versions",List.of(Map.of("id",first,"version",999999),Map.of("id",second,"version",999999)));
      assertThat(client.callTool(McpSchema.CallToolRequest.builder("finance_counterparties_merge").arguments(stale).build()).isError()).isTrue();
      assertThat(db.queryForObject("SELECT counterparty_id FROM finance_account WHERE id=?",Long.class,revenue)).isEqualTo(second);
      var merged=call(client,"finance_counterparties_merge",arguments);
      assertThat(call(client,"finance_counterparties_merge",arguments)).isEqualTo(merged);
      var retry=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/finances/counterparties/merge"))
          .header("Content-Type","application/json").header("Cookie","XSRF-TOKEN="+CSRF).header("X-XSRF-TOKEN",CSRF)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(arguments))).build(),HttpResponse.BodyHandlers.ofString());
      assertThat(retry.statusCode()).isEqualTo(200);assertThat(json.readTree(retry.body())).isEqualTo(json.valueToTree(merged));
      assertThat(db.queryForObject("SELECT counterparty_id FROM finance_account WHERE id=?",Long.class,revenue)).isEqualTo(first);
      arguments.put("name","Changed payload");
      assertThat(client.callTool(McpSchema.CallToolRequest.builder("finance_counterparties_merge").arguments(arguments).build()).isError()).isTrue();
      var canonical=call(client,"finance_counterparties_get",Map.of("id",second));assertThat(((Number)canonical.get("id")).longValue()).isEqualTo(first);
      var catalog=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/finances/configuration/mcp-tools")).GET().build(),HttpResponse.BodyHandlers.ofString());
      assertThat(catalog.statusCode()).isEqualTo(200);
      var published=json.readTree(catalog.body()).at("/endpoints/0/tools");
      assertThat(published.size()).isEqualTo(client.listTools().tools().size());
      for(var tool:client.listTools().tools()) assertThat(published.toString()).contains(tool.name(),tool.description());
    }
  }
}
