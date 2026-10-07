package com.sixtymeters.thereabout.finance.splitwise;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.FinanceReadRepository;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Reads persisted source evidence, including history deliberately excluded from automatic import. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SplitwiseSources {
  private final SplitwiseSourceRepository sources;
  private final SplitwiseConnectionRepository connections;
  private final FinanceReadRepository reads;
  private final ObjectMapper json;

  private long group() {
    var c = connections.findById(1L).orElseThrow(() -> missing("Splitwise connection"));
    var id = json.readValue(c.getSettingsJson(), GenSplitwiseSettings.class).getGroupId();
    require(id != null, "Select a Splitwise group first");
    return id;
  }

  public GenSplitwiseSourcePage list(GenSplitwiseSourceQuery query) {
    long group = group();
    String from = query.getFrom() == null ? null : date(query.getFrom()).toString();
    String to = query.getTo() == null ? null : date(query.getTo()).toString();
    require(from == null || to == null || from.compareTo(to) <= 0, "Start date must precede end date");
    int page = page(query.getPage()), size = pageSize(query.getPageSize());
    var result = sources.findAll((root, cq, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("groupId"), group));
      if (query.getExpenseId() != null) predicates.add(cb.equal(root.get("expenseId"), query.getExpenseId()));
      if (query.getMemberId() != null) predicates.add(cb.equal(root.get("memberId"), query.getMemberId()));
      if (query.getState() != null) predicates.add(cb.equal(root.get("state"), query.getState()));
      // rowJson stores the same Zurich calendar date shown in the preview, rather than a UTC date.
      var day = cb.substring(cb.function("json_value", String.class, root.get("rowJson"), cb.literal("$.date")), 1, 10);
      if (from != null) predicates.add(cb.greaterThanOrEqualTo(day, from));
      if (to != null) predicates.add(cb.lessThanOrEqualTo(day, to));
      if (!text(query.getQ()).isEmpty()) {
        var description = cb.lower(cb.function("json_value", String.class, root.get("sourceJson"), cb.literal("$.description")));
        String term = text(query.getQ()).toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        predicates.add(cb.like(description, "%" + term + "%", '\\'));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    }, PageRequest.of(page, size, Sort.by("expenseId", "memberId")));
    return new GenSplitwiseSourcePage().items(result.getContent().stream().map(this::view).toList())
        .total(result.getTotalElements()).page(page).pageSize(size);
  }

  public GenSplitwiseSourceDetail get(GenSplitwiseSourceKey key) {
    var source = sources.findById(SplitwiseExpense.reference(key.getExpenseId(), key.getMemberId())).orElseThrow(() -> missing("Source entry"));
    require(source.getGroupId() == group(), "Source entry does not belong to the configured group");
    return detail(source);
  }

  public GenSplitwiseSource view(SplitwiseSource source) {
    var e = json.readValue(source.getSourceJson(), SplitwiseExpense.class);
    var result = new GenSplitwiseSource().id(source.getId()).expenseId(source.getExpenseId()).memberId(source.getMemberId())
        .accountId(source.getAccountId()).version(source.getVersion()).state(source.getState()).locallyManaged(source.isLocallyManaged())
        .classification(source.getClassification()).message(source.getMessage()).transactionId(source.getTransactionId())
        .appliedVersion(source.getAppliedVersion()).description(Objects.toString(e.description(), ""))
        .date(e.occurredAt().toString()).amount(money(e.amount(source.getMemberId()))).currency(e.currency())
        .sourceCategoryId(e.category() == null ? null : e.category().id()).updatedAt(e.updatedAt()).deletedAt(e.deletedAt());
    if (source.getTransactionId() == null && source.getRowJson() != null)
      result.setCandidateTransactionId(json.readValue(source.getRowJson(), GenSplitwiseRow.class).getTransactionId());
    Long transaction = result.getTransactionId() == null ? result.getCandidateTransactionId() : result.getTransactionId();
    if (transaction != null) result.setTransactionVersion(reads.transaction(owner(source), transaction).getVersion());
    return result;
  }

  public GenSplitwiseSourceDetail detail(SplitwiseSource source) {
    var e = json.readValue(source.getSourceJson(), SplitwiseExpense.class);
    var evidence = new GenSplitwiseEvidence().groupId(e.groupId()).payment(e.payment()).details(e.details()).date(e.date())
        .updatedAt(e.updatedAt()).deletedAt(e.deletedAt()).shares(e.users() == null ? List.of() : e.users().stream()
            .map(s -> new GenSplitwiseShare().memberId(s.userId()).paidShare(s.paidShare()).owedShare(s.owedShare())).toList());
    var result = new GenSplitwiseSourceDetail().source(view(source)).evidence(evidence);
    if (source.getTransactionId() != null) result.setTransaction(reads.transaction(owner(source), source.getTransactionId()));
    return result;
  }

  private UserId owner(SplitwiseSource source) {
    return new UserId(reads.account(new UserId(1), source.getAccountId()).getUserId());
  }
}
