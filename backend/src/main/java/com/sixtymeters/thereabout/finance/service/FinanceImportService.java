package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Temporary review state; the coordinator is the sole approval persistence boundary. */
@Service
@RequiredArgsConstructor
public class FinanceImportService {
  private final ImportCsvReader csv;
  private final ImportInterpreter interpreter;
  private final FinanceImportHintService hints;
  private final AccountService accounts;
  private final CategoryService categories;
  private final TransactionService transactions;
  private final FinanceReadRepository reads;
  private final CounterpartyService counterparties;
  private final FinanceAccountRepository accountRepository;
  private final FinanceImportSourceRepository sources;
  private final FinancePostingRepository postings;
  private final com.sixtymeters.thereabout.finance.splitwise.SplitwiseSourceRepository splitwiseSources;
  private final FinanceWriteCoordinator writes;
  private final ObjectMapper json;
  private final Clock financeClock;
  private final com.sixtymeters.thereabout.ai.OpenAiService ai;
  private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(2),
          Thread.ofPlatform().name("finance-import-", 0).factory(),
          new ThreadPoolExecutor.AbortPolicy());

  private static final class Job {
    String id, fileName, currency, error = "";
    UserId caller;
    long accountId, revision = 0;
    volatile GenFinanceImportJob.StatusEnum status = GenFinanceImportJob.StatusEnum.QUEUED;
    volatile Instant expires;
    int processed;
    List<ImportCsvReader.Row> source;
    List<GenFinanceImportRow> rows = new ArrayList<>();
    Map<String, String> fingerprints = new HashMap<>();
    Future<?> task;
  }

  public synchronized GenFinanceImportJob prepare(UserId user, GenFinanceImportPrepareInput input) {
    expire();
    require(ai.settings().getConfigured(), "Configure an OpenAI API key before importing");
    require(jobs.size() < 64, "Too many import drafts; cancel an old import or try later");
    require(
        jobs.values().stream()
            .noneMatch(
                j ->
                    j.caller.equals(user)
                        && (j.status == GenFinanceImportJob.StatusEnum.RUNNING
                            || j.status == GenFinanceImportJob.StatusEnum.QUEUED)),
        "An import is already running for this user");
    var account = accounts.transferAccount(input.getAccountId());
    require(account.isActive() && !account.isDeleted(), "Select an active account");
    String name = required(input.getFileName(), "fileName");
    require(
        name.length() <= 255 && name.toLowerCase(Locale.ROOT).endsWith(".csv"),
        "Select a CSV file");
    var parsed = csv.parse(input.getCsvText());
    var known =
        accountRepository.findAll().stream()
            .filter(
                a ->
                    !a.isDeleted()
                        && a.isActive()
                        && (a.getKind().isOwn() || a.getKind().isCounterparty()))
            .map(
                a ->
                    new GenFinanceAccount()
                        .id(a.getId())
                        .name(a.getName())
                        .kind(GenFinanceAccountKind.fromValue(a.getKind().name()))
                        .currency(a.getCurrency()))
            .toList();
    var categoryList = reads.categories().getItems();
    // A prepare-time snapshot keeps every chunk consistent despite later hint changes.
    var hintSnapshot = hints.snapshot(user, account.getId());
    var job = new Job();
    job.id = UUID.randomUUID().toString();
    job.fileName = name;
    job.caller = user;
    job.accountId = account.getId();
    job.currency = account.getCurrency();
    job.source = parsed;
    job.expires = financeClock.instant().plus(Duration.ofHours(1));
    var occurrences = new HashMap<String, Integer>();
    for (var row : parsed) {
      String value = json.writeValueAsString(row.cells());
      int occurrence = occurrences.merge(value, 1, Integer::sum);
      job.fingerprints.put(row.id(), hash(value + ":" + occurrence));
    }
    jobs.put(job.id, job);
    try {
      job.task = workers.submit(() -> run(job, account, known, categoryList, hintSnapshot));
    } catch (RejectedExecutionException ex) {
      jobs.remove(job.id);
      require(false, "Import workers are busy; try again shortly");
    }
    return view(job, 0, 50);
  }

  private void run(
      Job job,
      FinanceAccountEntity account,
      List<GenFinanceAccount> known,
      List<GenFinanceCategory> categoryList,
      List<String> hintSnapshot) {
    synchronized (job) {
      if (job.status == GenFinanceImportJob.StatusEnum.CANCELLED) return;
      job.status = GenFinanceImportJob.StatusEnum.RUNNING;
    }
    try {
      for (int offset = 0; offset < job.source.size(); offset += 50) {
        synchronized (job) {
          if (job.status == GenFinanceImportJob.StatusEnum.CANCELLED) return;
        }
        var chunk = job.source.subList(offset, Math.min(offset + 50, job.source.size()));
        var response =
            interpreter.interpret(
                account,
                job.source.subList(0, Math.min(20, job.source.size())),
                chunk,
                known,
                categoryList,
                hintSnapshot);
        require(
            response != null && response.rows != null && response.rows.size() == chunk.size(),
            "AI did not cover every source row");
        var proposed = new LinkedHashMap<String, ImportInterpreter.Proposal>();
        for (var p : response.rows)
          require(p != null && proposed.put(p.rowId, p) == null, "Duplicate AI row");
        var converted = new ArrayList<GenFinanceImportRow>();
        for (var source : chunk) {
          var p = proposed.get(source.id());
          require(p != null, "Missing AI row");
          // Classification removes statement structure, never a skipped transaction or duplicate.
          if (p.nonTransaction && p.skip && text(p.type).isEmpty() && text(p.amount).isEmpty())
            continue;
          var row =
              new GenFinanceImportRow()
                  .rowId(source.id())
                  .source(source.cells())
                  .skip(p.skip)
                  .reason(text(p.reason))
                  .date(text(p.date))
                  .description(text(p.description))
                  .amount(text(p.amount))
                  .otherAccountId(p.otherAccountId == null ? null : p.otherAccountId.orElse(null))
                  .counterpartyName(text(p.counterpartyName))
                  .categoryId(p.categoryId == null ? null : p.categoryId.orElse(null))
                  .categoryName(text(p.categoryName))
                  .otherAmount(text(p.otherAmount))
                  .foreignAmount(text(p.foreignAmount))
                  .foreignCurrency(text(p.foreignCurrency))
                  .notes(text(p.notes))
                  .externalReference(text(p.externalReference))
                  .incoming(text(p.notes).startsWith("INCOMING:"))
                  .duplicateOverride(false)
                  .reviewed(false);
          if (!text(p.type).isBlank()) {
            try {
              row.setType(GenFinanceImportRow.TypeEnum.fromValue(p.type));
            } catch (IllegalArgumentException ex) {
              row.setReason("Select a transaction type.");
            }
          }
          if (!p.skip && !text(p.amount).isEmpty()) {
            boolean evidence = supportedAmount(source, p.amountEvidence, p.amount);
            if (!evidence) {
              row.setAmount("");
              row.setReason(
                  "Booked amount is not supported by the source. Enter the booked amount or skip.");
            }
          }
          if (!p.skip) {
            var dateCandidates = ImportCsvReader.dates(text(p.dateEvidence));
            if (!source.cells().contains(p.dateEvidence)
                || dateCandidates.isEmpty()
                || text(p.date).isEmpty()
                || !supportedDate(dateCandidates, p.date)) {
              row.setDate("");
              row.setReason("Date is not supported by the source. Enter the date or skip.");
            } else if (dateCandidates.size() > 1)
              row.setReason("Ambiguous source date; confirm the date.");
            if (!text(p.foreignAmount).isEmpty()
                && !supportedAmount(source, p.foreignAmountEvidence, p.foreignAmount)) {
              row.setForeignAmount("");
              row.setReason("Confirm the original amount from the source.");
            }
            if (row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER
                && known.stream()
                    .noneMatch(
                        a ->
                            Objects.equals(a.getId(), row.getOtherAccountId())
                                && job.currency.equals(a.getCurrency()))
                && !supportedAmount(source, p.otherAmountEvidence, p.otherAmount)) {
              row.setOtherAmount("");
              row.setReason("Enter the booked amount on the other transfer account.");
            }
            if (ImportCsvReader.numbers(text(p.amountEvidence)).size() > 1)
              row.setReason("Ambiguous source number; confirm the booked amount.");
          }
          converted.add(row);
        }
        synchronized (job) {
          if (job.status == GenFinanceImportJob.StatusEnum.CANCELLED) return;
          job.rows.addAll(converted);
          job.processed += chunk.size();
        }
      }
      synchronized (job) {
        if (job.status == GenFinanceImportJob.StatusEnum.CANCELLED) return;
        require(
            job.rows.stream().filter(r -> !Boolean.TRUE.equals(r.getSkip())).count()
                <= ImportCsvReader.MAX_ROWS,
            "CSV exceeds 2,000 data rows");
        validate(job, job.rows, true);
        job.status = GenFinanceImportJob.StatusEnum.READY;
        job.revision++;
        job.expires = financeClock.instant().plus(Duration.ofHours(1));
      }
    } catch (RuntimeException ex) {
      synchronized (job) {
        if (job.status != GenFinanceImportJob.StatusEnum.CANCELLED) {
          job.status = GenFinanceImportJob.StatusEnum.FAILED;
          job.rows.clear();
          job.error =
              "Import could not be prepared. Check the CSV, API key and model, then retry. No"
                  + " transactions were created.";
        }
      }
    }
  }

  public GenFinanceImportJob get(UserId user, GenFinanceImportQuery input) {
    var job = job(user, input.getJobId());
    synchronized (job) {
      return view(job, page(input.getPage()), pageSize(input.getPageSize()));
    }
  }

  public GenFinanceImportJob review(UserId user, GenFinanceImportReviewInput input) {
    var job = job(user, input.getJobId());
    synchronized (job) {
      ready(job, input.getRevision());
      var rows = corrected(job, input.getRows());
      validate(job, rows, false);
      job.rows = rows;
      job.revision++;
      return view(job, 0, 50);
    }
  }

  public GenFinanceImportJob cancel(UserId user, GenFinanceImportQuery input) {
    var job = job(user, input.getJobId());
    synchronized (job) {
      require(job.status != GenFinanceImportJob.StatusEnum.APPROVED, "Import already approved");
      job.status = GenFinanceImportJob.StatusEnum.CANCELLED;
      job.rows.clear();
      if (job.task != null) job.task.cancel(true);
      return view(job, 0, 50);
    }
  }

  public GenFinanceImportApproval approve(UserId user, GenFinanceImportApproveInput input) {
    // Replay uses only the request, so it remains valid after the in-memory draft expires or a
    // restart.
    return writes.write(
        user,
        "imports.approve",
        input.getRequestKey(),
        input,
        GenFinanceImportApproval.class,
        () -> {
          var job = job(user, input.getJobId());
          synchronized (job) {
            ready(job, input.getRevision());
            var rows =
                (input.getRows() == null || input.getRows().isEmpty())
                    ? copy(job.rows)
                    : corrected(job, input.getRows());
            validate(job, rows, false);
            if (rows.stream().anyMatch(r -> !r.getIssues().isEmpty())) {
              // Ledger changes since preview become reviewable without committing any ledger data.
              job.rows = rows;
              job.revision++;
              require(false, "Resolve every row or explicitly skip it before approval");
            }
            var ids = new ArrayList<Long>();
            var newCounters = new HashMap<String, Long>();
            for (var row : rows) {
              if (Boolean.TRUE.equals(row.getSkip())) continue;
              Long other = row.getOtherAccountId();
              if (other == null) {
                String kind =
                    row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT ? "REVENUE" : "EXPENSE";
                String name = required(row.getCounterpartyName(), "counterparty");
                String key = kind + ":" + nameKey(name);
                other = newCounters.get(key);
                if (other == null) {
                  var existing = counterparties.matchingAccounts(name, AccountKind.valueOf(kind), job.currency).stream().findFirst();
                  other =
                      existing
                          .map(FinanceAccountEntity::getId)
                          .orElseGet(
                              () ->
                                  accounts
                                      .save(
                                          user,
                                          new GenFinanceAccountInput()
                                              .requestKey(childKey(input, "counter", key))
                                              .name(name)
                                              .kind(GenFinanceAccountKind.fromValue(kind))
                                              .currency(job.currency))
                                      .getAccount()
                                      .getId());
                  newCounters.put(key, other);
                }
              }
              Long category = row.getCategoryId();
              var tx =
                  transaction(job, row, other, category)
                      .requestKey(childKey(input, "tx", row.getRowId()));
              Long id = transactions.importTransaction(user, tx).getTransaction().getId();
              ids.add(id);
              var provenance = new FinanceImportSourceEntity();
              provenance.setAccountId(job.accountId);
              provenance.setFingerprint(job.fingerprints.get(row.getRowId()));
              provenance.setTransactionId(id);
              provenance.setFileName(job.fileName);
              provenance.setSourceRow(json.writeValueAsString(row));
              provenance.setDuplicateOverride(Boolean.TRUE.equals(row.getDuplicateOverride()));
              sources.save(provenance);
              writes.audit(user, "imports.approve", id, null, row);
            }
            org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        synchronized (job) {
                          job.status = GenFinanceImportJob.StatusEnum.APPROVED;
                        }
                      }
                    });
            return new GenFinanceImportApproval()
                .transactionIds(ids)
                .created(ids.size())
                .skipped(rows.size() - ids.size());
          }
        });
  }

  private boolean supportedAmount(ImportCsvReader.Row source, String evidence, String amount) {
    if (text(amount).isEmpty() || !source.cells().contains(evidence)) return false;
    try {
      var proposed = decimal(amount).abs();
      return ImportCsvReader.numbers(text(evidence)).stream()
          .anyMatch(n -> n.compareTo(proposed) == 0);
    } catch (org.springframework.web.server.ResponseStatusException ex) {
      return false;
    }
  }

  private boolean supportedDate(Set<LocalDate> candidates, String proposed) {
    try {
      return candidates.contains(dateTime(proposed, null, false).toLocalDate());
    } catch (org.springframework.web.server.ResponseStatusException ex) {
      return false;
    }
  }

  private GenFinanceTransactionInput transaction(
      Job job, GenFinanceImportRow row, Long other, Long category) {
    boolean incoming =
        row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT
            || (row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER
                && Boolean.TRUE.equals(row.getIncoming()));
    var otherEntity = accounts.requireAccount(job.caller, other);
    String otherCurrency =
        row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER
            ? otherEntity.getCurrency()
            : job.currency;
    String otherAmount =
        row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER
            ? row.getOtherAmount()
            : row.getAmount();
    return new GenFinanceTransactionInput()
        .type(GenFinanceTransactionType.fromValue(row.getType().getValue()))
        .date(row.getDate())
        .description(row.getDescription())
        .sourceId(incoming ? other : job.accountId)
        .destinationId(incoming ? job.accountId : other)
        .sourceCurrency(incoming ? otherCurrency : job.currency)
        .destinationCurrency(incoming ? job.currency : otherCurrency)
        .sourceAmount(incoming ? otherAmount : row.getAmount())
        .destinationAmount(incoming ? row.getAmount() : otherAmount)
        .foreignAmount(text(row.getForeignAmount()).isEmpty() ? null : row.getForeignAmount())
        .foreignCurrency(text(row.getForeignCurrency()).isEmpty() ? null : row.getForeignCurrency())
        .categoryId(category)
        .notes(row.getNotes())
        .externalReference(row.getExternalReference());
  }

  private void validate(Job job, List<GenFinanceImportRow> rows, boolean autoSkip) {
    var account = accounts.transferAccount(job.accountId);
    require(
        account.isActive() && !account.isDeleted() && account.getCurrency().equals(job.currency),
        "Selected account changed or is unavailable");
    Set<String> fingerprints = new HashSet<>();
    sources.findByAccountId(job.accountId).forEach(s -> fingerprints.add(s.getFingerprint()));
    var dates =
        rows.stream()
            .filter(r -> !text(r.getDate()).isBlank())
            .map(
                r -> {
                  try {
                    return dateTime(r.getDate(), null, false);
                  } catch (RuntimeException ex) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    List<FinancePostingEntity> ledger =
        dates.isEmpty()
            ? List.of()
            : postings.importMatches(
                job.accountId,
                dates.getFirst().toLocalDate().atStartOfDay(),
                dates.getLast().toLocalDate().atTime(LocalTime.MAX));
    var knownCategories = reads.categories().getItems();
    var knownCounterparties =
        accountRepository.findAll().stream()
            .filter(a -> !a.isDeleted() && a.isActive() && a.getKind().isCounterparty())
            .toList();
    var matchedCounts = new HashMap<String, Integer>();
    for (var row : rows) {
      var issues = new ArrayList<String>();
      row.setIssues(issues);
      row.setDuplicate("");
      try {
        if (Boolean.TRUE.equals(row.getSkip())) {
          require(!text(row.getReason()).isEmpty(), "A skip reason is required");
          continue;
        }
        resolveAssignments(row, knownCategories, job.currency);
        require(row.getType() != null, "Select a transaction type");
        var date = dateTime(row.getDate(), null, false);
        require(date != null, "Enter a date");
        require(
            text(row.getExternalReference()).length() <= 1024, "External reference is too long");
        required(row.getDescription(), "description");
        var amount = decimal(row.getAmount());
        require(amount.signum() > 0, "Enter a positive booked amount");
        if (!text(row.getReason()).isEmpty() && !Boolean.TRUE.equals(row.getReviewed()))
          issues.add(row.getReason());
        if (row.getOtherAccountId() != null) {
          var other = accounts.requireAccount(job.caller, row.getOtherAccountId());
          require(
              other.isActive() && !other.isDeleted() && other.getId() != job.accountId,
              "Select a different active account");
          require(
              row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER
                  ? other.getKind().isOwn()
                  : other.getKind()
                      == (row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT
                          ? AccountKind.REVENUE
                          : AccountKind.EXPENSE),
              "Counterparty direction does not match transaction type");
          if (row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER) {
            if (other.getCurrency().equals(job.currency)) {
              row.setOtherAmount(row.getAmount());
            } else {
              require(
                  decimal(row.getOtherAmount()).signum() > 0, "Enter the other transfer amount");
            }
          }
        } else {
          require(
              row.getType() != GenFinanceImportRow.TypeEnum.TRANSFER,
              "Select the other main account");
          required(row.getCounterpartyName(), "counterparty");
        }
        require(
            text(row.getCategoryName()).isEmpty(),
            "Select an existing category or leave it uncategorised");
        row.setCategoryId(categories.validate(row.getCategoryId()));
        if (!text(row.getForeignCurrency()).isEmpty()) {
          accounts.currency(row.getForeignCurrency());
          require(decimal(row.getForeignAmount()).signum() > 0, "Enter original amount");
        } else require(text(row.getForeignAmount()).isEmpty(), "Select original currency");
        boolean incoming =
            row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT
                || (row.getType() == GenFinanceImportRow.TypeEnum.TRANSFER
                    && Boolean.TRUE.equals(row.getIncoming()));
        BigDecimal signed = incoming ? amount : amount.negate();
        String signature =
            date.toLocalDate()
                + ":"
                + money(signed)
                + ":"
                + text(row.getDescription()).toLowerCase(Locale.ROOT);
        int occurrence = matchedCounts.merge(signature, 1, Integer::sum);
        long exact =
            ledger.stream()
                .filter(
                    p ->
                        p.getAmount().compareTo(signed) == 0
                            && p.getTransaction()
                                .getOccurredAt()
                                .toLocalDate()
                                .equals(date.toLocalDate())
                            && p.getTransaction()
                                .getDescription()
                                .equalsIgnoreCase(row.getDescription()))
                .count();
        boolean clear =
            fingerprints.contains(job.fingerprints.get(row.getRowId())) || exact >= occurrence;
        boolean possible =
            ledger.stream()
                .anyMatch(
                    p ->
                        p.getAmount().compareTo(signed) == 0
                            && p.getTransaction()
                                .getOccurredAt()
                                .toLocalDate()
                                .equals(date.toLocalDate()));
        boolean linkedSettlement = possible && ledger.stream().anyMatch(p -> p.getAmount().compareTo(signed) == 0
            && p.getTransaction().getOccurredAt().toLocalDate().equals(date.toLocalDate())
            && p.getTransaction().getType() == TransactionType.TRANSFER
            && !splitwiseSources.findByTransactionId(p.getTransaction().getId()).isEmpty());
        if (clear || possible) {
          row.setDuplicate(
              clear
                  ? "Matches an imported row or existing transaction"
                  : linkedSettlement ? "Existing Splitwise settlement books this bank movement; skip or explicitly keep" : "Possible duplicate: same date and amount");
          if (clear && autoSkip) {
            row.setSkip(true);
            row.setReason("Duplicate: " + row.getDuplicate());
            issues.clear();
          } else if (!Boolean.TRUE.equals(row.getDuplicateOverride()))
            issues.add("Skip this duplicate or explicitly keep it");
        }
      } catch (org.springframework.web.server.ResponseStatusException ex) {
        issues.add(ex.getReason());
      } catch (IllegalArgumentException ex) {
        issues.add("Invalid transaction fields");
      }
    }
  }

  private void resolveAssignments(
      GenFinanceImportRow row,
      List<GenFinanceCategory> knownCategories,
      String currency) {
    if (row.getCategoryId() == null || row.getCategoryId() == 0) {
      row.setCategoryId(null);
      if (!text(row.getCategoryName()).isEmpty()) {
        var matches =
            knownCategories.stream()
                .filter(c -> nameKey(c.getName()).equals(nameKey(row.getCategoryName())))
                .toList();
        if (matches.size() == 1) row.setCategoryId(matches.getFirst().getId());
      }
    }
    if (row.getCategoryId() != null) row.setCategoryName("");
    if (row.getOtherAccountId() == null
        && !text(row.getCounterpartyName()).isEmpty()
        && (row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT
            || row.getType() == GenFinanceImportRow.TypeEnum.WITHDRAWAL)) {
      var kind =
          row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT
              ? AccountKind.REVENUE
              : AccountKind.EXPENSE;
      var matches = counterparties.matchingAccounts(row.getCounterpartyName(), kind, currency);
      require(matches.size() <= 1, "Several existing counterparties match; select the correct one");
      if (matches.size() == 1) {
        row.setOtherAccountId(matches.getFirst().getId());
        row.setCounterpartyName("");
      }
    }
  }

  // Treat formatting and common German umlaut spellings as equivalent; never guess a fuzzy
  // identity.
  private String nameKey(String name) {
    String expanded =
        text(name)
            .toLowerCase(Locale.ROOT)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss");
    return java.text.Normalizer.normalize(expanded, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^\\p{L}\\p{N}]", "");
  }

  private List<GenFinanceImportRow> corrected(Job job, List<GenFinanceImportRow> changes) {
    require(
        changes != null && !changes.isEmpty() && changes.size() <= job.rows.size(),
        "Supply reviewed rows");
    var rows = copy(job.rows);
    var byId = new HashMap<String, GenFinanceImportRow>();
    rows.forEach(r -> byId.put(r.getRowId(), r));
    var seen = new HashSet<String>();
    for (var change : changes) {
      require(
          change != null && seen.add(change.getRowId()) && byId.containsKey(change.getRowId()),
          "Unknown or duplicate source row");
      var corrected = json.convertValue(change, GenFinanceImportRow.class);
      corrected.setSource(byId.get(change.getRowId()).getSource());
      corrected.setReviewed(true);
      rows.set(rows.indexOf(byId.get(change.getRowId())), corrected);
    }
    return rows;
  }

  private List<GenFinanceImportRow> copy(List<GenFinanceImportRow> rows) {
    return rows.stream()
        .map(r -> json.readValue(json.writeValueAsString(r), GenFinanceImportRow.class))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  private void ready(Job job, Long revision) {
    conflict(job.status == GenFinanceImportJob.StatusEnum.READY, "Import is not ready");
    conflict(
        revision != null && revision == job.revision,
        "Draft changed; reload before reviewing or approving");
  }

  private Job job(UserId user, String id) {
    var job = jobs.get(id);
    if (job == null || !job.caller.equals(user))
      throw missing("Import draft (expired or unavailable)");
    synchronized (job) {
      if (!job.expires.isAfter(financeClock.instant()))
        throw missing("Import draft (expired or unavailable)");
      // Browsers keep open drafts alive. Abandoned drafts still have bounded memory retention.
      if (job.status == GenFinanceImportJob.StatusEnum.READY
          || job.status == GenFinanceImportJob.StatusEnum.RUNNING
          || job.status == GenFinanceImportJob.StatusEnum.QUEUED)
        job.expires = financeClock.instant().plus(Duration.ofHours(1));
      return job;
    }
  }

  private GenFinanceImportJob view(Job job, int page, int size) {
    synchronized (job) {
      var rows = copy(job.rows);
      int from = Math.min(rows.size(), page * size);
      var impact = BigDecimal.ZERO;
      var income = BigDecimal.ZERO;
      var expenses = BigDecimal.ZERO;
      int skipped = 0, unresolved = 0, created = 0;
      for (var row : rows) {
        if (Boolean.TRUE.equals(row.getSkip())) skipped++;
        else if (row.getIssues() != null && !row.getIssues().isEmpty()) unresolved++;
        else created++;
      }
      for (var row : rows)
        if (!Boolean.TRUE.equals(row.getSkip())
            && row.getIssues() != null
            && row.getIssues().isEmpty()) {
          var amount = decimal(row.getAmount());
          impact =
              impact.add(
                  row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT
                          || Boolean.TRUE.equals(row.getIncoming())
                      ? amount
                      : amount.negate());
          if (row.getType() == GenFinanceImportRow.TypeEnum.DEPOSIT) income = income.add(amount);
          else if (row.getType() == GenFinanceImportRow.TypeEnum.WITHDRAWAL)
            expenses = expenses.add(amount);
        }
      var balance = reads.balance(job.caller, job.accountId, LocalDateTime.now(financeClock));
      return new GenFinanceImportJob()
          .currentBalance(money(balance))
          .predictedBalance(money(balance.add(impact)))
          .incomeTotal(money(income))
          .expenseTotal(money(expenses))
          .createCount(created)
          .skippedCount(skipped)
          .unresolvedCount(unresolved)
          .jobId(job.id)
          .accountId(job.accountId)
          .fileName(job.fileName)
          .status(job.status)
          .revision(job.revision)
          .processed(job.processed)
          .total(
              job.status == GenFinanceImportJob.StatusEnum.RUNNING
                      || job.status == GenFinanceImportJob.StatusEnum.QUEUED
                  ? job.source.size()
                  : rows.size())
          .page(page)
          .pageSize(size)
          .rows(rows.subList(from, Math.min(rows.size(), from + size)))
          .error(job.error)
          .expiresAt(job.expires.toString())
          .balanceImpact(money(impact))
          .readyToApprove(
              job.status == GenFinanceImportJob.StatusEnum.READY
                  && rows.stream().allMatch(r -> r.getIssues() != null && r.getIssues().isEmpty()));
    }
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String childKey(GenFinanceImportApproveInput input, String operation, String value) {
    return "import:" + hash(input.getRequestKey() + ":" + operation + ":" + value);
  }

  @Scheduled(fixedDelay = 60000)
  public void expire() {
    jobs.entrySet()
        .removeIf(
            e -> {
              var j = e.getValue();
              synchronized (j) {
                boolean expired = !j.expires.isAfter(financeClock.instant());
                if (expired && j.task != null) j.task.cancel(true);
                return expired;
              }
            });
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
    jobs.clear();
  }
}
