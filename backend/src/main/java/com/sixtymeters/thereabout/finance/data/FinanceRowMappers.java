package com.sixtymeters.thereabout.finance.data;

import com.sixtymeters.thereabout.generated.model.*;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.money;
import java.sql.*;
import org.springframework.jdbc.core.RowMapper;

public final class FinanceRowMappers {
  private FinanceRowMappers() {}

  public static final RowMapper<GenFinanceAccount> ACCOUNT =
      (r, index) ->
          new GenFinanceAccount()
              .id(r.getObject("id", Long.class))
              .sourceId(r.getObject("source_id", Long.class))
              .name(r.getString("name"))
              .kind(GenFinanceAccountKind.fromValue(r.getString("kind")))
              .currency(r.getString("currency"))
              .active(r.getBoolean("active"))
              .deleted(r.getBoolean("deleted"))
              .includeNetWorth(r.getBoolean("include_net_worth"))
              .logoUrl(r.getString("logo_url"))
              .websiteUrl(r.getString("website_url"))
              .version(r.getObject("version", Long.class))
              .balance(money(r.getBigDecimal("balance")));
  public static final RowMapper<GenFinanceCategory> CATEGORY =
      (r, index) ->
          new GenFinanceCategory()
              .id(r.getObject("id", Long.class))
              .sourceId(r.getObject("source_id", Long.class))
              .name(r.getString("name"))
              .deleted(r.getBoolean("deleted"))
              .version(r.getObject("version", Long.class));
  public static final RowMapper<GenFinanceCurrency> CURRENCY =
      (r, index) ->
          new GenFinanceCurrency()
              .code(r.getString("code"))
              .name(r.getString("name"))
              .symbol(r.getString("symbol"))
              .decimalPlaces(r.getObject("decimal_places", Long.class))
              .enabled(r.getBoolean("enabled"));
  public static final RowMapper<GenFinanceTransaction> TRANSACTION =
      (r, index) ->
          new GenFinanceTransaction()
              .id(r.getObject("id", Long.class))
              .sourceId(r.getObject("source_id", Long.class))
              .type(GenFinanceTransactionType.fromValue(r.getString("type")))
              .effect(GenFinanceEffect.fromValue(r.getString("effect")))
              .description(r.getString("description"))
              .occurredAt(r.getString("occurred_at"))
              .categoryId(r.getObject("category_id", Long.class))
              .categoryName(r.getString("category_name"))
              .notes(r.getString("notes"))
              .externalReference(r.getString("external_reference"))
              .deleted(r.getBoolean("deleted"))
              .version(r.getObject("version", Long.class))
              .sourceAccountId(r.getObject("source_id_account", Long.class))
              .destinationAccountId(r.getObject("destination_id_account", Long.class))
              .sourceName(r.getString("source_name"))
              .destinationName(r.getString("destination_name"))
              .sourceAmount(money(r.getBigDecimal("source_amount")))
              .destinationAmount(money(r.getBigDecimal("destination_amount")))
              .sourceCurrency(r.getString("source_currency"))
              .destinationCurrency(r.getString("destination_currency"))
              .foreignAmount(money(r.getBigDecimal("foreign_amount")))
              .foreignCurrency(r.getString("foreign_currency"))
              .valuationId(r.getObject("valuation_id", Long.class));
  public static final RowMapper<GenFinanceAudit> AUDIT =
      (r, index) ->
          new GenFinanceAudit()
              .id(r.getObject("id", Long.class))
              .operation(r.getString("operation"))
              .entityId(r.getObject("entity_id", Long.class))
              .createdAt(r.getString("created_at"))
              .beforeJson(r.getString("before_json"))
              .afterJson(r.getString("after_json"));
  public static final RowMapper<GenFinanceValuation> VALUATION =
      (r, index) ->
          new GenFinanceValuation()
              .id(r.getObject("id", Long.class))
              .accountId(r.getObject("account_id", Long.class))
              .name(r.getString("name"))
              .currency(r.getString("currency"))
              .occurredAt(r.getString("occurred_at"))
              .reportedValue(money(r.getBigDecimal("reported_value")))
              .previousBalance(money(r.getBigDecimal("previous_balance")))
              .transactionId(r.getObject("transaction_id", Long.class))
              .reference(r.getString("reference"))
              .origin(r.getString("origin"));
  public static final RowMapper<GenFinanceRate> RATE =
      (r, index) ->
          new GenFinanceRate()
              .id(r.getObject("id", Long.class))
              .fromCurrency(r.getString("from_currency"))
              .toCurrency(r.getString("to_currency"))
              .rateDate(r.getString("rate_date"))
              .rate(money(r.getBigDecimal("rate")))
              .source(r.getString("source"))
              .version(r.getObject("version", Long.class));
}
