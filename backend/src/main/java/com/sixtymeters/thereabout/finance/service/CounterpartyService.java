package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;
import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical merchant identity is independent of the direction and currency of ledger accounts. */
@Service
@RequiredArgsConstructor
public class CounterpartyService {
  private final FinanceCounterpartyRepository counterparties;
  private final FinanceAccountRepository accounts;
  private final CounterpartyReadRepository queries;
  private final FinanceReadRepository reads;
  private final FinanceWriteCoordinator writes;
  private final EntityManager entities;

  @Transactional(readOnly = true)
  public GenFinanceCounterpartyPage list(UserId user, GenFinanceCounterpartyQuery query) {
    int page = page(query.getPage()), size = pageSize(query.getPageSize());
    var selection = queries.list(text(query.getQ()), query.getKind() == null ? null : query.getKind().getValue(), query.getActive(), page, size);
    return new GenFinanceCounterpartyPage().items(selection.ids().stream().map(id -> get(user, id)).toList())
        .total(selection.total()).page(page).pageSize(size);
  }

  @Transactional(readOnly = true)
  public GenFinanceCounterparty get(UserId user, long id) { return projection(user, canonical(id)); }

  private FinanceCounterpartyEntity canonical(long id) {
    var visited = new HashSet<Long>();
    var c = requireCounterparty(id);
    while (c.getMergedIntoId() != null) {
      if (!visited.add(c.getId())) throw new IllegalStateException("Invalid counterparty redirect");
      c = requireCounterparty(c.getMergedIntoId());
    }
    return c;
  }
  private FinanceCounterpartyEntity requireCounterparty(long id) {
    return counterparties.findById(id).orElseThrow(() -> missing("Counterparty"));
  }
  private GenFinanceCounterparty projection(UserId user, FinanceCounterpartyEntity c) {
    var linked = accounts.findByCounterpartyIdOrderByIdAsc(c.getId());
    return new GenFinanceCounterparty().id(c.getId()).name(c.getName()).websiteUrl(c.getWebsiteUrl())
        .version(c.getVersion()).aliases(c.getAliases().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList())
        .accounts(linked.stream().map(a -> reads.account(user, a.getId())).toList())
        .active(linked.stream().anyMatch(a -> a.isActive() && !a.isDeleted()));
  }

  public GenFinanceCounterparty save(UserId user, long id, GenFinanceCounterpartyInput input) {
    return writes.write(user, "counterparties.save:" + id, input.getRequestKey(), input, GenFinanceCounterparty.class, () -> {
      var c = requireCounterparty(id);
      conflict(c.getMergedIntoId() == null, "Counterparty was combined; reload first");
      version(input.getVersion(), c.getVersion());
      var before = projection(user, c);
      c.setName(required(input.getName(), "name"));
      c.setWebsiteUrl(WebsiteUrls.normalize(input.getWebsiteUrl()));
      require(input.getAliases() != null && input.getAliases().size() <= 500, "Choose up to 500 aliases");
      var aliases = new LinkedHashSet<String>();
      for (String alias : input.getAliases()) {
        String name = required(alias, "alias"); require(name.length() <= 1024, "Alias is too long"); aliases.add(name);
      }
      c.getAliases().clear(); c.getAliases().addAll(aliases); c.setUpdatedAt(Instant.now());
      entities.flush();
      var after = projection(user, c); writes.audit(user, "counterparties.save", id, before, after); return after;
    });
  }

  @Transactional(readOnly = true)
  public GenFinanceCounterpartyMergeResult preview(UserId user, GenFinanceCounterpartyMergePreviewInput input) {
    return preview(user, input.getIds(), input.getTargetId(), input.getName(), input.getWebsiteUrl());
  }
  private GenFinanceCounterpartyMergeResult preview(UserId user, Collection<Long> ids, Long target, String name, String website) {
    require(ids != null && ids.size() >= 2 && ids.size() <= 100 && ids.stream().noneMatch(Objects::isNull)
        && new HashSet<>(ids).size() == ids.size() && ids.contains(target), "Select 2–100 distinct counterparties and a surviving identity");
    require(required(name, "name").length() <= 1024, "Name is too long");
    String normalizedWebsite = WebsiteUrls.normalize(website);
    var selected = ids.stream().map(id -> {
      var c = requireCounterparty(id);
      conflict(c.getMergedIntoId() == null, "A selected counterparty was already combined; reload first");
      return projection(user, c);
    }).toList();
    var aliases = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
    selected.forEach(c -> { aliases.add(c.getName()); aliases.addAll(c.getAliases()); });
    var survivor = projection(user, requireCounterparty(target));
    survivor.setName(name.trim()); survivor.setWebsiteUrl(normalizedWebsite);
    survivor.setAliases(new ArrayList<>(aliases));
    survivor.setAccounts(selected.stream().flatMap(c -> c.getAccounts().stream()).toList());
    require(survivor.getAccounts().stream().allMatch(a -> a.getKind() == GenFinanceAccountKind.EXPENSE || a.getKind() == GenFinanceAccountKind.REVENUE), "Only counterparties can be combined");
    return new GenFinanceCounterpartyMergeResult().counterparty(survivor).selected(selected)
        .mergedIds(ids.stream().filter(id -> !id.equals(target)).toList()).aliases(new ArrayList<>(aliases))
        .affectedTransactions(queries.affectedTransactions(new ArrayList<>(ids))).conflicts(List.of());
  }

  public GenFinanceCounterpartyMergeResult merge(UserId user, GenFinanceCounterpartyMergeInput input) {
    return writes.write(user, "counterparties.merge", input.getRequestKey(), input, GenFinanceCounterpartyMergeResult.class, () -> {
      var result = preview(user, input.getIds(), input.getTargetId(), input.getName(), input.getWebsiteUrl());
      require(input.getVersions() != null && input.getVersions().size() == result.getSelected().size(), "Every selected counterparty needs an expected version");
      var versions = new HashMap<Long, Long>();
      for (var expected : input.getVersions()) require(expected != null && expected.getId() != null && expected.getVersion() != null
          && versions.putIfAbsent(expected.getId(), expected.getVersion()) == null, "Invalid expected versions");
      for (var c : result.getSelected()) version(versions.get(c.getId()), c.getVersion());
      var target = requireCounterparty(input.getTargetId());
      target.setName(result.getCounterparty().getName()); target.setWebsiteUrl(result.getCounterparty().getWebsiteUrl());
      target.getAliases().addAll(result.getAliases()); target.setUpdatedAt(Instant.now());
      for (Long id : result.getMergedIds()) {
        var old = requireCounterparty(id); old.setMergedIntoId(target.getId()); old.setUpdatedAt(Instant.now());
        for (var account : accounts.findByCounterpartyIdOrderByIdAsc(id)) account.setCounterpartyId(target.getId());
      }
      entities.flush();
      result.setCounterparty(projection(user, target));
      writes.audit(user, "counterparties.merge", target.getId(), result.getSelected(), result);
      return result;
    });
  }

  /** Called within the existing finance write transaction for every new counterparty account. */
  public void attach(FinanceAccountEntity account) {
    if (!account.getKind().isCounterparty() || account.getCounterpartyId() != null) return;
    var matches = counterparties.matching(normalize(account.getName()));
    require(matches.size() <= 1, "Several counterparties match this name; choose an existing account");
    var c = matches.isEmpty() ? new FinanceCounterpartyEntity() : matches.getFirst();
    if (matches.isEmpty()) { c.setName(account.getName()); c.setWebsiteUrl(account.getWebsiteUrl()); }
    c.getAliases().add(account.getName()); c.setUpdatedAt(Instant.now());
    counterparties.saveAndFlush(c); account.setCounterpartyId(c.getId());
  }

  public void associate(FinanceAccountEntity account, long id) {
    require(account.getKind().isCounterparty(), "Canonical counterparties require an expense or revenue account");
    var canonical = counterparties.findById(id).orElseThrow(() -> missing("Counterparty"));
    conflict(canonical.getMergedIntoId() == null, "Counterparty was combined; select its surviving identity");
    require(account.getCounterpartyId() == null || account.getCounterpartyId().equals(id), "Account counterparty cannot be changed");
    account.setCounterpartyId(id);
  }

  @Transactional(readOnly = true)
  public List<FinanceAccountEntity> matchingAccounts(String name, AccountKind kind, String currency) {
    var matches = counterparties.matching(normalize(name));
    var candidates = matches.stream()
        .map(c -> accounts.findByCounterpartyIdOrderByIdAsc(c.getId()).stream()
            .filter(a -> !a.isDeleted() && a.isActive() && a.getKind() == kind && a.getCurrency().equals(currency))
            .toList())
        .filter(linked -> !linked.isEmpty()).toList();
    require(candidates.size() <= 1 && (!candidates.isEmpty() || matches.size() <= 1), "Several existing counterparties match; select the correct one");
    // Accounts of an already combined identity are equivalent within a direction and currency.
    return candidates.isEmpty() ? List.of() : List.of(candidates.getFirst().getFirst());
  }
  private String normalize(String value) { return required(value, "counterparty").trim().toLowerCase(Locale.ROOT); }
}
