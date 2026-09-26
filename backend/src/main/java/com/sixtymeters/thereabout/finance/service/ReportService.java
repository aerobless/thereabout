package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.finance.data.FinanceReadRepository.LedgerEntry;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Typed report projections. Original ledger amounts are never modified by conversion. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
  private final FinanceReadRepository reads;
  private final ExchangeRateReadRepository rates;
  private final Clock financeClock;

  public GenFinanceOverview overview(GenFinancePeriodQuery query) {
    LocalDateTime now = LocalDateTime.now(financeClock);
    var period = period(query, now);
    var to = period.to().isAfter(now) ? now : period.to();
    var accounts = reads.ownAccounts();
    var ledger = reads.ledger();
    var balances = balances(ledger, now);
    var fx = new Conversion(rates);
    var totals = new EnumMap<GenFinanceAccountKind, BigDecimal>(GenFinanceAccountKind.class);
    BigDecimal total = BigDecimal.ZERO;
    for (var account : accounts) {
      var balance = balances.getOrDefault(account.getId(), BigDecimal.ZERO);
      account.setBalance(money(balance));
      var quote = rates.quote(account.getCurrency(), now.toLocalDate());
      BigDecimal converted =
          Boolean.TRUE.equals(account.getIncludeNetWorth())
              ? fx.convert(balance, account.getCurrency(), now.toLocalDate())
              : quote.map(q -> balance.multiply(q.rate())).orElse(BigDecimal.ZERO);
      account.setBalanceChf(quote.isPresent() ? money(converted) : null);
      quote.ifPresent(
          q -> {
            account.setRateDate(q.date().toString());
            account.setRateSource(q.source());
          });
      if (Boolean.TRUE.equals(account.getIncludeNetWorth())) {
        total = total.add(converted);
        totals.merge(account.getKind(), converted, BigDecimal::add);
      }
    }
    long selected = query.getAccountId() == null ? 0 : query.getAccountId();
    String currency =
        selected == 0
            ? "CHF"
            : accounts.stream()
                .filter(a -> a.getId() == selected)
                .findFirst()
                .orElseThrow(() -> missing("Own account"))
                .getCurrency();
    List<GenFinanceSeriesPoint> series = new ArrayList<>();
    LocalDateTime cursor = period.from();
    while (!cursor.isAfter(to)) {
      var at = balances(ledger, cursor);
      BigDecimal value = BigDecimal.ZERO;
      for (var account : accounts) {
        if (selected != 0 && account.getId() != selected) continue;
        if (selected == 0 && !Boolean.TRUE.equals(account.getIncludeNetWorth())) continue;
        var nativeValue = at.getOrDefault(account.getId(), BigDecimal.ZERO);
        value =
            value.add(
                selected == 0
                    ? fx.convert(nativeValue, account.getCurrency(), cursor.toLocalDate())
                    : nativeValue);
      }
      series.add(
          new GenFinanceSeriesPoint().date(cursor.toLocalDate().toString()).value(money(value)));
      if (cursor.equals(to)) break;
      LocalDateTime next =
          cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay().minusNanos(1);
      if (!next.isAfter(cursor))
        next =
            next.plusDays(1)
                .toLocalDate()
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay()
                .minusNanos(1);
      cursor = next.isAfter(to) ? to : next;
    }
    return new GenFinanceOverview()
        .netWorth(money(total))
        .totals(
            new GenFinanceTotals()
                .cash(money(totals.getOrDefault(GenFinanceAccountKind.CASH, BigDecimal.ZERO)))
                .investment(
                    money(totals.getOrDefault(GenFinanceAccountKind.INVESTMENT, BigDecimal.ZERO)))
                .realEstate(
                    money(totals.getOrDefault(GenFinanceAccountKind.REAL_ESTATE, BigDecimal.ZERO)))
                .otherAsset(
                    money(totals.getOrDefault(GenFinanceAccountKind.OTHER_ASSET, BigDecimal.ZERO))))
        .accounts(accounts)
        .series(series)
        .chartCurrency(currency)
        .warnings(fx.warnings())
        .complete(fx.complete())
        .asOf(now.toString())
        .latestTransaction(reads.latestTransaction())
        .earliestTransaction(reads.earliestTransaction());
  }

  public GenFinanceReport report(GenFinancePeriodQuery query) {
    var period = period(query, LocalDateTime.now(financeClock));
    long selected = query.getAccountId() == null ? 0 : query.getAccountId();
    var accounts = reads.ownAccounts();
    var ledger = reads.ledger();
    var fx = new Conversion(rates);
    Set<Long> own = new HashSet<>();
    accounts.forEach(a -> own.add(a.getId()));
    Map<String, Cashflow> months = new TreeMap<>();
    Map<Long, Cashflow> categories = new TreeMap<>();
    for (var entry : ledger) {
      if (!own.contains(entry.accountId())
          || (selected != 0 && entry.accountId() != selected)
          || !period.contains(entry.occurredAt())) continue;
      if (entry.effect() != FinancialEffect.OPERATING
          || (entry.type() != TransactionType.DEPOSIT
              && entry.type() != TransactionType.WITHDRAWAL)) continue;
      var amount = fx.convert(entry.amount(), entry.currency(), entry.occurredAt().toLocalDate());
      String month = YearMonth.from(entry.occurredAt()).toString();
      months.computeIfAbsent(month, key -> new Cashflow(key, null)).add(amount);
      long category = entry.categoryId() == null ? 0 : entry.categoryId();
      categories
          .computeIfAbsent(
              category,
              key ->
                  new Cashflow(
                      entry.categoryName() == null ? "Uncategorized" : entry.categoryName(),
                      entry.categoryId()))
          .add(amount);
    }
    var monthRows = months.values().stream().map(Cashflow::row).toList();
    var categoryRows =
        categories.values().stream()
            .sorted(Comparator.comparing((Cashflow c) -> c.expenses).reversed())
            .map(Cashflow::row)
            .toList();
    BigDecimal income =
        months.values().stream().map(c -> c.income).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal expenses =
        months.values().stream().map(c -> c.expenses).reduce(BigDecimal.ZERO, BigDecimal::add);
    var start = balances(ledger, period.from().minusNanos(1));
    var end = balances(ledger, period.to());
    List<GenFinanceInvestmentRow> investments = new ArrayList<>();
    for (var account : accounts) {
      if (!AccountKind.valueOf(account.getKind().getValue()).isValuedAsset()
          || (selected != 0 && account.getId() != selected)) continue;
      BigDecimal in = BigDecimal.ZERO,
          out = BigDecimal.ZERO,
          gain = BigDecimal.ZERO,
          other = BigDecimal.ZERO;
      for (var entry : ledger) {
        if (entry.accountId() != account.getId() || !period.contains(entry.occurredAt())) continue;
        var amount = entry.amount();
        switch (entry.effect()) {
          case VALUATION -> gain = gain.add(amount);
          case OPENING, RECONCILIATION -> other = other.add(amount);
          case OPERATING -> {
            if (amount.signum() > 0) in = in.add(amount);
            else out = out.add(amount.abs());
          }
        }
      }
      investments.add(
          new GenFinanceInvestmentRow()
              .id(account.getId())
              .name(account.getName())
              .kind(account.getKind())
              .currency(account.getCurrency())
              .start(money(start.getOrDefault(account.getId(), BigDecimal.ZERO)))
              .end(money(end.getOrDefault(account.getId(), BigDecimal.ZERO)))
              .inflows(money(in))
              .outflows(money(out))
              .valuationChange(money(gain))
              .adjustments(money(other)));
    }
    return new GenFinanceReport()
        .months(monthRows)
        .categories(categoryRows)
        .investments(investments)
        .income(money(income))
        .expenses(money(expenses))
        .net(money(income.subtract(expenses)))
        .currency("CHF")
        .warnings(fx.warnings())
        .exchangeRates(fx.used());
  }

  private Period period(GenFinancePeriodQuery query, LocalDateTime now) {
    var from = dateTime(query.getFrom(), now.toLocalDate().withDayOfYear(1).atStartOfDay(), false);
    var to = dateTime(query.getTo(), now, true);
    require(!from.isAfter(to), "Start must precede end");
    require(from.plusYears(100).isAfter(to), "Maximum range is 100 years");
    return new Period(from, to);
  }

  private record Period(LocalDateTime from, LocalDateTime to) {
    boolean contains(LocalDateTime at) {
      return !at.isBefore(from) && !at.isAfter(to);
    }
  }

  private Map<Long, BigDecimal> balances(List<LedgerEntry> ledger, LocalDateTime at) {
    Map<Long, BigDecimal> balances = new HashMap<>();
    for (var entry : ledger)
      if (!entry.occurredAt().isAfter(at))
        balances.merge(entry.accountId(), entry.amount(), BigDecimal::add);
    return balances;
  }

  private static final class Cashflow {
    final String name;
    final Long categoryId;
    BigDecimal income = BigDecimal.ZERO, expenses = BigDecimal.ZERO;

    Cashflow(String name, Long categoryId) {
      this.name = name;
      this.categoryId = categoryId;
    }

    void add(BigDecimal amount) {
      if (amount.signum() >= 0) income = income.add(amount);
      else expenses = expenses.add(amount.abs());
    }

    GenFinanceCashflowRow row() {
      return new GenFinanceCashflowRow()
          .name(name)
          .categoryId(categoryId)
          .income(money(income))
          .expenses(money(expenses))
          .net(money(income.subtract(expenses)));
    }
  }

  /** Per-request cache and warnings: never shared across users or report requests. */
  private static final class Conversion {
    private record Key(String currency, LocalDate date) {}

    final ExchangeRateReadRepository rates;
    final Map<Key, Optional<ExchangeRateReadRepository.Quote>> cache = new LinkedHashMap<>();
    final Set<String> warnings = new LinkedHashSet<>();
    boolean complete = true;

    Conversion(ExchangeRateReadRepository rates) {
      this.rates = rates;
    }

    BigDecimal convert(BigDecimal amount, String currency, LocalDate date) {
      var quote =
          cache.computeIfAbsent(new Key(currency, date), key -> rates.quote(currency, date));
      if (quote.isEmpty()) {
        if (amount.signum() != 0) {
          warnings.add("Missing " + currency + "/CHF rate on " + date);
          complete = false;
        }
        return BigDecimal.ZERO;
      }
      var q = quote.get();
      if (q.date().isBefore(date.minusDays(7)))
        warnings.add("Stale " + currency + "/CHF rate: " + q.date() + " (" + q.source() + ")");
      return amount.multiply(q.rate());
    }

    boolean complete() {
      return complete;
    }

    List<String> warnings() {
      return List.copyOf(warnings);
    }

    List<GenFinanceQuote> used() {
      return cache.entrySet().stream()
          .filter(e -> e.getValue().isPresent())
          .map(
              e -> {
                var q = e.getValue().get();
                return new GenFinanceQuote()
                    .requested(e.getKey().currency() + e.getKey().date())
                    .date(q.date().toString())
                    .source(q.source())
                    .rate(money(q.rate()));
              })
          .toList();
    }
  }
}
