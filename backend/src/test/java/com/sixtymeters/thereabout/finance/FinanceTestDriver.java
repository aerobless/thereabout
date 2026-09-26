package com.sixtymeters.thereabout.finance;

import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import tools.jackson.databind.ObjectMapper;

/** Characterization adapter for the pre-refactor assertions. Not shipped in the application. */
@TestComponent
@RequiredArgsConstructor
class FinanceTestDriver {
  private final FinanceReadRepository reads;
  private final AccountService accounts;
  private final CategoryService categories;
  private final TransactionService transactions;
  private final ValuationService valuations;
  private final ExchangeRateService rates;
  private final ReportService reports;
  private final ObjectMapper json;

  Map<String, Object> execute(String op, Map<String, Object> input) {
    Object result =
        switch (op) {
          case "accounts.list" -> {
            var p = json.convertValue(input, GenFinanceAccountQuery.class);
            yield reads.accounts(p);
          }
          case "accounts.save" -> {
            var p = json.convertValue(input, GenFinanceAccountInput.class);
            yield accounts.save(p);
          }
          case "categories.save" -> {
            var p = json.convertValue(input, GenFinanceCategoryInput.class);
            yield categories.save(p);
          }
          case "transactions.list" -> {
            var p = json.convertValue(input, GenFinanceTransactionQuery.class);
            yield reads.transactions(p);
          }
          case "transactions.save" -> {
            var p = json.convertValue(input, GenFinanceTransactionInput.class);
            yield transactions.save(p);
          }
          case "transactions.delete" -> {
            var p = json.convertValue(input, GenFinanceVersionedInput.class);
            yield transactions.setDeleted(p, true);
          }
          case "transactions.restore" -> {
            var p = json.convertValue(input, GenFinanceVersionedInput.class);
            yield transactions.setDeleted(p, false);
          }
          case "transactions.categorize" -> {
            var p = json.convertValue(input, GenFinanceBulkCategoryInput.class);
            yield transactions.categorize(p);
          }
          case "valuations.preview" -> {
            var p = json.convertValue(input, GenFinanceValuationPreviewInput.class);
            yield valuations.preview(p);
          }
          case "valuations.save" -> {
            var p = json.convertValue(input, GenFinanceValuationInput.class);
            yield valuations.save(p);
          }
          case "valuations.list" -> {
            var p = json.convertValue(input, GenFinanceValuationQuery.class);
            yield reads.valuations(p.getAccountId());
          }
          case "rates.list" -> {
            var p = json.convertValue(input, GenFinanceRateQuery.class);
            yield reads.rates(p);
          }
          case "rates.save" -> {
            var p = json.convertValue(input, GenFinanceRateInput.class);
            yield rates.save(p);
          }
          case "overview" -> {
            var p = json.convertValue(input, GenFinancePeriodQuery.class);
            yield reports.overview(p);
          }
          case "reports" -> {
            var p = json.convertValue(input, GenFinancePeriodQuery.class);
            yield reports.report(p);
          }
          case "transactions.get" ->
              new GenFinanceTransactionDetail()
                  .transaction(reads.transaction(((Number) input.get("id")).longValue()))
                  .history(reads.history(((Number) input.get("id")).longValue()));
          default -> throw new IllegalArgumentException(op);
        };
    return legacy(json.convertValue(result, Map.class));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> legacy(Map<String, Object> source) {
    Map<String, String> names =
        Map.ofEntries(
            Map.entry("sourceAccountId", "source_id_account"),
            Map.entry("destinationAccountId", "destination_id_account"),
            Map.entry("sourceAmount", "source_amount"),
            Map.entry("destinationAmount", "destination_amount"),
            Map.entry("sourceCurrency", "source_currency"),
            Map.entry("destinationCurrency", "destination_currency"),
            Map.entry("foreignAmount", "foreign_amount"),
            Map.entry("foreignCurrency", "foreign_currency"),
            Map.entry("categoryId", "category_id"),
            Map.entry("occurredAt", "occurred_at"),
            Map.entry("runningBalance", "running_balance"),
            Map.entry("reportedValue", "reported_value"),
            Map.entry("previousBalance", "previous_balance"),
            Map.entry("transactionId", "transaction_id"),
            Map.entry("accountId", "account_id"));
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach(
        (key, value) ->
            result.put(
                names.getOrDefault(key, key),
                value instanceof Map<?, ?> m
                    ? legacy((Map<String, Object>) m)
                    : value instanceof List<?> list
                        ? list.stream()
                            .map(
                                x -> x instanceof Map<?, ?> m ? legacy((Map<String, Object>) m) : x)
                            .toList()
                        : value));
    // Preview/report top-level fields were camelCase even in the original API.
    for (var e : names.entrySet())
      if (source.containsKey(e.getKey())) result.put(e.getKey(), result.get(e.getValue()));
    return result;
  }
}
