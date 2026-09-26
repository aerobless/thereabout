package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {
  private final FinanceTransactionRepository transactions;
  private final FinanceValuationRepository valuations;
  private final FinanceReadRepository reads;
  private final AccountService accounts;
  private final CategoryService categories;
  private final FinanceWriteCoordinator writes;
  private final java.time.Clock financeClock;

  public GenFinanceTransactionResult save(GenFinanceTransactionInput input) {
    return writes.write(
        "transactions.save",
        input.getRequestKey(),
        input,
        GenFinanceTransactionResult.class,
        () -> {
          var transaction =
              input.getId() == null ? new FinanceTransactionEntity() : existing(input.getId());
          GenFinanceTransaction before =
              transaction.getId() == null ? null : reads.transaction(transaction.getId());
          if (before != null) {
            version(input.getVersion(), transaction.getVersion());
            require(!transaction.isDeleted(), "Restore the transaction before editing");
            requireUnlinked(transaction.getId());
          }
          require(input.getType() != null, "Transaction type required");
          var type = TransactionType.valueOf(input.getType().getValue());
          require(
              before != null
                  || (type != TransactionType.OPENING && type != TransactionType.RECONCILIATION),
              "Opening/reconciliation entries are imported; create an explicit adjustment"
                  + " transaction instead");
          require(
              input.getSourceId() != null && input.getDestinationId() != null,
              "Source and destination required");
          require(
              !input.getSourceId().equals(input.getDestinationId()),
              "Source and destination must differ");
          var source = accounts.requireAccount(input.getSourceId());
          var destination = accounts.requireAccount(input.getDestinationId());
          validateAccounts(type, source, destination);
          String sc = required(input.getSourceCurrency(), "sourceCurrency"),
              dc = required(input.getDestinationCurrency(), "destinationCurrency");
          accounts.currency(sc);
          accounts.currency(dc);
          require(
              !source.getKind().isOwn() || sc.equals(source.getCurrency()),
              "Source currency differs from account currency");
          require(
              !destination.getKind().isOwn() || dc.equals(destination.getCurrency()),
              "Destination currency differs from account currency");
          BigDecimal sa = decimal(input.getSourceAmount()),
              da = decimal(input.getDestinationAmount());
          require(sa.signum() > 0 && da.signum() > 0, "Amounts must be positive");
          boolean unchanged =
              before != null
                  && sc.equals(before.getSourceCurrency())
                  && dc.equals(before.getDestinationCurrency())
                  && sa.compareTo(decimal(before.getSourceAmount())) == 0
                  && da.compareTo(decimal(before.getDestinationAmount())) == 0;
          if (!unchanged) {
            accounts.precision(sa, sc);
            accounts.precision(da, dc);
          }
          require(
              unchanged || !sc.equals(dc) || sa.compareTo(da) == 0,
              "Same-currency postings must balance");
          require(
              type == TransactionType.TRANSFER || sc.equals(dc),
              "Expenses and income use the own account currency on both legs; use foreignAmount for"
                  + " original currency");
          String fc = text(input.getForeignCurrency());
          BigDecimal fa = fc.isEmpty() ? null : decimal(input.getForeignAmount());
          if (fa != null) {
            accounts.currency(fc);
            require(fa.signum() > 0, "Foreign amount must be positive");
            if (before == null
                || !fc.equals(before.getForeignCurrency())
                || before.getForeignAmount() == null
                || fa.compareTo(decimal(before.getForeignAmount()).abs()) != 0)
              accounts.precision(fa, fc);
          }
          var effect =
              input.getEffect() == null
                  ? FinancialEffect.OPERATING
                  : FinancialEffect.valueOf(input.getEffect().getValue());
          if (type == TransactionType.OPENING) effect = FinancialEffect.OPENING;
          if (type == TransactionType.RECONCILIATION) effect = FinancialEffect.RECONCILIATION;
          var date = dateTime(input.getDate(), null, false);
          require(date != null, "date is required");
          transaction.setType(type);
          transaction.setEffect(effect);
          transaction.setDescription(required(input.getDescription(), "description"));
          transaction.setOccurredAt(date);
          transaction.setCategoryId(categories.validate(input.getCategoryId()));
          transaction.setNotes(text(input.getNotes()));
          transaction.setExternalReference(text(input.getExternalReference()));
          setPosting(
              transaction,
              PostingSide.SOURCE,
              source.getId(),
              sa.negate(),
              sc,
              fa == null ? null : fa.negate(),
              fc);
          setPosting(transaction, PostingSide.DESTINATION, destination.getId(), da, dc, fa, fc);
          transaction.touch(financeClock);
          transactions.saveAndFlush(transaction);
          var after = reads.transaction(transaction.getId());
          writes.audit("transactions.save", transaction.getId(), before, after);
          return new GenFinanceTransactionResult().transaction(after);
        });
  }

  private void validateAccounts(
      TransactionType type, FinanceAccountEntity source, FinanceAccountEntity destination) {
    require(!source.isDeleted() && !destination.isDeleted(), "Deleted account");
    require(source.isActive() && destination.isActive(), "Inactive account");
    switch (type) {
      case WITHDRAWAL ->
          require(
              source.getKind().isOwn() && destination.getKind() == AccountKind.EXPENSE,
              "An expense goes from an own account to an expense counterparty");
      case DEPOSIT ->
          require(
              source.getKind() == AccountKind.REVENUE && destination.getKind().isOwn(),
              "Income goes from a revenue counterparty to an own account");
      case TRANSFER ->
          require(
              source.getKind().isOwn() && destination.getKind().isOwn(),
              "Transfers need two own accounts");
      default -> {
        /* Imported opening/reconciliation directions retain their technical counterparty. */
      }
    }
  }

  private void setPosting(
      FinanceTransactionEntity transaction,
      PostingSide side,
      long account,
      BigDecimal amount,
      String currency,
      BigDecimal foreign,
      String foreignCurrency) {
    var posting =
        transaction.getPostings().stream()
            .filter(p -> p.getSide() == side)
            .findFirst()
            .orElseGet(
                () -> {
                  var created = new FinancePostingEntity();
                  created.setSide(side);
                  transaction.addPosting(created);
                  return created;
                });
    posting.setAccountId(account);
    posting.setAmount(amount);
    posting.setCurrency(currency);
    posting.setForeignAmount(foreign);
    posting.setForeignCurrency(foreignCurrency.isEmpty() ? null : foreignCurrency);
  }

  private FinanceTransactionEntity existing(long id) {
    return transactions.findById(id).orElseThrow(() -> missing("Transaction"));
  }

  private void requireUnlinked(long id) {
    require(
        !valuations.existsByTransactionId(id),
        "Valuation-linked transactions cannot be edited or deleted; add a new dated valuation");
  }

  public GenFinanceTransactionResult setDeleted(GenFinanceVersionedInput input, boolean deleted) {
    String operation = deleted ? "transactions.delete" : "transactions.restore";
    return writes.write(
        operation,
        input.getRequestKey(),
        input,
        GenFinanceTransactionResult.class,
        () -> {
          var transaction = existing(input.getId());
          var before = reads.transaction(input.getId());
          version(input.getVersion(), transaction.getVersion());
          requireUnlinked(input.getId());
          if (!deleted)
            for (var posting : transaction.getPostings()) {
              var account = accounts.requireAccount(posting.getAccountId());
              require(
                  !account.isDeleted()
                      && (!account.getKind().isOwn()
                          || posting.getCurrency().equals(account.getCurrency())),
                  "Cannot restore historical postings incompatible with current accounts");
              posting.setDeleted(false);
            }
          transaction.setDeleted(deleted);
          transaction.touch(financeClock);
          transactions.saveAndFlush(transaction);
          var after = reads.transaction(transaction.getId());
          writes.audit(operation, transaction.getId(), before, after);
          return new GenFinanceTransactionResult().transaction(after);
        });
  }

  public GenFinanceBulkResult categorize(GenFinanceBulkCategoryInput input) {
    return writes.write(
        "transactions.categorize",
        input.getRequestKey(),
        input,
        GenFinanceBulkResult.class,
        () -> {
          var items = input.getItems();
          require(
              items != null && !items.isEmpty() && items.size() <= 200,
              "Select 1 to 200 transactions");
          require(
              items.stream().map(GenFinanceSelection::getId).distinct().count() == items.size(),
              "Duplicate selection");
          Long category = categories.validate(input.getCategoryId());
          for (var item : items) {
            var transaction = existing(item.getId());
            var before = reads.transaction(item.getId());
            version(item.getVersion(), transaction.getVersion());
            transaction.setCategoryId(category);
            transaction.touch(financeClock);
            transactions.saveAndFlush(transaction);
            writes.audit(
                "transactions.categorize", item.getId(), before, reads.transaction(item.getId()));
          }
          return new GenFinanceBulkResult().updated(items.size());
        });
  }

  /** Called only inside the valuation service's existing atomic write boundary. */
  public Long valuationAdjustment(
      long account,
      String currency,
      LocalDateTime date,
      BigDecimal reported,
      BigDecimal delta,
      String reference) {
    boolean gain = delta.signum() > 0;
    var counter =
        accounts.valuationCounter(gain ? AccountKind.REVENUE : AccountKind.EXPENSE, currency);
    var transaction = new FinanceTransactionEntity();
    transaction.setType(gain ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL);
    transaction.setEffect(FinancialEffect.VALUATION);
    transaction.setDescription(gain ? "Valuation gain" : "Valuation loss");
    transaction.setOccurredAt(date);
    transaction.setNotes("Reported total: " + money(reported) + " " + currency);
    transaction.setExternalReference(reference);
    setPosting(
        transaction,
        PostingSide.SOURCE,
        gain ? counter.getId() : account,
        delta.abs().negate(),
        currency,
        null,
        "");
    setPosting(
        transaction,
        PostingSide.DESTINATION,
        gain ? account : counter.getId(),
        delta.abs(),
        currency,
        null,
        "");
    transaction.touch(financeClock);
    transactions.saveAndFlush(transaction);
    writes.audit(
        "transactions.valuation",
        transaction.getId(),
        null,
        reads.transaction(transaction.getId()));
    return transaction.getId();
  }
}
