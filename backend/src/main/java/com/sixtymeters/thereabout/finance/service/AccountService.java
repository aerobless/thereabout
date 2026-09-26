package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {
  private final FinanceAccountRepository accounts;
  private final FinanceCurrencyRepository currencies;
  private final FinancePostingRepository postings;
  private final FinanceReadRepository reads;
  private final FinanceWriteCoordinator writes;

  public FinanceAccountEntity requireAccount(long id) {
    return accounts.findById(id).orElseThrow(() -> missing("Account"));
  }

  public void currency(String code) {
    require(currencies.existsById(code), "Unknown currency");
  }

  public void precision(BigDecimal amount, String code) {
    var c = currencies.findById(code).orElseThrow(() -> missing("Currency"));
    require(
        amount.stripTrailingZeros().scale() <= c.getDecimalPlaces(),
        "Amount exceeds currency precision");
  }

  public GenFinanceAccountResult save(GenFinanceAccountInput input) {
    return writes.write(
        "accounts.save",
        input.getRequestKey(),
        input,
        GenFinanceAccountResult.class,
        () -> {
          String name = required(input.getName(), "name"),
              currency = required(input.getCurrency(), "currency");
          require(input.getKind() != null, "Account kind required");
          var kind = AccountKind.valueOf(input.getKind().getValue());
          require(kind.isOwn() || kind.isCounterparty(), "Invalid account kind");
          currency(currency);
          var account =
              input.getId() == null ? new FinanceAccountEntity() : requireAccount(input.getId());
          GenFinanceAccount before =
              account.getId() == null ? null : reads.account(account.getId());
          if (before != null) {
            version(input.getVersion(), account.getVersion());
            require(
                account.getKind().isOwn() == kind.isOwn(),
                "Cannot change between own and counterparty account");
            require(
                currency.equals(account.getCurrency())
                    || !postings.existsByAccountId(account.getId()),
                "Cannot change currency of an account with postings");
            require(
                account.getKind() == kind || !account.getKind().isCounterparty(),
                "Cannot change counterparty direction");
          }
          account.setName(name);
          account.setKind(kind);
          account.setCurrency(currency);
          account.setActive(input.getActive() == null || input.getActive());
          account.setIncludeNetWorth(
              kind.isOwn() && (input.getIncludeNetWorth() == null || input.getIncludeNetWorth()));
          accounts.saveAndFlush(account);
          var after = reads.account(account.getId());
          writes.audit("accounts.save", account.getId(), before, after);
          return new GenFinanceAccountResult().account(after);
        });
  }

  public FinanceAccountEntity valuationCounter(AccountKind kind, String currency) {
    return accounts
        .findFirstByNameAndKindAndCurrencyAndDeletedFalseOrderByIdAsc(
            "Asset valuation", kind, currency)
        .orElseGet(
            () -> {
              var counter = new FinanceAccountEntity();
              counter.setName("Asset valuation");
              counter.setKind(kind);
              counter.setCurrency(currency);
              return accounts.saveAndFlush(counter);
            });
  }
}
