package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {
  private final FinanceRateRepository rates;
  private final ExchangeRateBatchRepository batch;
  private final FinanceReadRepository reads;
  private final FinanceWriteCoordinator writes;
  private final AccountService accounts;
  private final EcbRateClient ecb;

  public GenFinanceRateResult save(GenFinanceRateInput input) {
    return writes.write(
        "rates.save",
        input.getRequestKey(),
        input,
        GenFinanceRateResult.class,
        () -> {
          String from = required(input.getFromCurrency(), "fromCurrency"),
              to = required(input.getToCurrency(), "toCurrency");
          require(
              to.equals("CHF") && !from.equals(to),
              "Manual rates must convert a foreign currency to CHF");
          accounts.currency(from);
          var date = date(input.getDate());
          var value = decimal(input.getRate());
          require(value.signum() > 0, "Rate must be positive");
          var rate =
              rates
                  .findByFromCurrencyAndToCurrencyAndRateDateAndSource(from, to, date, "MANUAL")
                  .orElseGet(FinanceRateEntity::new);
          GenFinanceRate before = rate.getId() == null ? null : reads.rate(rate.getId());
          if (before != null) version(input.getVersion(), rate.getVersion());
          rate.setFromCurrency(from);
          rate.setToCurrency(to);
          rate.setRateDate(date);
          rate.setRate(value);
          rate.setSource("MANUAL");
          rates.saveAndFlush(rate);
          var after = reads.rate(rate.getId());
          writes.audit("rates.save", rate.getId(), before, after);
          return new GenFinanceRateResult().rate(after);
        });
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public GenFinanceRateRefreshResult refresh(GenFinanceRefreshInput input) {
    var previous =
        writes.replay(
            "rates.refresh", input.getRequestKey(), input, GenFinanceRateRefreshResult.class);
    if (previous.isPresent()) return previous.get();
    // Do not hold a ledger lock or database transaction while waiting for ECB.
    var download = ecb.download();
    return writes.write(
        "rates.refresh",
        input.getRequestKey(),
        input,
        GenFinanceRateRefreshResult.class,
        () -> {
          batch.saveEcb(download.values());
          return new GenFinanceRateRefreshResult()
              .saved((long) download.values().size())
              .latestDate(download.latest().toString())
              .source("ECB");
        });
  }
}
