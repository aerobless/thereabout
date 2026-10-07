package com.sixtymeters.thereabout.finance.splitwise;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;
import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class SplitwiseLedger {
  private final SplitwiseSourceRepository sources;
  private final SplitwiseSources sourceReads;
  private final SplitwiseConnectionRepository connections;
  private final FinanceTransactionRepository transactions;
  private final com.sixtymeters.thereabout.finance.service.CounterpartyService counterparties;
  private final FinanceAccountRepository accountRepository;
  private final FinanceReadRepository reads;
  private final TransactionService ledger;
  private final AccountService accounts;
  private final CategoryService categories;
  private final FinanceWriteCoordinator writes;
  private final ObjectMapper json;
  private final Clock financeClock;
  private static final Pattern REFERENCE = Pattern.compile("splitwise:[0-9]+:user:[0-9]+(?![0-9])");
  private static final Pattern SETTLEMENT = Pattern.compile("(?i)(spliti|splitti|splitwise).*(sync|ausgleich|usglich|usglich)|(ausgleich|usglich).*splitt?i|back on splitti");
  public record Leg(long account, BigDecimal amount, String currency) {}
  public record Existing(long id, long version, boolean deleted, TransactionType type, LocalDateTime date,
      String description, String reference, String notes, String metadata, List<Leg> legs) {
    BigDecimal amount(long account) { return legs.stream().filter(p -> p.account == account).map(Leg::amount).findFirst().orElse(BigDecimal.ZERO); }
    boolean contains(long account) { return legs.stream().anyMatch(p -> p.account == account); }
    boolean currency(String currency) { return legs.stream().allMatch(p -> p.currency.equals(currency)); }
  }
  public record Context(List<Existing> existing, Map<String,List<Existing>> references) {}

  @Transactional(readOnly = true)
  public Context context() {
    var existing = transactions.findAllWithPostings().stream().map(t -> new Existing(t.getId(), t.getVersion(), t.isDeleted(),
        t.getType(), t.getOccurredAt(), t.getDescription(), t.getExternalReference(), t.getNotes(), t.getMetadata(),
        t.getPostings().stream().map(p -> new Leg(p.getAccountId(), p.getAmount(), p.getCurrency())).toList())).toList();
    return context(existing);
  }
  private Context context(List<Existing> existing) {
    var references = new HashMap<String,List<Existing>>();
    for (var t : existing) {
      var matcher = REFERENCE.matcher(Objects.toString(t.reference, "") + " " + Objects.toString(t.notes, "") + " " + Objects.toString(t.metadata, ""));
      var seen = new HashSet<String>();
      while (matcher.find()) if (seen.add(matcher.group())) references.computeIfAbsent(matcher.group(), k -> new ArrayList<>()).add(t);
    }
    return new Context(existing, references);
  }
  public Context approvedVersions(Context current, List<GenSplitwiseRow> approved) {
    var versions = new HashMap<Long,Long>();
    for (var row : approved) if (row.getTransactionId() != null && row.getTransactionVersion() != null) versions.put(row.getTransactionId(), row.getTransactionVersion());
    return context(current.existing.stream().map(t -> new Existing(t.id, versions.getOrDefault(t.id, t.version), t.deleted, t.type,
        t.date, t.description, t.reference, t.notes, t.metadata, t.legs)).toList());
  }
  public String contextHash(Context context) { return hash(json.writeValueAsString(context.existing)); }
  public static String hash(String text) {
    try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  public GenSplitwiseRow plan(SplitwiseExpense e, GenSplitwiseMemberMapping m, GenSplitwiseSettings settings, boolean initial, Context context) {
    var source = sources.findById(SplitwiseExpense.reference(e.id(), m.getMemberId())).orElse(null);
    var amount = e.amount(m.getMemberId());
    var row = new GenSplitwiseRow().expenseId(e.id()).memberId(m.getMemberId()).accountId(m.getAccountId())
        .description(Objects.toString(e.description(), "Splitwise expense " + e.id())).date(e.occurredAt().toString())
        .amount(money(amount)).currency(e.currency()).sourceCategoryId(e.category() == null ? null : e.category().id())
        .future(e.occurredAt().isAfter(LocalDateTime.now(financeClock)))
        .requiresClassification(!e.payment() && (source == null || source.getClassification() == null) && SETTLEMENT.matcher(Objects.toString(e.description(), "")).find()).action("CREATE");
    if (source != null) {
      row.setTransactionId(source.getTransactionId());
      if (source.isLocallyManaged()) return action(row, "LOCAL", "Managed locally; source changes will not be applied.");
      if ("IGNORED".equals(source.getState())) return action(row, "IGNORED", "Historical entry preserved.");
      if (source.getTransactionId() != null) {
        var t = transactions.findById(source.getTransactionId()).orElse(null);
        if (t == null || !Objects.equals(source.getAppliedVersion(), t.getVersion())) return action(row, "LOCAL", "Local transaction changed since synchronization.");
      }
      if (Objects.equals(source.getAppliedHash(), hash(json.writeValueAsString(e)))) return action(row, "UNCHANGED", null);
    }
    if (e.deletedAt() != null || e.groupId() != settings.getGroupId()) return action(row, "SOURCE_DELETED", null);
    if (amount.signum() == 0) return action(row, "ZERO", null);
    var own = accountRepository.findById(m.getAccountId()).orElse(null);
    var bank = accountRepository.findById(m.getBankAccountId()).orElse(null);
    if (!valid(own) || !valid(bank) || !Objects.equals(own.getUserId(), bank.getUserId()) || own.getId().equals(bank.getId())
        || !own.getCurrency().equals(e.currency()) || !bank.getCurrency().equals(e.currency())) return action(row, "PENDING", "Choose active accounts with matching owner and currency.");
    var exact = context.references.getOrDefault(SplitwiseExpense.reference(e.id(), m.getMemberId()), List.of());
    if ((source == null || source.getTransactionId() == null) && !exact.isEmpty()) {
      if (exact.size() != 1 || !exact.getFirst().contains(m.getAccountId())) return action(row, "PENDING", "Source reference is ambiguous or belongs to another account.");
      var t = exact.getFirst(); row.setTransactionId(t.id); row.setTransactionVersion(t.version);
      if (sources.findByTransactionId(t.id).stream().anyMatch(s -> !s.getId().equals(SplitwiseExpense.reference(e.id(), m.getMemberId()))))
        return action(row, "PENDING", "This transaction is already linked to another Splitwise member.");
      if (t.deleted) return action(row, "LOCAL", "Existing deleted entry is preserved.");
      if (t.type == TransactionType.TRANSFER && !t.contains(m.getBankAccountId())) return action(row, "PENDING", "Existing settlement uses a different bank account; preserve or review its mapping.");
      if (t.amount(m.getAccountId()).compareTo(amount) != 0 || !t.currency(e.currency()) || !t.date.toLocalDate().equals(e.occurredAt().toLocalDate()))
        return action(row, "PENDING", "Existing source reference differs; review instead of overwriting.");
      return action(row, "ADOPT", null);
    }
    if (source == null && initial && m.getStartDate() != null && !m.getStartDate().isBlank()
        && e.occurredAt().toLocalDate().isBefore(LocalDate.parse(m.getStartDate()))) return action(row, "IGNORED", "Before the selected start date.");
    boolean settlement = isSettlement(e, source);
    if (settlement) {
      if (source != null && "CREATE_SETTLEMENT".equals(source.getClassification())) return action(row, "CREATE", null);
      if (source != null && source.getTransactionId() != null) return action(row, "UPDATE", null);
      var matches = context.existing.stream().filter(t -> !t.deleted && t.type == TransactionType.TRANSFER
          && t.contains(m.getAccountId()) && t.contains(m.getBankAccountId()) && t.currency(e.currency())
          && t.amount(m.getAccountId()).compareTo(amount) == 0 && t.date.toLocalDate().equals(e.occurredAt().toLocalDate())).toList();
      if (matches.size() == 1 && sources.findByTransactionId(matches.getFirst().id).isEmpty()) return action(row.transactionId(matches.getFirst().id).transactionVersion(matches.getFirst().version), "ADOPT", null);
      return action(row, "PENDING", "Settlement: link a bank transaction or explicitly create the transfer.");
    }
    if ((source == null || source.getClassification() == null) && SETTLEMENT.matcher(row.getDescription()).find()) return action(row, "PENDING", "Possible settlement recorded as an expense. Choose Expense or Settlement.");
    if ((source == null || !"CREATE_EXPENSE".equals(source.getClassification())) && context.existing.stream().anyMatch(t -> !t.deleted && (source == null || !Objects.equals(t.id, source.getTransactionId())) && t.contains(m.getAccountId())
        && t.amount(m.getAccountId()).compareTo(amount) == 0 && t.currency(e.currency())
        && t.date.toLocalDate().equals(e.occurredAt().toLocalDate()) && !REFERENCE.matcher(Objects.toString(t.reference, "")).find()))
      return action(row, "PENDING", "Possible historical duplicate: same account, date and amount.");
    var mapping = settings.getCategories().stream().filter(c -> e.category() != null && c.getSourceCategoryId() == e.category().id()).findFirst();
    if (mapping.isEmpty()) return action(row, "PENDING", "Map the Splitwise category before importing.");
    try { categories.validate(mapping.get().getCategoryId()); }
    catch (RuntimeException failure) { return action(row, "PENDING", "The mapped category is no longer available."); }
    return action(row, source != null && source.getTransactionId() != null ? "UPDATE" : "CREATE", null);
  }
  private GenSplitwiseRow action(GenSplitwiseRow row, String action, String message) { return row.action(action).message(message); }
  private boolean valid(FinanceAccountEntity a) { return a != null && !a.isDeleted() && a.isActive() && a.getKind() == AccountKind.CASH && a.getUserId() != null; }
  private boolean isSettlement(SplitwiseExpense e, SplitwiseSource source) {
    if (source != null && Set.of("EXPENSE", "CREATE_EXPENSE").contains(Objects.toString(source.getClassification(), ""))) return false;
    return e.payment() || (source != null && Set.of("SETTLEMENT", "CREATE_SETTLEMENT").contains(Objects.toString(source.getClassification(), "")));
  }

  @Transactional
  public void apply(SplitwiseExpense e, GenSplitwiseSettings settings, boolean initial, Context context) {
    var user = owner(settings.getMembers().getFirst());
    writes.write(user, "splitwise.apply", UUID.randomUUID().toString(), e, GenFinanceBulkResult.class, () -> {
      // Both user projections commit together, and local overrides are rechecked under the same write lock.
      for (var member : settings.getMembers()) applyMember(e, member, settings, initial, context);
      return new GenFinanceBulkResult().updated(1);
    });
  }
  private void applyMember(SplitwiseExpense e, GenSplitwiseMemberMapping m, GenSplitwiseSettings settings, boolean initial, Context context) {
    var row = plan(e, m, settings, initial, context);
    var id = SplitwiseExpense.reference(e.id(), m.getMemberId());
    var source = sources.findById(id).orElseGet(SplitwiseSource::new);
    var previousSource = source.getSourceJson();
    source.setId(id); source.setExpenseId(e.id()); source.setMemberId(m.getMemberId()); source.setGroupId(settings.getGroupId());
    source.setAccountId(m.getAccountId()); source.setSourceJson(json.writeValueAsString(e)); source.setMessage(row.getMessage());
    var user = owner(m);
    switch (row.getAction()) {
      case "LOCAL" -> { source.setLocallyManaged(true); source.setTransactionId(row.getTransactionId()); }
      case "ADOPT" -> {
        var existing = transactions.findById(row.getTransactionId()).orElseThrow();
        var expected = context.existing.stream().filter(t -> t.id == existing.getId()).findFirst().orElseThrow();
        if (existing.isDeleted() || existing.getVersion() != expected.version) { source.setLocallyManaged(true); row.action("LOCAL"); }
        source.setTransactionId(existing.getId()); source.setAppliedVersion(existing.getVersion());
        if (existing.getType() == TransactionType.TRANSFER) source.setClassification("SETTLEMENT");
      }
      case "ZERO", "SOURCE_DELETED" -> {
        if (source.getTransactionId() != null) {
          var t = transactions.findById(source.getTransactionId()).orElseThrow();
          if (!t.isDeleted()) ledger.syncSetDeleted(user, versioned(t), true);
          source.setAppliedVersion(t.getVersion());
        }
      }
      case "CREATE", "UPDATE" -> {
        var amount = e.amount(m.getMemberId());
        var t = source.getTransactionId() == null ? null : transactions.findById(source.getTransactionId()).orElseThrow();
        if (t != null && t.isDeleted()) ledger.syncSetDeleted(user, versioned(t), false);
        boolean settlement = isSettlement(e, source);
        long other = settlement ? m.getBankAccountId() : counter(amount.signum() > 0 ? AccountKind.REVENUE : AccountKind.EXPENSE, e.currency());
        var input = input(m.getAccountId(), other, amount, e.currency(), e.occurredAt(), row.getDescription(), settlement);
        input.externalReference(t != null && t.getExternalReference() != null && !t.getExternalReference().isBlank() ? t.getExternalReference() : id)
            .notes(Objects.toString(e.details(), ""));
        if (!settlement) input.categoryId(settings.getCategories().stream().filter(c -> c.getSourceCategoryId().equals(row.getSourceCategoryId())).findFirst().orElseThrow().getCategoryId());
        if (t != null) input.id(t.getId()).version(t.getVersion());
        var after = ledger.syncSave(user, input).getTransaction();
        source.setTransactionId(after.getId()); source.setAppliedVersion(after.getVersion());
      }
      default -> { }
    }
    source.setState(row.getAction().equals("UNCHANGED") ? source.getState() : row.getAction().equals("CREATE") || row.getAction().equals("UPDATE") || row.getAction().equals("ADOPT") ? "ACTIVE" : row.getAction());
    if ("LOCAL".equals(row.getAction()) && previousSource != null && !previousSource.equals(source.getSourceJson()))
      writes.audit(user, "splitwise.source-observed", source.getTransactionId(), json.readTree(previousSource), json.valueToTree(e));
    if (!Set.of("PENDING", "LOCAL").contains(row.getAction())) source.setAppliedHash(hash(source.getSourceJson()));
    source.setRowJson(json.writeValueAsString(row));
    sources.saveAndFlush(source);
  }
  private UserId owner(GenSplitwiseMemberMapping m) { return new UserId(accountRepository.findById(m.getAccountId()).orElseThrow(() -> missing("Account")).getUserId()); }
  private GenFinanceVersionedInput versioned(FinanceTransactionEntity t) { return new GenFinanceVersionedInput().id(t.getId()).version(t.getVersion()).requestKey(UUID.randomUUID().toString()); }
  private long counter(AccountKind kind, String currency) {
    String name = kind == AccountKind.REVENUE ? "Splitwise reimbursements" : "Splitwise shared expenses";
    return accountRepository.findFirstByNameAndKindAndCurrencyAndDeletedFalseOrderByIdAsc(name, kind, currency).map(FinanceAccountEntity::getId).orElseGet(() -> {
      var a = new FinanceAccountEntity(); a.setName(name); a.setKind(kind); a.setCurrency(currency); counterparties.attach(a); return accountRepository.saveAndFlush(a).getId();
    });
  }
  private GenFinanceTransactionInput input(long own, long other, BigDecimal amount, String currency, LocalDateTime date, String description, boolean settlement) {
    boolean positive = amount.signum() > 0;
    return new GenFinanceTransactionInput().requestKey(UUID.randomUUID().toString()).description(description).date(date.toString())
        .type(settlement ? GenFinanceTransactionType.TRANSFER : positive ? GenFinanceTransactionType.DEPOSIT : GenFinanceTransactionType.WITHDRAWAL)
        .effect(!settlement && positive ? GenFinanceEffect.EXPENSE_REIMBURSEMENT : GenFinanceEffect.OPERATING)
        .sourceId(positive ? other : own).destinationId(positive ? own : other)
        .sourceAmount(money(amount.abs())).destinationAmount(money(amount.abs())).sourceCurrency(currency).destinationCurrency(currency);
  }
  @Transactional
  public GenSplitwiseSourceDetail resolve(GenSplitwiseResolveInput choice, GenSplitwiseSettings settings) {
    return writes.write(owner(settings.getMembers().getFirst()), "splitwise.resolve", required(choice.getRequestKey(), "requestKey"), choice, GenSplitwiseSourceDetail.class, () -> {
      var source = sources.findById(SplitwiseExpense.reference(choice.getExpenseId(), choice.getMemberId())).orElseThrow(() -> missing("Source entry"));
      version(choice.getSourceVersion(), source.getVersion());
      require(source.getGroupId() == settings.getGroupId(), "Source entry does not belong to the configured group");
      var before = sourceReads.view(source);
      var e = json.readValue(source.getSourceJson(), SplitwiseExpense.class);
      var m = settings.getMembers().stream().filter(v -> v.getMemberId().equals(choice.getMemberId())).findFirst().orElseThrow(() -> missing("Mapped member"));
      require(source.getAccountId() == m.getAccountId(), "Source account differs from the member mapping");
      require(choice.getAction() != null, "Choose a review action");
      require(choice.getAction() == GenSplitwiseResolveInput.ActionEnum.LINK_EXISTING || !source.isLocallyManaged() && "PENDING".equals(source.getState()),
          "Only pending source entries can be resolved");
      switch (choice.getAction()) {
        case SKIP -> { source.setLocallyManaged(true); source.setState("LOCAL"); source.setMessage("Explicitly skipped in Thereabout."); }
        case AS_EXPENSE -> source.setClassification("EXPENSE");
        case AS_SETTLEMENT -> source.setClassification("SETTLEMENT");
        case CREATE -> {
          require(source.getTransactionId() == null, "An existing source transaction must not be duplicated");
          require(e.payment() || source.getClassification() != null || !SETTLEMENT.matcher(Objects.toString(e.description(), "")).find(),
              "Classify the historical entry as an expense or settlement first");
          require(context().references.getOrDefault(source.getId(), List.of()).isEmpty(), "Existing source reference must be reviewed, not duplicated");
          source.setClassification(isSettlement(e, source) ? "CREATE_SETTLEMENT" : "CREATE_EXPENSE");
        }
        case LINK -> {
          require(choice.getTransactionId() != null, "Choose a bank transaction");
          var t = transactions.findById(choice.getTransactionId()).orElseThrow(() -> missing("Transaction"));
          version(choice.getTransactionVersion(), t.getVersion());
          require(source.getTransactionId() == null, "An existing source link must not be replaced");
          var amount = e.amount(m.getMemberId());
          var bankLeg = t.getPostings().stream().filter(p -> p.getAccountId().equals(m.getBankAccountId())).findFirst().orElseThrow(() -> missing("Bank posting"));
          require(!t.isDeleted() && t.getOccurredAt().toLocalDate().equals(e.occurredAt().toLocalDate())
              && bankLeg.getAmount().compareTo(amount.negate()) == 0 && bankLeg.getCurrency().equals(e.currency())
              && sources.findByTransactionId(t.getId()).isEmpty(), "Bank transaction does not uniquely match this settlement");
          source.setTransactionId(t.getId()); source.setAppliedVersion(t.getVersion()); source.setClassification("SETTLEMENT");
        }
        case LINK_EXISTING -> {
          require(choice.getTransactionId() != null, "Choose an existing transaction");
          var t = transactions.findById(choice.getTransactionId()).orElseThrow(() -> missing("Transaction"));
          version(choice.getTransactionVersion(), t.getVersion());
          require(source.getTransactionId() == null || source.getTransactionId().equals(t.getId()), "An existing source link must not be replaced");
          require(sources.findByTransactionId(t.getId()).stream().allMatch(s -> s.getId().equals(source.getId())), "Transaction is linked to another Splitwise source");
          var own = accountRepository.findById(m.getAccountId()).orElseThrow(() -> missing("Account"));
          require(own.getUserId() != null, "Virtual account must have an owner");
          var posting = t.getPostings().stream().filter(p -> p.getAccountId().equals(m.getAccountId())).findFirst().orElseThrow(() -> missing("Virtual-account posting"));
          require(posting.getAmount().compareTo(e.amount(m.getMemberId())) == 0 && posting.getCurrency().equals(e.currency()), "Virtual-account amount or currency differs from Splitwise");
          // Linking records provenance only: deliberate legacy dates and deleted local history survive.
          source.setTransactionId(t.getId()); source.setAppliedVersion(t.getVersion()); source.setLocallyManaged(true);
          source.setState("LOCAL"); source.setMessage("Linked manually; managed locally. Automatic updates are disabled.");
          if (t.getType() == TransactionType.TRANSFER) source.setClassification("SETTLEMENT");
        }
      }
      if (!source.isLocallyManaged()) source.setAppliedHash(null);
      sources.saveAndFlush(source);
      connections.findById(1L).filter(c -> !c.isInitialized()).ifPresent(c -> {
        c.setPreviewJson(null); c.setPreviewId(null); c.setSnapshotJson(null); c.setState("IDLE");
        connections.saveAndFlush(c);
      });
      var after = sourceReads.detail(source);
      writes.audit(owner(m), "splitwise.resolve", source.getTransactionId(), before, after.getSource());
      return after;
    });
  }
  @Transactional
  public void correct(GenSplitwiseBalance balance, GenSplitwiseMemberMapping member, String initializationKey) {
    var delta = decimal(balance.getCorrectionDelta());
    if (delta.signum() == 0) return;
    require(Boolean.TRUE.equals(balance.getCanCorrect()), "The starting balance cannot be corrected until its coverage is clear");
    var date = LocalDate.parse(member.getStartDate()).atStartOfDay().minusNanos(1000);
    var input = input(member.getAccountId(), counter(delta.signum() > 0 ? AccountKind.REVENUE : AccountKind.EXPENSE, balance.getCurrency()),
        delta, balance.getCurrency(), date, "Splitwise starting balance correction", false)
        .effect(GenFinanceEffect.RECONCILIATION).externalReference("splitwise-start:" + initializationKey + ":" + member.getMemberId())
        .requestKey("sw-start:" + hash(initializationKey + ":" + member.getMemberId()).substring(0, 40));
    ledger.syncSave(owner(member), input);
  }
}
