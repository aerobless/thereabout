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
          if (input.getLogoUrl() != null) account.setLogoUrl(logoUrl(input.getLogoUrl()));
          if (input.getWebsiteUrl() != null) account.setWebsiteUrl(websiteUrl(input.getWebsiteUrl()));
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

  private String websiteUrl(String value) {
    if (value.isBlank()) return null;
    require(value.length() <= 2048, "Website URL is too long");
    String url = value.trim();
    if (!url.contains("://")) url = "https://" + url;
    try {
      var uri = java.net.URI.create(url);
      require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
          && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443),
          "Enter a bank website using HTTPS without credentials");
      // Only the public home page is needed; never fetch or retain private paths or query strings.
      return new java.net.URI("https", null, uri.getHost().toLowerCase(java.util.Locale.ROOT),
          -1, "/", null, null).toASCIIString();
    } catch (IllegalArgumentException | java.net.URISyntaxException ex) {
      throw new com.sixtymeters.thereabout.config.ThereaboutException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid website URL");
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
