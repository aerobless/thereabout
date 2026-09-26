package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValuationService {
  private final AccountService accounts;
  private final FinanceReadRepository reads;
  private final FinanceValuationRepository valuations;
  private final TransactionService transactions;
  private final FinanceWriteCoordinator writes;

  public GenFinanceValuationPreview preview(GenFinanceValuationPreviewInput input) {
    var account = accounts.requireAccount(input.getAccountId());
    require(account.getKind().isValuedAsset(), "Account is not a valued asset");
    var date = dateTime(input.getDate(), null, false);
    require(date != null, "date required");
    var reported = decimal(input.getReportedValue());
    require(reported.signum() >= 0, "Value must not be negative");
    accounts.precision(reported, account.getCurrency());
    var balance = reads.balance(account.getId(), date);
    return new GenFinanceValuationPreview()
        .accountId(account.getId())
        .date(date.toString())
        .reportedValue(money(reported))
        .previousBalance(money(balance))
        .difference(money(reported.subtract(balance)))
        .currency(account.getCurrency());
  }

  public GenFinanceValuationResult save(GenFinanceValuationInput input) {
    return writes.write(
        "valuations.save",
        input.getRequestKey(),
        input,
        GenFinanceValuationResult.class,
        () -> {
          var preview =
              preview(
                  new GenFinanceValuationPreviewInput()
                      .accountId(input.getAccountId())
                      .date(input.getDate())
                      .reportedValue(input.getReportedValue()));
          String reference = required(input.getReference(), "reference");
          require(reference.length() <= 255, "Reference too long");
          conflict(
              !valuations.existsByAccountIdAndReference(input.getAccountId(), reference),
              "This valuation reference has already been recorded");
          var previous = decimal(preview.getPreviousBalance());
          conflict(
              previous.compareTo(decimal(input.getExpectedBalance())) == 0,
              "Balance changed since preview; preview again");
          var delta = decimal(preview.getDifference());
          var reported = decimal(preview.getReportedValue());
          var date = dateTime(input.getDate(), null, false);
          Long transactionId =
              delta.signum() == 0
                  ? null
                  : transactions.valuationAdjustment(
                      input.getAccountId(),
                      preview.getCurrency(),
                      date,
                      reported,
                      delta,
                      reference);
          var valuation = new FinanceValuationEntity();
          valuation.setAccountId(input.getAccountId());
          valuation.setOccurredAt(date);
          valuation.setReportedValue(reported);
          valuation.setPreviousBalance(previous);
          valuation.setTransactionId(transactionId);
          valuation.setReference(reference);
          valuation.setOrigin("MANUAL");
          valuations.saveAndFlush(valuation);
          var after = reads.valuation(valuation.getId());
          writes.audit("valuations.save", input.getAccountId(), null, after);
          return new GenFinanceValuationResult().valuation(after).preview(preview);
        });
  }
}
