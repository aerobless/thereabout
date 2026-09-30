package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.finance.data.FinanceReadRepository;
import com.sixtymeters.thereabout.finance.domain.FinanceRules;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Configuration
public class FinanceMcpConfiguration {
  @Bean
  public HttpServletStreamableServerTransportProvider financeTransport() {
    return HttpServletStreamableServerTransportProvider.builder()
        .jsonMapper(McpJsonDefaults.getMapper())
        .mcpEndpoint("/mcp/finances")
        .contextExtractor(request -> {
          var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
          return io.modelcontextprotocol.common.McpTransportContext.create(auth == null ? Map.of() : Map.of("thereabout.actor", auth));
        })
        .build();
  }

  @Bean
  public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> financeMcpServlet(
      HttpServletStreamableServerTransportProvider transport) {
    var bean = new ServletRegistrationBean<>(transport, "/mcp/finances");
    bean.setAsyncSupported(true);
    return bean;
  }

  @Bean(destroyMethod = "close")
  public McpSyncServer financeMcpServer(
      HttpServletStreamableServerTransportProvider transport,
      com.sixtymeters.thereabout.access.UserContext users,
      FinanceReadRepository reads,
      AccountService accounts,
      CategoryService categories,
      TransactionService transactions,
      ValuationService valuations,
      ExchangeRateService rates,
      ReportService reports,
      ObjectMapper json,
      Validator validator)
      throws IOException {
    JsonNode schemas = new FinanceOpenApiSchemas(json).load();
    var tools = new ToolFactory(json, validator, schemas);
    var builder =
        McpServer.sync(transport)
            .serverInfo("thereabout-finances", "2.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build());
    builder.tools(
        tools.tool("overview", true, GenFinancePeriodQuery.class, input -> reports.overview(users.integration(), input)),
        tools.tool("accounts.list", true, GenFinanceAccountQuery.class, input -> reads.accounts(users.integration(), input)),
        tools.tool("accounts.save", false, GenFinanceAccountInput.class, input -> accounts.save(users.integration(), input)),
        tools.tool("categories.list", true, EmptyInput.class, ignored -> reads.categories()),
        tools.tool("categories.save", false, GenFinanceCategoryInput.class, input -> categories.save(users.integration(), input)),
        tools.tool("currencies.list", true, EmptyInput.class, ignored -> reads.currencies()),
        tools.tool(
            "transactions.list", true, GenFinanceTransactionQuery.class, input -> reads.transactions(users.integration(), input)),
        tools.tool(
            "transactions.get",
            true,
            GenFinanceIdQuery.class,
            input ->
                new GenFinanceTransactionDetail()
                    .transaction(reads.transaction(users.integration(), input.getId()))
                    .history(reads.history(users.integration(), input.getId()))),
        tools.tool(
            "transactions.save", false, GenFinanceTransactionInput.class, input -> transactions.save(users.integration(), input)),
        tools.tool(
            "transactions.delete",
            false,
            GenFinanceVersionedInput.class,
            input -> transactions.setDeleted(users.integration(), input, true)),
        tools.tool(
            "transactions.restore",
            false,
            GenFinanceVersionedInput.class,
            input -> transactions.setDeleted(users.integration(), input, false)),
        tools.tool(
            "transactions.categorize",
            false,
            GenFinanceBulkCategoryInput.class,
            input -> transactions.categorize(users.integration(), input)),
        tools.tool(
            "valuations.preview", true, GenFinanceValuationPreviewInput.class, input -> valuations.preview(users.integration(), input)),
        tools.tool("valuations.save", false, GenFinanceValuationInput.class, input -> valuations.save(users.integration(), input)),
        tools.tool(
            "valuations.list",
            true,
            GenFinanceValuationQuery.class,
            input -> reads.valuations(users.integration(), input.getAccountId())),
        tools.tool("reports", true, GenFinancePeriodQuery.class, input -> reports.report(users.integration(), input)),
        tools.tool("rates.list", true, GenFinanceRateQuery.class, reads::rates),
        tools.tool("rates.save", false, GenFinanceRateInput.class, input -> rates.save(users.integration(), input)),
        tools.tool("rates.refresh", false, GenFinanceRefreshInput.class, input -> rates.refresh(users.integration(), input)));
    return builder.build();
  }

  /** Maps SDK JSON at the transport boundary only; application services receive typed inputs. */
  public record EmptyInput() {}

  private record ToolFactory(ObjectMapper json, Validator validator, JsonNode schemas) {
    @SuppressWarnings("unchecked")
    <I, R> McpServerFeatures.SyncToolSpecification tool(
        String operation, boolean read, Class<I> inputType, Function<I, R> handler) {
      String schemaName =
          inputType == EmptyInput.class ? "FinanceEmpty" : inputType.getSimpleName().substring(3);
      Map<String, Object> schema = json.convertValue(schemas.required(schemaName), Map.class);
      var tool =
          McpSchema.Tool.builder("finance_" + operation.replace('.', '_'), schema)
              .description(
                  "Finance "
                      + operation
                      + ". Monetary values are decimal strings; writes require requestKey and edits"
                      + " require version.")
              .annotations(
                  new McpSchema.ToolAnnotations(
                      operation, read, !read, true, operation.equals("rates.refresh"), false))
              .build();
      return McpServerFeatures.SyncToolSpecification.builder()
          .tool(tool)
          .callHandler(
              (exchange, request) -> {
                // SDK handlers run on worker threads; carry the verified request actor for auditing.
                var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
                var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                if (exchange.transportContext().get("thereabout.actor") instanceof org.springframework.security.core.Authentication actor) context.setAuthentication(actor);
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                try {
                  I input = json.convertValue(request.arguments(), inputType);
                  var violations = validator.validate(input);
                  FinanceRules.require(
                      violations.isEmpty(),
                      violations.stream()
                          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                          .findFirst()
                          .orElse("Invalid input"));
                  R result = handler.apply(input);
                  return McpSchema.CallToolResult.builder()
                      .structuredContent(result)
                      .addTextContent(json.writeValueAsString(result))
                      .isError(false)
                      .build();
                } catch (ResponseStatusException e) {
                  return error(e.getStatusCode() + ": " + e.getReason());
                } catch (OptimisticLockingFailureException e) {
                  return error("409: Record changed; reload before saving");
                } catch (IllegalArgumentException e) {
                  return error("400: Invalid request values");
                } catch (IllegalStateException e) {
                  return error("503: Operation unavailable; no changes were committed");
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
                }
              })
          .build();
    }

    private McpSchema.CallToolResult error(String detail) {
      return McpSchema.CallToolResult.builder().addTextContent(detail).isError(true).build();
    }
  }
}
