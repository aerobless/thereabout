package com.sixtymeters.thereabout.finance.service;

import com.sixtymeters.thereabout.ai.OpenAiService;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.text.Normalizer;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Retrieval is local and exhaustive; only a bounded shortlist reaches the model. */
@Service
@RequiredArgsConstructor
public class ImportCounterpartyMatcher {
  private final FinanceCounterpartyRepository counterparties;
  private final FinanceAccountRepository accounts;
  private final OpenAiService ai;
  private static final double AUTOMATIC_MATCH_THRESHOLD = .95;
  private final ObjectMapper json;
  public record AccountScope(AccountKind kind, String currency) {}
  public record Candidate(long id, long version, String name, List<String> aliases, Set<AccountScope> accounts) {
    GenFinanceImportCandidate view() { return new GenFinanceImportCandidate().id(id).version(version).name(name); }
  }
  public record Match(Long selected, GenFinanceImportRow.MatchStatusEnum status, List<Candidate> candidates, String message) {}
  @Transactional(readOnly = true)
  public List<Candidate> snapshot() {
    var scopes = new HashMap<Long, Set<AccountScope>>();
    accounts.findAll().stream().filter(a -> a.getCounterpartyId() != null && a.isActive() && !a.isDeleted())
        .forEach(a -> scopes.computeIfAbsent(a.getCounterpartyId(), ignored -> new HashSet<>())
            .add(new AccountScope(a.getKind(), a.getCurrency())));
    return counterparties.findAll().stream().filter(c -> c.getMergedIntoId() == null)
        .map(c -> new Candidate(c.getId(), c.getVersion(), c.getName(), List.copyOf(c.getAliases()), Set.copyOf(scopes.getOrDefault(c.getId(), Set.of())))).toList();
  }
  public static String normalized(String name) {
    if (name == null) return "";
    return Normalizer.normalize(name.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss"), Normalizer.Form.NFKD)
        .replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
  }
  private static List<String> names(Candidate c) {
    var names = new ArrayList<String>(); names.add(c.name()); names.addAll(c.aliases()); return names;
  }
  public Match match(String merchant, String description, List<String> evidence, AccountKind kind, String currency, List<Candidate> all, Map<String, Match> cache) {
    String label = normalized(merchant);
    if (label.isBlank()) return new Match(null, GenFinanceImportRow.MatchStatusEnum.REVIEW, List.of(), "Enter or select a counterparty.");
    var exact = all.stream().filter(c -> names(c).stream().anyMatch(n -> normalized(n).equals(label))).sorted(Comparator.comparingLong(Candidate::id)).toList();
    if (exact.size() == 1) return new Match(exact.getFirst().id(), GenFinanceImportRow.MatchStatusEnum.EXACT, exact, "Exact canonical name or alias");
    // Legacy imports can hold separate expense/income identities with the same name.
    // Prefer an existing ledger only when it uniquely resolves identical canonical names.
    if (exact.size() > 1 && exact.stream().allMatch(c -> normalized(c.name()).equals(label))) {
      var compatible = exact.stream().filter(c -> c.accounts().contains(new AccountScope(kind, currency))).toList();
      if (compatible.size() == 1) return new Match(compatible.getFirst().id(), GenFinanceImportRow.MatchStatusEnum.EXACT,
          exact.stream().sorted(Comparator.comparing(c -> c.id() != compatible.getFirst().id())).limit(8).toList(), "Exact name with a unique account for this direction and currency");
    }
    var shortlist = exact.isEmpty() ? rank(merchant, all) : exact.stream().limit(8).toList();
    if (shortlist.isEmpty()) return new Match(null, GenFinanceImportRow.MatchStatusEnum.NEW, List.of(), "No plausible existing counterparty found");
    String key = label + ":" + kind + ":" + currency + ":" + shortlist.stream().map(c -> c.id() + "/" + c.version()).toList();
    return cache.computeIfAbsent(key, ignored -> decide(merchant, description, evidence, shortlist, exact.size() > 1));
  }
  private Match decide(String merchant, String description, List<String> evidence, List<Candidate> shortlist, boolean ambiguousExact) {
    try {
      var choices = shortlist.stream().map(c -> new OpenAiService.MerchantChoice(Long.toString(c.id()),
          bounded(c.name(), 180) + "; aliases: " + c.aliases().stream().sorted().limit(4).map(a -> bounded(a, 120)).toList())).toList();
      String input = json.writeValueAsString(Map.of("merchant", bounded(merchant, 400), "description", bounded(description, 600),
          "csvEvidence", bounded(String.join(" | ", evidence == null ? List.of() : evidence), 1000)));
      var choice = ai.chooseMerchant(input, choices);
      if (choice == null) throw new IllegalStateException("No decision");
      Long id = "none".equals(choice.id()) ? null : shortlist.stream().filter(c -> Long.toString(c.id()).equals(choice.id())).map(Candidate::id).findFirst().orElseThrow();
      boolean automatic = id != null && !ambiguousExact
          && acceptable(choice.confidence()) && acceptable(choice.probability());
      return new Match(id, automatic ? GenFinanceImportRow.MatchStatusEnum.CONFIDENT : GenFinanceImportRow.MatchStatusEnum.REVIEW, shortlist,
          automatic ? "High-confidence existing counterparty match" : id == null ? "Confirm creating a new counterparty; plausible existing matches are available." : "Suggested existing counterparty; confirm or choose another.");
    } catch (RuntimeException unavailable) {
      // Failure/refusal cannot turn into automatic entity creation.
      return new Match(shortlist.getFirst().id(), GenFinanceImportRow.MatchStatusEnum.UNAVAILABLE, shortlist,
          "Automatic matching unavailable. Review the candidates and confirm your selection.");
    }
  }
  private static boolean acceptable(double value) {
    return Double.isFinite(value) && value >= AUTOMATIC_MATCH_THRESHOLD && value <= 1;
  }
  static String bounded(String value, int length) { return value == null ? "" : value.substring(0, Math.min(length, value.length())); }
  public List<Candidate> rank(String merchant, List<Candidate> all) {
    var frequencies = new HashMap<String, Integer>();
    for (var c : all) names(c).stream().flatMap(n -> tokens(n).stream()).distinct().forEach(t -> frequencies.merge(t, 1, Integer::sum));
    record Ranked(Candidate candidate, double score) {}
    return all.stream().map(c -> new Ranked(c, names(c).stream().mapToDouble(n -> score(merchant, n, frequencies)).max().orElse(0)))
        .filter(r -> r.score() >= .58).sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparingLong(r -> r.candidate().id()))
        .limit(8).map(Ranked::candidate).toList();
  }
  private static Set<String> tokens(String name) {
    return Arrays.stream(normalized(name).split(" ")).filter(t -> t.length() >= 3 && !Set.of("the", "and", "ltd", "gmbh", "inc", "payment", "purchase", "zahlung", "card", "debit", "credit", "online").contains(t))
        .collect(java.util.stream.Collectors.toSet());
  }
  private static double score(String a, String b, Map<String, Integer> frequencies) {
    String x = normalized(a), y = normalized(b);
    if (x.isEmpty() || y.isEmpty()) return 0;
    var ta = tokens(a); var tb = tokens(b);
    double weight = tb.stream().mapToDouble(t -> 1.0 / Math.sqrt(frequencies.getOrDefault(t, 1))).sum();
    double overlap = tb.stream().filter(ta::contains).mapToDouble(t -> 1.0 / Math.sqrt(frequencies.getOrDefault(t, 1))).sum();
    // Token evidence can recognize merchant + city/reference; spelling similarity handles short names.
    double tokenScore = weight == 0 ? 0 : overlap / weight * .85;
    return Math.max(tokenScore, similarity(bounded(x, 180), bounded(y, 180)));
  }
  private static double similarity(String a, String b) {
    int[] previous = new int[b.length() + 1]; for (int j = 0; j <= b.length(); j++) previous[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      int[] current = new int[b.length() + 1]; current[0] = i;
      for (int j = 1; j <= b.length(); j++) current[j] = Math.min(Math.min(current[j-1] + 1, previous[j] + 1), previous[j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1));
      previous = current;
    }
    return 1.0 - (double) previous[b.length()] / Math.max(a.length(), b.length());
  }
}
