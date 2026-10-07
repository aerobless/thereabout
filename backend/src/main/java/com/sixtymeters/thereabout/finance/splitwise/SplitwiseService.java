package com.sixtymeters.thereabout.finance.splitwise;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;
import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.client.data.*;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** One backend worker serializes remote reads. Each source expense has its own atomic ledger boundary. */
@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class SplitwiseService {
  private final SplitwiseConnectionRepository connections;
  private final SplitwiseSourceRepository sources;
  private final ConfigurationRepository configuration;
  private final FinanceAccountRepository accounts;
  private final FinanceReadRepository reads;
  private final SplitwiseClient client;
  private final SplitwiseLedger ledger;
  private final com.sixtymeters.thereabout.finance.service.FinanceWriteCoordinator writes;
  private final com.sixtymeters.thereabout.access.UserContext users;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final Clock financeClock;
  @Value("${thereabout.splitwise.worker-enabled:true}") private boolean workerEnabled;
  private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();

  private SplitwiseConnection connection() { return connections.findById(1L).orElseGet(() -> { var c = new SplitwiseConnection(); c.setSettingsJson(json.writeValueAsString(empty())); return c; }); }
  private GenSplitwiseSettings empty() { return new GenSplitwiseSettings().configured(false).tested(false).initialized(false).enabled(false).revision(0L).members(new ArrayList<>()).categories(new ArrayList<>()); }
  private void update(Consumer<SplitwiseConnection> action) { transactions.executeWithoutResult(t -> { var c = connection(); action.accept(c); connections.saveAndFlush(c); }); }
  private LocalDateTime now() { return LocalDateTime.now(financeClock); }
  private Instant instant(LocalDateTime date) { return date.atZone(financeClock.getZone()).toInstant(); }
  private String key() { return configuration.findById(ConfigurationKey.SPLITWISE_API_KEY).map(ConfigurationEntity::getConfigValue).orElse(""); }
  private GenSplitwiseSettings settings(SplitwiseConnection c) {
    return json.readValue(c.getSettingsJson(), GenSplitwiseSettings.class).configured(!key().isBlank()).tested(c.isTested())
        .initialized(c.isInitialized()).revision(c.getRevision());
  }
  public GenSplitwiseSettings settings() { return settings(connection()); }
  private boolean busy(SplitwiseConnection c) { return Set.of("PREVIEW_QUEUED", "PREVIEW_RUNNING", "INITIALIZE_QUEUED", "INITIALIZING", "SYNC_QUEUED", "SYNCING").contains(c.getState()); }
  public synchronized GenSplitwiseSettings save(GenSplitwiseSettingsInput input) {
    update(c -> {
      require(!busy(c), "Wait for the current synchronization to finish");
      version(input.getRevision(), c.getRevision());
      var previous = settings(c);
      var next = empty().groupId(input.getGroupId()).enabled(Boolean.TRUE.equals(input.getEnabled())).members(input.getMembers()).categories(input.getCategories());
      require(next.getMembers() != null && next.getCategories() != null, "Mappings are required");
      if (c.isInitialized()) require(Objects.equals(previous.getGroupId(), next.getGroupId())
          && json.writeValueAsString(previous.getMembers()).equals(json.writeValueAsString(next.getMembers())),
          "Initialized group and account/start-date mappings are fixed; category mappings and automatic sync remain editable");
      boolean replacement = input.getApiKey() != null && !input.getApiKey().isBlank();
      boolean credentialsOnly = (replacement || Boolean.TRUE.equals(input.getRemoveKey()))
          && Objects.equals(previous.getGroupId(), next.getGroupId()) && previous.getMembers().equals(next.getMembers()) && previous.getCategories().equals(next.getCategories());
      require(!(replacement && Boolean.TRUE.equals(input.getRemoveKey())), "Choose replacement or removal");
      if (replacement) {
        require(input.getApiKey().length() <= 2048 && !input.getApiKey().matches(".*\\s.*"), "Invalid API key");
        configuration.save(ConfigurationEntity.builder().configKey(ConfigurationKey.SPLITWISE_API_KEY).configValue(input.getApiKey()).build());
        c.setTested(false); next.enabled(false);
      }
      if (Boolean.TRUE.equals(input.getRemoveKey())) { configuration.deleteById(ConfigurationKey.SPLITWISE_API_KEY); c.setTested(false); next.enabled(false); }
      if (next.getGroupId() != null && !credentialsOnly) validateMappings(next, c);
      else if (next.getGroupId() == null) require(next.getMembers().isEmpty(), "Select a group before mapping members");
      require(!Boolean.TRUE.equals(next.getEnabled()) || c.isInitialized() && c.isTested() && !key().isBlank(), "Complete the first import and connection test before enabling automatic synchronization");
      c.setSettingsJson(json.writeValueAsString(next)); c.setRevision(c.getRevision() + 1);
      c.setPreviewJson(null); c.setPreviewId(null); c.setSnapshotJson(null); c.setError(null); c.setRetryAt(null); c.setState("IDLE");
    });
    return settings();
  }
  private void validateMappings(GenSplitwiseSettings s, SplitwiseConnection c) {
    require(c.getCatalogJson() != null, "Test the connection before selecting a group");
    var catalog = json.readValue(c.getCatalogJson(), GenSplitwiseCatalog.class);
    var group = catalog.getGroups().stream().filter(g -> g.getId().equals(s.getGroupId())).findFirst().orElseThrow(() -> missing("Accessible group"));
    var seen = new HashSet<Long>(); var users = new HashSet<Long>();
    for (var m : s.getMembers()) {
      require(m.getMemberId() != null && seen.add(m.getMemberId()) && group.getMembers().stream().anyMatch(v -> v.getId().equals(m.getMemberId())), "Choose each group member once");
      require(m.getAccountId() != null && m.getBankAccountId() != null, "Select existing virtual and bank accounts");
      var own = accounts.findById(m.getAccountId()).orElseThrow(() -> missing("Account"));
      var bank = accounts.findById(m.getBankAccountId()).orElseThrow(() -> missing("Bank account"));
      require(own.getKind() == AccountKind.CASH && bank.getKind() == AccountKind.CASH && own.isActive() && bank.isActive() && !own.isDeleted() && !bank.isDeleted()
          && own.getUserId() != null && Objects.equals(own.getUserId(), bank.getUserId()) && !own.getId().equals(bank.getId()) && own.getCurrency().equals(bank.getCurrency())
          && users.add(own.getUserId()), "Select different active CASH accounts with the same owner and currency; map each Thereabout user once");
      if (m.getStartDate() != null && !m.getStartDate().isBlank()) LocalDate.parse(m.getStartDate()); else m.setStartDate(null);
    }
    validateCategories(s.getCategories(), catalog);
  }
  private void validateCategories(List<GenSplitwiseCategoryMapping> mappings, GenSplitwiseCatalog catalog) {
    var seen = new HashSet<Long>();
    for (var mapping : mappings) {
      require(mapping.getSourceCategoryId() != null && seen.add(mapping.getSourceCategoryId())
          && catalog.getSourceCategories().stream().anyMatch(v -> v.getId().equals(mapping.getSourceCategoryId())), "Choose each Splitwise category once");
      if (mapping.getCategoryId() != null) require(!reads.category(mapping.getCategoryId()).getDeleted(), "Choose an active finance category");
    }
  }
  public synchronized GenSplitwiseSettings saveCategories(GenSplitwiseCategorySaveInput input) {
    return writes.write(users.integration(), "splitwise.categories.save", input.getRequestKey(), input, GenSplitwiseSettings.class, () -> {
      var c = connection();
      require(!busy(c), "Wait for the current synchronization to finish");
      version(input.getRevision(), c.getRevision());
      require(c.getCatalogJson() != null, "Test the connection before mapping categories");
      require(input.getCategories() != null, "Mappings are required");
      validateCategories(input.getCategories(), json.readValue(c.getCatalogJson(), GenSplitwiseCatalog.class));
      var next = settings(c); var before = List.copyOf(next.getCategories());
      next.setCategories(input.getCategories());
      c.setSettingsJson(json.writeValueAsString(next)); c.setRevision(c.getRevision() + 1);
      c.setPreviewJson(null); c.setPreviewId(null); c.setSnapshotJson(null);
      if ("PREVIEW_READY".equals(c.getState())) c.setState("IDLE");
      connections.saveAndFlush(c);
      writes.audit(users.integration(), "splitwise.categories.save", null, before, next.getCategories());
      return settings(c);
    });
  }
  public synchronized GenSplitwiseCatalog test() {
    require(!busy(connection()), "Wait for the current synchronization to finish");
    require(!key().isBlank(), "Save an API key first");
    try {
      var catalog = client.catalog(key());
      var current = settings();
      if (current.getGroupId() != null) require(catalog.getGroups().stream().anyMatch(g -> g.getId().equals(current.getGroupId())), "This API key cannot access the configured group");
      update(c -> { c.setCatalogJson(json.writeValueAsString(catalog)); c.setTested(true); c.setError(null); c.setRetryAt(null); c.setFailures(0); c.setState("IDLE"); });
      return catalog();
    } catch (SplitwiseClient.RemoteFailure e) { fail(e); throw bad("Splitwise connection failed (HTTP " + e.status + "). Check the API key and access."); }
  }
  public GenSplitwiseCatalog catalog() {
    var c = connection();
    var catalog = c.getCatalogJson() == null ? new GenSplitwiseCatalog().groups(new ArrayList<>()).sourceCategories(new ArrayList<>()) : json.readValue(c.getCatalogJson(), GenSplitwiseCatalog.class);
    var own = new ArrayList<GenFinanceAccount>();
    var context = ledger.context();
    for (var user : reads.users()) for (var account : reads.ownAccounts(new UserId(user.getId())))
      if (account.getKind() == GenFinanceAccountKind.CASH && Boolean.TRUE.equals(account.getActive()))  {
        context.existing().stream().filter(v -> !v.deleted() && v.contains(account.getId()) && !v.date().isAfter(now()))
            .map(v -> v.date().toLocalDate()).max(Comparator.naturalOrder()).ifPresent(date -> account.setSuggestedStartDate(date.plusDays(1).toString()));
        own.add(account);
      }
    return catalog.accounts(own).categories(reads.categories().getItems());
  }
  public GenSplitwiseStatus status() {
    var c = connection();
    var rows = settings(c).getGroupId() == null ? List.<GenSplitwiseRow>of() : sources.findByGroupId(settings(c).getGroupId()).stream()
        .filter(s -> Set.of("PENDING", "UNAVAILABLE").contains(s.getState())).filter(s -> s.getRowJson() != null)
        .map(s -> json.readValue(s.getRowJson(), GenSplitwiseRow.class).action(s.getState()).message(s.getMessage()).classification(s.getClassification())
            .transactionId(s.getTransactionId() != null ? s.getTransactionId() : json.readValue(s.getRowJson(), GenSplitwiseRow.class).getTransactionId())).toList();
    return new GenSplitwiseStatus().state(c.getState()).error(c.getError()).lastSuccess(string(c.getLastSuccess())).retryAt(string(c.getRetryAt()))
        .cursor(string(c.getCursorAt())).processed(c.getProcessed()).rows(rows)
        .preview(c.isInitialized() || c.getPreviewJson() == null ? null : json.readValue(c.getPreviewJson(), GenSplitwisePreview.class));
  }
  private String string(LocalDateTime value) { return value == null ? null : value.toString(); }
  private void ready() {
    var c = connection(); var s = settings(c);
    require(c.isTested() && !key().isBlank() && s.getGroupId() != null && !s.getMembers().isEmpty(), "Test the connection and configure at least one group member");
  }
  public synchronized GenSplitwiseStatus preview() { ready(); update(c -> { require(!busy(c), "A synchronization is already running"); c.setState("PREVIEW_QUEUED"); c.setRetryAt(null); c.setProcessed(0); c.setError(null); }); return status(); }
  public synchronized GenSplitwiseStatus initialize(GenSplitwiseInitializeInput input) {
    required(input.getRequestKey(), "requestKey"); require(input.getRequestKey().length() <= 100, "requestKey is too long");
    update(c -> {
      if (Objects.equals(c.getInitializeKey(), input.getRequestKey())) {
        require(Objects.equals(c.getPreviewId(), input.getPreviewId()) && Objects.equals(c.getCorrectionMembersJson(), json.writeValueAsString(input.getCorrectionMembers())), "requestKey was already used for another initialization"); return;
      }
      require(!c.isInitialized() && "PREVIEW_READY".equals(c.getState()) && Objects.equals(c.getPreviewId(), input.getPreviewId())
          && c.getPreviewExpires().isAfter(now()), "Create a fresh first-import preview");
      require(Objects.equals(c.getLedgerHash(), ledger.contextHash(ledger.context())), "Local transactions changed; create a new preview");
      var p = json.readValue(c.getPreviewJson(), GenSplitwisePreview.class);
      for (var member : input.getCorrectionMembers()) require(p.getBalances().stream().anyMatch(b -> b.getMemberId().equals(member) && Boolean.TRUE.equals(b.getCanCorrect())), "Review starting-balance coverage before confirming a correction");
      c.setInitializeKey(input.getRequestKey()); c.setCorrectionMembersJson(json.writeValueAsString(input.getCorrectionMembers())); c.setState("INITIALIZE_QUEUED"); c.setProcessed(0); c.setError(null); c.setRetryAt(null);
    }); return status();
  }
  public synchronized GenSplitwiseStatus sync() { ready(); require(connection().isInitialized(), "Confirm the first import first");
    update(c -> { require(c.getRetryAt() == null || !c.getRetryAt().isAfter(now()), "Waiting for Splitwise retry time"); if (!busy(c)) { c.setState("SYNC_QUEUED"); c.setProcessed(0); c.setError(null); } }); return status(); }
  public synchronized GenSplitwiseSourceDetail resolve(GenSplitwiseResolveInput input) {
    require(!busy(connection()), "Wait for the current synchronization to finish");
    return ledger.resolve(input, settings());
  }
  @Scheduled(fixedDelay = 1000, initialDelay = 15000)
  public void work() { executeWork(false); }
  private void executeWork(boolean explicit) {
    if ((!explicit && !workerEnabled) || !running.compareAndSet(false, true)) return;
    try { performWork(); } finally { running.set(false); }
  }
  private void performWork() {
    GenSplitwiseSettings s; boolean preview; boolean initial;
    synchronized (this) {
    var c = connection(); s = settings(c);
    if (!c.isTested() || key().isBlank() || c.getRetryAt() != null && c.getRetryAt().isAfter(now())) return;
    preview = Set.of("PREVIEW_QUEUED", "PREVIEW_RUNNING").contains(c.getState());
    initial = Set.of("INITIALIZE_QUEUED", "INITIALIZING").contains(c.getState());
    boolean due = c.isInitialized() && Boolean.TRUE.equals(s.getEnabled()) && (c.getLastSuccess() == null || c.getLastSuccess().plusMinutes(5).isBefore(now()));
    if (!preview && !initial && !due && !Set.of("SYNC_QUEUED", "SYNCING").contains(c.getState())) return;
    update(v -> v.setState(initial ? "INITIALIZING" : preview ? "PREVIEW_RUNNING" : "SYNCING"));
    }
    try {
      if (preview) makePreview(s);
      else if (initial) firstImport(s);
      else synchronize(s);
    } catch (SplitwiseClient.RemoteFailure failure) { fail(failure); }
    catch (RuntimeException failure) { log.error("Splitwise ledger synchronization failed", failure); update(v -> { v.setError("Synchronization stopped. Review configuration and pending entries; retry safely with Sync now."); v.setState(initial ? "INITIALIZE_QUEUED" : preview ? "PREVIEW_QUEUED" : "SYNC_QUEUED"); v.setRetryAt(now().plusMinutes(5)); }); }
  }
  // Called explicitly by isolated integration tests; it does not depend on application scheduling.
  public void runPending() { executeWork(true); }
  private List<SplitwiseExpense> decode(String snapshot) { return Arrays.asList(json.readValue(snapshot, SplitwiseExpense[].class)); }
  private void makePreview(GenSplitwiseSettings settings) {
    var at = now(); update(c -> c.setState("PREVIEW_RUNNING"));
    var expenses = client.expenses(key(), settings.getGroupId(), null, instant(at));
    var context = ledger.context(); var rows = new ArrayList<GenSplitwiseRow>();
    for (var expense : expenses) for (var member : settings.getMembers()) rows.add(ledger.plan(expense, member, settings, true, context));
    var balances = new ArrayList<GenSplitwiseBalance>();
    for (var member : settings.getMembers()) {
      var own = new UserId(accounts.findById(member.getAccountId()).orElseThrow().getUserId());
      var account = reads.account(own, member.getAccountId());
      BigDecimal total = expenses.stream().filter(e -> e.deletedAt() == null && !e.occurredAt().isAfter(at) && account.getCurrency().equals(e.currency()))
          .map(e -> e.amount(member.getMemberId())).reduce(BigDecimal.ZERO, BigDecimal::add);
      var local = reads.balance(own, account.getId(), at);
      var projected = local;
      for (var row : rows) if (row.getMemberId().equals(member.getMemberId()) && !row.getFuture() && row.getAction().equals("CREATE")) projected = projected.add(decimal(row.getAmount()));
      BigDecimal correction = BigDecimal.ZERO;
      boolean canCorrect = member.getStartDate() != null && rows.stream().noneMatch(r -> r.getMemberId().equals(member.getMemberId()) && r.getTransactionId() != null && r.getAction().equals("PENDING"));
      if (canCorrect) {
        var boundary = LocalDate.parse(member.getStartDate()).atStartOfDay();
        var oldSource = expenses.stream().filter(e -> e.deletedAt() == null && e.occurredAt().isBefore(boundary) && account.getCurrency().equals(e.currency()))
            .map(e -> e.amount(member.getMemberId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        correction = oldSource.subtract(reads.balance(own, account.getId(), boundary.minusNanos(1000)));
      }
      balances.add(new GenSplitwiseBalance().memberId(member.getMemberId()).name(account.getUserName()).currency(account.getCurrency())
          .sourceBalance(money(total)).localBalance(money(local)).projectedBalance(money(projected)).correctionDelta(money(correction)).canCorrect(canCorrect));
    }
    var p = new GenSplitwisePreview().id(UUID.randomUUID().toString()).revision(settings.getRevision()).expiresAt(at.plusDays(1).toString()).rows(rows).balances(balances);
    update(c -> {
      c.setPreviewId(p.getId()); c.setPreviewExpires(at.plusDays(1)); c.setPreviewJson(json.writeValueAsString(p));
      c.setSnapshotJson(json.writeValueAsString(expenses)); c.setLedgerHash(ledger.contextHash(context)); c.setState("PREVIEW_READY"); c.setError(null);
      // Persist reviewable source identities without creating any financial postings.
      for (var row : rows) if (row.getAction().equals("PENDING")) {
        var id = SplitwiseExpense.reference(row.getExpenseId(), row.getMemberId());
        var source = sources.findById(id).orElseGet(SplitwiseSource::new);
        source.setId(id); source.setExpenseId(row.getExpenseId()); source.setMemberId(row.getMemberId()); source.setGroupId(settings.getGroupId()); source.setAccountId(row.getAccountId());
        source.setSourceJson(json.writeValueAsString(expenses.stream().filter(e -> e.id() == row.getExpenseId()).findFirst().orElseThrow()));
        source.setState("PENDING"); source.setMessage(row.getMessage()); source.setRowJson(json.writeValueAsString(row)); sources.save(source);
      }
    });
  }
  private void firstImport(GenSplitwiseSettings settings) {
    var c = connection(); update(v -> v.setState("INITIALIZING"));
    var preview = json.readValue(c.getPreviewJson(), GenSplitwisePreview.class);
    var context = ledger.approvedVersions(ledger.context(), preview.getRows());
    for (var expense : decode(c.getSnapshotJson())) { ledger.apply(expense, settings, true, context); update(v -> v.setProcessed(v.getProcessed() + 1)); }
    var corrections = Arrays.asList(json.readValue(c.getCorrectionMembersJson(), Long[].class));
    for (var balance : preview.getBalances()) if (corrections.contains(balance.getMemberId())) ledger.correct(balance,
        settings.getMembers().stream().filter(m -> m.getMemberId().equals(balance.getMemberId())).findFirst().orElseThrow(), c.getInitializeKey());
    update(v -> { v.setInitialized(true); v.setCursorAt(c.getPreviewExpires().minusDays(1)); v.setFullSyncAt(c.getPreviewExpires().minusDays(1)); finish(v); });
  }
  private void synchronize(GenSplitwiseSettings settings) {
    var at = now(); var c = connection(); update(v -> v.setState("SYNCING"));
    boolean full = c.getFullSyncAt() == null || c.getFullSyncAt().plusDays(1).isBefore(at);
    var downloaded = client.expenses(key(), settings.getGroupId(), full || c.getCursorAt() == null ? null : instant(c.getCursorAt().minusMinutes(10)), instant(at));
    var expenses = new LinkedHashMap<Long,SplitwiseExpense>(); downloaded.forEach(e -> expenses.put(e.id(), e));
    var known = sources.findByGroupId(settings.getGroupId());
    if (full) {
      var checked = new HashSet<Long>();
      for (var source : known) if (!expenses.containsKey(source.getExpenseId()) && !source.getState().equals("IGNORED") && checked.add(source.getExpenseId())) {
        try { var expense = client.expense(key(), source.getExpenseId()); expenses.put(expense.id(), expense); }
        catch (SplitwiseClient.RemoteFailure failure) {
          if (failure.status != 404) throw failure;
          transactions.executeWithoutResult(t -> { for (var absent : sources.findByGroupId(settings.getGroupId())) if (absent.getExpenseId() == source.getExpenseId()) {
            absent.setState("UNAVAILABLE"); absent.setMessage("Source is unavailable; existing postings were preserved."); absent.setAppliedHash(null); sources.save(absent);
          }});
        }
      }
      var catalog = client.catalog(key()); update(v -> v.setCatalogJson(json.writeValueAsString(catalog)));
    }
    for (var source : known) if (source.getState().equals("PENDING")) expenses.putIfAbsent(source.getExpenseId(), json.readValue(source.getSourceJson(), SplitwiseExpense.class));
    // Stage the complete download before advancing the cursor; interrupted runs replay through stable source identities.
    update(v -> { v.setSnapshotJson(json.writeValueAsString(expenses.values())); v.setProcessed(0); });
    var context = ledger.context();
    for (var expense : expenses.values()) { ledger.apply(expense, settings, false, context); update(v -> v.setProcessed(v.getProcessed() + 1)); }
    update(v -> { v.setCursorAt(at); if (full) v.setFullSyncAt(at); finish(v); });
  }
  private void finish(SplitwiseConnection c) { c.setState("IDLE"); c.setError(null); c.setRetryAt(null); c.setFailures(0); c.setLastSuccess(now()); }
  private void fail(SplitwiseClient.RemoteFailure failure) {
    update(c -> {
      c.setFailures(c.getFailures() + 1);
      boolean auth = failure.status == 401 || failure.status == 403;
      if (auth) { c.setTested(false); c.setState("AUTH_ERROR"); c.setError("Splitwise access was rejected. Update or test the credentials."); }
      else {
        long delay = Math.min(3600, 30L << Math.min(c.getFailures(), 7));
        var retry = failure.retryAfter == null ? Duration.ofSeconds(delay + ThreadLocalRandom.current().nextLong(15)) : failure.retryAfter;
        c.setRetryAt(now().plus(retry)); c.setError("Splitwise is temporarily unavailable (HTTP " + failure.status + "). Retrying with backoff.");
      }
    });
  }
  private com.sixtymeters.thereabout.config.ThereaboutException bad(String message) { return new com.sixtymeters.thereabout.config.ThereaboutException(org.springframework.http.HttpStatus.BAD_REQUEST, message); }
}
