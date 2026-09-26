package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;

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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "thereabout.finances.enabled=true",
      "thereabout.calendar.worker-enabled=false",
      "thereabout.launcher.fetch-icons=false"
    })
class FinanceMcpTest {
  @LocalServerPort int port;

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
      assertThat(tools).hasSize(19);
      assertThat(tools)
          .extracting(McpSchema.Tool::name)
          .contains("finance_transactions_save", "finance_valuations_preview");
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
}
