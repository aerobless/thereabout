package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {
  private final FinanceAccountRepository accounts;
  private final CounterpartyService counterparties;
  private final FinanceCurrencyRepository currencies;
  private final FinancePostingRepository postings;
  private final FinanceReadRepository reads;
  private final FinanceWriteCoordinator writes;
  private final com.sixtymeters.thereabout.access.UserContext users;

  /** Main accounts are shared among authenticated personal data users; ownership governs reports, not access. */
  public FinanceAccountEntity requireAccount(UserId user, long id) {
    var account=accounts.findById(id).orElseThrow(() -> missing("Account"));
    return account;
  }

  /** Used only for legs of an already authorized transaction, including shared technical accounts. */
  public FinanceAccountEntity transactionAccount(long id) { return accounts.findById(id).orElseThrow(() -> missing("Account")); }

  public FinanceAccountEntity transferAccount(long id) {
    var account=accounts.findById(id).orElseThrow(() -> missing("Account"));
    if (!account.getKind().isOwn()) throw missing("Transfer account");
    return account;
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

  public GenFinanceAccountResult save(UserId user, GenFinanceAccountInput input) {
    return writes.write(user,
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
              input.getId() == null ? new FinanceAccountEntity() : requireAccount(user, input.getId());
          GenFinanceAccount before =
              account.getId() == null ? null : reads.account(user, account.getId());
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
          if (input.getLogoUrl() != null) account.setLogoUrl(logoUrl(input.getLogoUrl()));
          if (input.getWebsiteUrl() != null) account.setWebsiteUrl(WebsiteUrls.normalize(input.getWebsiteUrl()));
          account.setName(name);
          account.setKind(kind);
          if (account.getId() == null) {
            account.setUserId(kind.isOwn()
                ? (input.getUserId() == null ? user.value() : users.require(input.getUserId()).value())
                : null);
          } else if (input.getUserId() != null) {
            require(java.util.Objects.equals(account.getUserId(), input.getUserId()), "Account owner cannot be changed");
          }
          account.setCurrency(currency);
          account.setActive(input.getActive() == null || input.getActive());
          account.setIncludeNetWorth(
              kind.isOwn() && (input.getIncludeNetWorth() == null || input.getIncludeNetWorth()));
          counterparties.attach(account);
          accounts.saveAndFlush(account);
          var after = reads.account(user, account.getId());
          writes.audit(user, "accounts.save", account.getId(), before, after);
          return new GenFinanceAccountResult().account(after);
        });
  }

  private String logoUrl(String value) {
    if (value.isBlank()) return null;
    require(value.length() <= 2048, "Logo URL is too long");
    try {
      var uri = java.net.URI.create(value.trim());
      require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
          && uri.getUserInfo() == null, "Logo must be an HTTPS image URL");
      return uri.toASCIIString();
    } catch (IllegalArgumentException ex) {
      throw new com.sixtymeters.thereabout.config.ThereaboutException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid logo URL");
    }
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
