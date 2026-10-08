package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ValuationService {
  private final AccountService accounts;
  private final FinanceReadRepository reads;
  private final FinanceValuationRepository valuations;
  private final TransactionService transactions;
  private final FinanceWriteCoordinator writes;

  private FinanceValuationEntity amendment(Long id, Long expectedVersion, long account) {
    if (id == null) return null;
    var valuation = valuations.findById(id).orElseThrow(() -> missing("Valuation"));
    version(expectedVersion, valuation.getVersion());
    require(valuation.getAccountId() == account, "A valuation belongs to its original account");
    require(!valuation.isDeleted(), "Restore the valuation before editing");
    return valuation;
  }

  @Transactional(readOnly = true)
  public GenFinanceValuationPreview preview(UserId user, GenFinanceValuationPreviewInput input) {
    var account = accounts.requireAccount(user, input.getAccountId());
    require(account.getKind().isValuedAsset(), "Account is not a valued asset");
    var valuation = amendment(input.getId(), input.getVersion(), account.getId());
    var date = dateTime(input.getDate(), null, false);
    require(date != null, "date required");
    var reported = decimal(input.getReportedValue());
    require(reported.signum() >= 0, "Value must not be negative");
    if (valuation == null || reported.compareTo(valuation.getReportedValue()) != 0)
      accounts.precision(reported, account.getCurrency());
    var balance = reads.balance(user, account.getId(), date);
    // Remove only this valuation's old movement. Later statements remain independent snapshots.
    if (valuation != null && valuation.getTransactionId() != null) {
      var old = reads.transaction(user, valuation.getTransactionId());
      if (!old.getDeleted() && !dateTime(old.getOccurredAt(), null, false).isAfter(date)) {
        var amount = old.getSourceAccountId().equals(account.getId())
            ? decimal(old.getSourceAmount()).negate() : decimal(old.getDestinationAmount());
        balance = balance.subtract(amount);
      }
    }
    var earliestChange = valuation != null && valuation.getOccurredAt().isBefore(date) ? valuation.getOccurredAt() : date;
    return new GenFinanceValuationPreview().accountId(account.getId()).date(date.toString())
        .reportedValue(money(reported)).previousBalance(money(balance))
        .difference(money(reported.subtract(balance))).currency(account.getCurrency())
        .laterValuations(valuations.existsByAccountIdAndDeletedFalseAndOccurredAtAfterAndIdNot(account.getId(), earliestChange,
            valuation == null ? 0L : valuation.getId()));
  }

  public GenFinanceValuationResult save(UserId user, GenFinanceValuationInput input) {
    return writes.write(user, "valuations.save", input.getRequestKey(), input, GenFinanceValuationResult.class, () -> {
      var valuation = amendment(input.getId(), input.getVersion(), input.getAccountId());
      var before = valuation == null ? null : reads.valuation(user, valuation.getId());
      var preview = preview(user, new GenFinanceValuationPreviewInput().id(input.getId()).version(input.getVersion())
          .accountId(input.getAccountId()).date(input.getDate()).reportedValue(input.getReportedValue()));
      String reference = required(input.getReference(), "reference");
      require(reference.length() <= 255, "Reference too long");
      conflict(valuation == null ? !valuations.existsByAccountIdAndReference(input.getAccountId(), reference)
          : !valuations.existsByAccountIdAndReferenceAndIdNot(input.getAccountId(), reference, valuation.getId()),
          "This valuation reference has already been recorded");
      var previous = decimal(preview.getPreviousBalance());
      conflict(previous.compareTo(decimal(input.getExpectedBalance())) == 0, "Balance changed since preview; preview again");
      var delta = decimal(preview.getDifference());
      var reported = decimal(preview.getReportedValue());
      var date = dateTime(input.getDate(), null, false);
      Long transactionId = transactions.valuationAdjustment(user, input.getAccountId(), preview.getCurrency(), date,
          reported, delta, reference, valuation == null ? null : valuation.getTransactionId());
      if (valuation == null) { valuation = new FinanceValuationEntity(); valuation.setOrigin("MANUAL"); }
      valuation.setAccountId(input.getAccountId()); valuation.setOccurredAt(date); valuation.setReportedValue(reported);
      valuation.setPreviousBalance(previous); valuation.setTransactionId(transactionId); valuation.setReference(reference);
      valuations.saveAndFlush(valuation);
      var after = reads.valuation(user, valuation.getId());
      writes.audit(user, before == null ? "valuations.save" : "valuations.amend", input.getAccountId(), before, after);
      return new GenFinanceValuationResult().valuation(after).preview(preview);
    });
  }

  public GenFinanceValuation setDeleted(UserId user, GenFinanceVersionedInput input, boolean deleted) {
    return writes.write(user, deleted ? "valuations.delete" : "valuations.restore", input.getRequestKey(), input,
        GenFinanceValuation.class, () -> {
          var valuation = valuations.findById(input.getId()).orElseThrow(() -> missing("Valuation"));
          accounts.requireAccount(user, valuation.getAccountId()); version(input.getVersion(), valuation.getVersion());
          var before = reads.valuation(user, valuation.getId());
          if (valuation.getTransactionId() != null) {
            var transaction = reads.transaction(user, valuation.getTransactionId());
            transactions.setDeleted(user, new GenFinanceVersionedInput().id(transaction.getId())
                .version(transaction.getVersion()).requestKey(UUID.randomUUID().toString()), deleted);
          } else {
            valuation.setDeleted(deleted); valuations.saveAndFlush(valuation);
            writes.audit(user, deleted ? "valuations.delete" : "valuations.restore", valuation.getAccountId(), before,
                reads.valuation(user, valuation.getId()));
          }
          return reads.valuation(user, valuation.getId());
        });
  }
}
