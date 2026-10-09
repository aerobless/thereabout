package com.sixtymeters.thereabout.finance.service;
import com.sixtymeters.thereabout.ai.OpenAiService;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.GenFinanceImportRow.MatchStatusEnum;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ImportCounterpartyMatcherTest {
  private final OpenAiService ai = mock(OpenAiService.class);
  private final ImportCounterpartyMatcher matcher = new ImportCounterpartyMatcher(mock(FinanceCounterpartyRepository.class), mock(FinanceAccountRepository.class), ai, new ObjectMapper());
  private ImportCounterpartyMatcher.Candidate candidate(long id, String name, String... aliases) { return new ImportCounterpartyMatcher.Candidate(id, 0, name, List.of(aliases), Set.of()); }
  @Test void exactCanonicalAndNormalizedAliasesAvoidApiCalls() {
    var catalog = List.of(candidate(1, "Zühlke Engineering AG", "Zuehlke Engineering A.G."));
    var result = matcher.match(" Zuehlke Engineering A.G. ", "Invoice", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>());
    assertThat(result.status()).isEqualTo(MatchStatusEnum.EXACT); assertThat(result.selected()).isEqualTo(1L); verifyNoInteractions(ai);
  }
  @Test void identicalCanonicalNamesUseTheUniqueCompatibleDirectionAndCurrency() {
    var expense = new ImportCounterpartyMatcher.Candidate(1, 0, "Coop", List.of(), Set.of(new ImportCounterpartyMatcher.AccountScope(AccountKind.EXPENSE, "CHF")));
    var income = new ImportCounterpartyMatcher.Candidate(2, 0, "coop", List.of(), Set.of(new ImportCounterpartyMatcher.AccountScope(AccountKind.REVENUE, "CHF")));
    var cache = new HashMap<String, ImportCounterpartyMatcher.Match>();
    assertThat(matcher.match("Coop", "Purchase", List.of(), AccountKind.EXPENSE, "CHF", List.of(expense, income), cache).selected()).isEqualTo(1L);
    var refund = matcher.match("Coop", "Refund", List.of(), AccountKind.REVENUE, "CHF", List.of(expense, income), cache);
    assertThat(refund.selected()).isEqualTo(2L); assertThat(refund.status()).isEqualTo(MatchStatusEnum.EXACT);
    verifyNoInteractions(ai);
    when(ai.chooseMerchant(anyString(), anyList())).thenReturn(new OpenAiService.MerchantDecision("1", .99, .99));
    var missingCurrency = matcher.match("Coop", "Purchase", List.of(), AccountKind.EXPENSE, "EUR", List.of(expense, income), cache);
    assertThat(missingCurrency.status()).isEqualTo(MatchStatusEnum.REVIEW);
  }
  @Test void theSelectedExactMatchRemainsInTheBoundedShortlist() {
    var catalog = new ArrayList<ImportCounterpartyMatcher.Candidate>();
    for (int i=1; i<=9; i++) catalog.add(new ImportCounterpartyMatcher.Candidate(i, 0, "Coop", List.of(),
        i == 9 ? Set.of(new ImportCounterpartyMatcher.AccountScope(AccountKind.EXPENSE, "CHF")) : Set.of()));
    var match = matcher.match("Coop", "Purchase", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>());
    assertThat(match.selected()).isEqualTo(9L); assertThat(match.candidates()).hasSize(8);
    assertThat(match.candidates()).extracting(ImportCounterpartyMatcher.Candidate::id).contains(9L);
    verifyNoInteractions(ai);
  }
  @Test void highConfidenceExistingMatchesAreAutomaticButWeakOrInvalidEstimatesNeedReview() {
    var catalog = List.of(candidate(1, "Europa-Park GmbH"));
    when(ai.chooseMerchant(anyString(), anyList())).thenReturn(new OpenAiService.MerchantDecision("1", .95, .95));
    var accepted = matcher.match("Europa-Park", "Tickets", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>());
    assertThat(accepted.status()).isEqualTo(MatchStatusEnum.CONFIDENT); assertThat(accepted.selected()).isEqualTo(1L);
    for (var decision : List.of(new OpenAiService.MerchantDecision("1", .94, .99), new OpenAiService.MerchantDecision("1", .99, .94),
        new OpenAiService.MerchantDecision("1", Double.NaN, .99), new OpenAiService.MerchantDecision("1", .99, Double.POSITIVE_INFINITY),
        new OpenAiService.MerchantDecision("1", 1.1, .99))) {
      when(ai.chooseMerchant(anyString(), anyList())).thenReturn(decision);
      assertThat(matcher.match("Europa-Park", "Tickets", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>()).status()).isEqualTo(MatchStatusEnum.REVIEW);
    }
  }
  @Test void identicalNamesAndSharedAliasesCannotBeAutoAcceptedByModelConfidenceAlone() {
    when(ai.chooseMerchant(anyString(), anyList())).thenReturn(new OpenAiService.MerchantDecision("1", .999, .999));
    for (var catalog : List.of(List.of(candidate(1, "Coop"), candidate(2, "coop")),
        List.of(candidate(1, "Alex Winter", "Alex"), candidate(2, "Alex Smith", "Alex")))) {
      String merchant = catalog.getFirst().name().equals("Coop") ? "Coop" : "Alex";
      assertThat(matcher.match(merchant, "Payment", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>()).status()).isEqualTo(MatchStatusEnum.REVIEW);
    }
  }
  @Test void boundsCandidatesAndCachesRepeatedMerchantsWithoutAutomaticConfirmation() {
    var catalog = new ArrayList<ImportCounterpartyMatcher.Candidate>();
    for (int i=1; i<=30; i++) catalog.add(candidate(i, "Acme market " + i));
    when(ai.chooseMerchant(anyString(), anyList())).thenReturn(new OpenAiService.MerchantDecision("1", .8, .8));
    var cache = new HashMap<String, ImportCounterpartyMatcher.Match>();
    var first = matcher.match("Acme market town", "Purchase", List.of("Acme market town"), AccountKind.EXPENSE, "CHF", catalog, cache);
    var repeated = matcher.match("ACME MARKET TOWN", "Another purchase", List.of(), AccountKind.EXPENSE, "CHF", catalog, cache);
    assertThat(first.candidates()).hasSize(8); assertThat(first.status()).isEqualTo(MatchStatusEnum.REVIEW); assertThat(repeated).isEqualTo(first);
    verify(ai, times(1)).chooseMerchant(anyString(), argThat(choices -> choices.size() == 8));
  }
  @Test void apiFailureAndRefusalRetainCandidatesAndCannotCreateAutomatically() {
    var catalog = List.of(candidate(1, "Revolut"));
    when(ai.chooseMerchant(anyString(), anyList())).thenThrow(new IllegalStateException("API refused"));
    var result = matcher.match("Revolutt", "Card", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>());
    assertThat(result.status()).isEqualTo(MatchStatusEnum.UNAVAILABLE); assertThat(result.selected()).isEqualTo(1L); assertThat(result.candidates()).hasSize(1);
    doReturn(new OpenAiService.MerchantDecision("none", .99, .99)).when(ai).chooseMerchant(anyString(), anyList());
    var none = matcher.match("Revolutt", "Card", List.of(), AccountKind.EXPENSE, "CHF", catalog, new HashMap<>());
    assertThat(none.selected()).isNull(); assertThat(none.status()).isEqualTo(MatchStatusEnum.REVIEW);
  }
}
