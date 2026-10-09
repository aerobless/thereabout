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
      @org.springframework.beans.factory.annotation.Qualifier("financeTransport") HttpServletStreamableServerTransportProvider transport) {
    var bean = new ServletRegistrationBean<>(transport, "/mcp/finances");
    bean.setAsyncSupported(true);
    return bean;
  }

  @Bean(destroyMethod = "close")
  public McpSyncServer financeMcpServer(
      @org.springframework.beans.factory.annotation.Qualifier("financeTransport") HttpServletStreamableServerTransportProvider transport,
      com.sixtymeters.thereabout.access.UserContext users,
      FinanceReadRepository reads,
      AccountService accounts,
      CounterpartyService counterparties,
      McpToolCatalog catalog,
      CategoryService categories,
      TransactionService transactions,
      ValuationService valuations,
      ExchangeRateService rates,
      ReportService reports,
      FinanceImportService imports,
      FinanceImportHintService hints,
      com.sixtymeters.thereabout.finance.splitwise.SplitwiseService splitwise,
      com.sixtymeters.thereabout.finance.splitwise.SplitwiseSources splitwiseSources,
      ObjectMapper json,
      Validator validator)
      throws IOException {
    JsonNode schemas = new FinanceOpenApiSchemas(json).load();
    var tools = new ToolFactory(json, validator, schemas, catalog);
    var builder =
        McpServer.sync(transport)
            .serverInfo("thereabout-finances", "2.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build());
    builder.tools(
        tools.tool("splitwise.get", true, EmptyInput.class, ignored -> new GenSplitwiseContext().settings(splitwise.settings()).catalog(splitwise.catalog()).status(splitwise.status())),
        tools.tool("splitwise.sources.list", true, GenSplitwiseSourceQuery.class, splitwiseSources::list),
        tools.tool("splitwise.sources.get", true, GenSplitwiseSourceKey.class, splitwiseSources::get),
        tools.tool("splitwise.resolve", false, GenSplitwiseResolveInput.class, splitwise::resolve),
        tools.tool("splitwise.categories.save", false, GenSplitwiseCategorySaveInput.class, splitwise::saveCategories),
        tools.tool("splitwise.sync", false, EmptyInput.class, ignored -> splitwise.sync()),
        tools.tool("import_hints.list", true, GenFinanceImportHintQuery.class, input -> hints.list(users.integration(), input.getAccountId())),
        tools.tool("import_hints.add", false, GenFinanceImportHintInput.class, input -> hints.add(users.integration(), input)),
        tools.tool("import_hints.remove", false, GenFinanceImportHintRemoveInput.class, input -> hints.remove(users.integration(), input)),
        tools.tool("imports.prepare", false, GenFinanceImportPrepareInput.class, input -> imports.prepare(users.integration(), input)),
        tools.tool("imports.get", true, GenFinanceImportQuery.class, input -> imports.get(users.integration(), input)),
        tools.tool("imports.review", false, GenFinanceImportReviewInput.class, input -> imports.review(users.integration(), input)),
        tools.tool("imports.cancel", false, GenFinanceImportQuery.class, input -> imports.cancel(users.integration(), input)),
        tools.tool("imports.approve", false, GenFinanceImportApproveInput.class, input -> imports.approve(users.integration(), input)),
        tools.tool("overview", true, GenFinancePeriodQuery.class, input -> reports.overview(users.integration(), input)),
        tools.tool("counterparties.list", true, GenFinanceCounterpartyQuery.class, input -> counterparties.list(users.integration(), input)),
        tools.tool("counterparties.get", true, GenFinanceCounterpartyId.class, input -> counterparties.get(users.integration(), input.getId())),
        tools.tool("counterparties.save", false, GenFinanceCounterpartySaveInput.class, input -> counterparties.save(users.integration(), input.getId(), input.getInput())),
        tools.tool("counterparties.merge_preview", true, GenFinanceCounterpartyMergePreviewInput.class, input -> counterparties.preview(users.integration(), input)),
        tools.tool("counterparties.merge", false, GenFinanceCounterpartyMergeInput.class, input -> counterparties.merge(users.integration(), input)),
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
        tools.tool("valuations.delete", false, GenFinanceVersionedInput.class, input -> valuations.setDeleted(users.integration(), input, true)),
        tools.tool("valuations.restore", false, GenFinanceVersionedInput.class, input -> valuations.setDeleted(users.integration(), input, false)),
        tools.tool(
            "valuations.list",
            true,
            GenFinanceValuationQuery.class,
            input -> reads.valuations(users.integration(), input)),
        tools.tool("reports", true, GenFinancePeriodQuery.class, input -> reports.report(users.integration(), input)),
        tools.tool("rates.list", true, GenFinanceRateQuery.class, reads::rates),
        tools.tool("rates.save", false, GenFinanceRateInput.class, input -> rates.save(users.integration(), input)),
        tools.tool("rates.refresh", false, GenFinanceRefreshInput.class, input -> rates.refresh(users.integration(), input)));
    return builder.build();
  }

  /** Maps SDK JSON at the transport boundary only; application services receive typed inputs. */
  public record EmptyInput() {}

  private record ToolFactory(ObjectMapper json, Validator validator, JsonNode schemas, McpToolCatalog catalog) {
    @SuppressWarnings("unchecked")
    <I, R> McpServerFeatures.SyncToolSpecification tool(
        String operation, boolean read, Class<I> inputType, Function<I, R> handler) {
      String schemaName =
          inputType == EmptyInput.class ? "FinanceEmpty" : inputType.getSimpleName().substring(3);
      Map<String, Object> schema = json.convertValue(schemas.required(schemaName), Map.class);
      var tool =
          McpSchema.Tool.builder("finance_" + operation.replace('.', '_'), schema)
              .description(
                  operation.startsWith("splitwise.") ? splitwiseDescription(operation) : operation.startsWith("import_hints.") ? "Manage saved account guidance for future imports. Any user's main account can be selected; changes never reinterpret existing drafts. Writes require requestKey; removal requires id and version."
                      : operation.startsWith("imports.") ? importDescription(operation) : description(operation))
              .annotations(
                  new McpSchema.ToolAnnotations(
                      operation, read, !read && !operation.startsWith("imports."), !operation.equals("imports.prepare"), operation.equals("rates.refresh") || operation.equals("imports.prepare") || operation.equals("splitwise.sync"), false))
              .build();
      catalog.register(tool, read);
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

    private String description(String operation) {
      return switch (operation) {
        case "counterparties.list" -> "Search canonical counterparties and aliases, with pagination and direction/status filters.";
        case "counterparties.get" -> "Read canonical name, website, aliases, associated ledger accounts and current version. Follows combined IDs.";
        case "counterparties.save" -> "Update an existing counterparty through the same service as REST. Supply id and input containing name, the complete aliases list, current version and requestKey; websiteUrl is normalized to an HTTPS origin, and omission or an empty value clears it. For website-only edits, first call counterparties.get and preserve its name and aliases. Rejects stale versions and combined IDs; idempotent retries. Preserves ledger accounts and financial history.";
        case "counterparties.merge_preview" -> "Preview combining 2–100 counterparties: surviving identity, aliases, directions/currencies, affected transaction count and all current versions. Read-only; preserves financial history.";
        case "counterparties.merge" -> "Atomically combine the reviewed counterparties. Supply ids, targetId, name, websiteUrl, versions for every selected identity and requestKey. Rejects stale versions and own accounts; idempotent retries. Preserves postings, balances, descriptions, source IDs and Splitwise state.";
        case "accounts.list" -> "Search ledger accounts by owner, name, direction and status; returns exact balances and versions.";
        case "accounts.save" -> "Create or update an account. Requires requestKey and version for edits; preserves posted currency and counterparty direction.";
        case "currencies.list" -> "List supported currencies, precision and enabled status.";
        case "categories.list" -> "List available transaction categories and their current versions.";
        case "categories.save" -> "Create or rename a category. Requires requestKey and version for edits.";
        case "transactions.list" -> "Search visible transactions with server pagination, categories and AND/OR calendar-day rules.";
        case "transactions.get" -> "Read a transaction's exact amounts, accounts, provenance and change history.";
        case "transactions.save" -> "Create or edit a transaction and both ledger movements atomically; requires requestKey and version for edits.";
        case "transactions.delete" -> "Reversibly delete a transaction and its postings; requires id, version and requestKey.";
        case "transactions.restore" -> "Restore a deleted transaction and its postings; requires id, version and requestKey.";
        case "transactions.categorize" -> "Assign a category to selected transactions with expected versions and requestKey.";
        case "valuations.preview" -> "Preview a reported account valuation or amendment. Supply id/version to exclude its old correction. Later valuations remain unchanged.";
        case "valuations.save" -> "Record or amend a valuation and its linked correction atomically. Amendments require id/version; all writes require expectedBalance from a fresh preview and requestKey. Zero differences remove the active correction; later valuations are not rebased.";
        case "valuations.delete", "valuations.restore" -> "Reversibly delete or restore a reported valuation and its correction atomically. Requires valuation id, version and requestKey. Later valuations remain unchanged.";
        case "valuations.list" -> "List reported account valuations and linked corrections.";
        case "overview" -> "Read account balances, net worth and completeness warnings for the selected period.";
        case "reports" -> "Read income, expenses, categories and investment reporting for a selected period.";
        case "rates.list" -> "List stored dated exchange rates, sources and current versions.";
        case "rates.save" -> "Save a manual dated exchange rate; requires current version and requestKey.";
        case "rates.refresh" -> "Fetch ECB reference rates without changing booked transaction amounts; requires requestKey.";
        default -> throw new IllegalArgumentException("Missing MCP tool description: " + operation);
      };
    }

    private String splitwiseDescription(String operation) {
      return switch (operation) {
        case "splitwise.get" -> "Read safe Splitwise settings, category/account mappings, cached catalog and current sync status. No secrets or remote requests.";
        case "splitwise.sources.list" -> "Search persisted Splitwise sources, including pending, imported, ignored, deleted and locally managed history. Filter expenseId, memberId, state, Zurich date range or description; paginated, sorted by expense/member ID. Monetary values are exact decimal strings.";
        case "splitwise.sources.get" -> "Inspect one Splitwise expenseId/memberId with original paid/owed shares, timestamps, classification, local link and current source/transaction versions. Read transactions through existing finance tools.";
        case "splitwise.resolve" -> "Record one review decision: AS_EXPENSE, AS_SETTLEMENT, CREATE, LINK a matching bank booking, SKIP permanently, or LINK_EXISTING to an already prepared virtual-account transaction. Requires requestKey and sourceVersion; links also require transactionVersion. LINK_EXISTING changes provenance only and permanently preserves local content and deletion state. No automatic sync; resolve several entries, then call splitwise.sync. Rejects writes during a running sync.";
        case "splitwise.categories.save" -> "Replace the complete Splitwise category mapping only. Requires requestKey and current settings revision. Missing source IDs are unmapped; null/omitted categoryId explicitly means Uncategorized. Does not recategorize existing transactions or start sync.";
        default -> "Queue Splitwise synchronization and return immediately. Repeated requests coalesce with the existing worker. Poll splitwise.get for completion and review cases. May create/update managed financial transactions; never writes to Splitwise. Initial import must already be confirmed.";
      };
    }

    private String importDescription(String operation) {
      return switch (operation) {
        case "imports.prepare" -> "Prepare CSV suggestions in memory using OpenAI; consumes API credits, creates no ledger records. Supply accountId, fileName and csvText. Poll imports.get. Any user's main account can be selected.";
        case "imports.get" -> "Get progress and paginated proposed transactions with immutable source rows and issues. Requests renew active drafts; abandoned drafts expire after 60 minutes without activity. Drafts are lost on restart.";
        case "imports.review" -> "Correct any proposed rows using rowId and current revision. Source evidence cannot be changed. All rows must be resolved or explicitly skipped before approval.";
        case "imports.cancel" -> "Cancel and discard an import draft without ledger changes.";
        default -> "Approve a READY draft with current revision and unique requestKey. Optional corrected rows are reviewed atomically. Creates transactions, proposed counterparties and provenance in one database transaction. Categories must already exist; imports cannot create categories. Explicit duplicateOverride is required to keep flagged duplicates. Idempotent receipt survives draft expiry.";
      };
    }

    private McpSchema.CallToolResult error(String detail) {
      return McpSchema.CallToolResult.builder().addTextContent(detail).isError(true).build();
    }
  }
}
