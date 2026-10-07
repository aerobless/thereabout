package com.sixtymeters.thereabout.finance.transport;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.FinanceReadRepository;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finances")
@RequiredArgsConstructor
public class FinanceController {
  private final FinanceReadRepository reads;
  private final AccountService accounts;
  private final CategoryService categories;
  private final TransactionService transactions;
  private final ValuationService valuations;
  private final ExchangeRateService rates;
  private final ReportService reports;
  private final com.sixtymeters.thereabout.access.UserContext users;

  @GetMapping("/overview")
  public GenFinanceOverview financeOverview(@Valid @ModelAttribute GenFinancePeriodQuery query) {
    return reports.overview(users.current(), query);
  }

  @GetMapping("/users")
  public java.util.List<GenFinanceUser> financeUsers() {
    users.current();
    return reads.users();
  }

  @GetMapping("/accounts")
  public GenFinanceAccountPage financeListAccounts(
      @Valid @ModelAttribute GenFinanceAccountQuery query) {
    return reads.accounts(users.current(), query);
  }

  @GetMapping("/transfer-accounts")
  public GenFinanceTransferAccountPage transferAccounts(@RequestParam(required=false) String q,
      @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer pageSize,
      @RequestParam(required=false) Long id) {
    return reads.transferAccounts(users.current(),q,page,pageSize,id);
  }

  @PostMapping("/accounts")
  public GenFinanceAccountResult financeCreateAccounts(
      @Valid @RequestBody GenFinanceAccountInput input) {
    require(input.getId() == null, "Create must not specify an id");
    return accounts.save(users.current(), input);
  }

  @PutMapping("/accounts/{id}")
  public GenFinanceAccountResult financeUpdateAccounts(
      @PathVariable long id, @Valid @RequestBody GenFinanceAccountInput input) {
    require(input.getId() == null || input.getId() == id, "Path and body id differ");
    input.setId(id);
    return accounts.save(users.current(), input);
  }

  @GetMapping("/categories")
  public GenFinanceCategoryList financeListCategories() {
    return reads.categories();
  }

  @PostMapping("/categories")
  public GenFinanceCategoryResult financeCreateCategories(
      @Valid @RequestBody GenFinanceCategoryInput input) {
    require(input.getId() == null, "Create must not specify an id");
    return categories.save(users.current(), input);
  }

  @PutMapping("/categories/{id}")
  public GenFinanceCategoryResult financeUpdateCategories(
      @PathVariable long id, @Valid @RequestBody GenFinanceCategoryInput input) {
    require(input.getId() == null || input.getId() == id, "Path and body id differ");
    input.setId(id);
    return categories.save(users.current(), input);
  }

  @PostMapping("/transactions/search")
  public GenFinanceTransactionPage searchTransactions(@Valid @RequestBody GenFinanceTransactionQuery input) {
    return reads.transactions(users.current(), input);
  }

  @GetMapping("/transactions")
  public GenFinanceTransactionPage financeListTransactions(
      @Valid @ModelAttribute GenFinanceTransactionQuery query) {
    return reads.transactions(users.current(), query);
  }

  @PostMapping("/transactions")
  public GenFinanceTransactionResult financeCreateTransactions(
      @Valid @RequestBody GenFinanceTransactionInput input) {
    require(input.getId() == null, "Create must not specify an id");
    return transactions.save(users.current(), input);
  }

  @PutMapping("/transactions/{id}")
  public GenFinanceTransactionResult financeUpdateTransactions(
      @PathVariable long id, @Valid @RequestBody GenFinanceTransactionInput input) {
    require(input.getId() == null || input.getId() == id, "Path and body id differ");
    input.setId(id);
    return transactions.save(users.current(), input);
  }

  @GetMapping("/currencies")
  public GenFinanceCurrencyList financeCurrencies() {
    return reads.currencies();
  }

  @GetMapping("/transactions/{id}")
  public GenFinanceTransactionDetail financeTransactionDetail(@PathVariable long id) {
    return new GenFinanceTransactionDetail()
        .transaction(reads.transaction(users.current(), id))
        .history(reads.history(users.current(), id));
  }

  @DeleteMapping("/transactions/{id}")
  public GenFinanceTransactionResult financeDeleteTransaction(
      @PathVariable long id, @Valid @RequestBody GenFinanceVersionedInput input) {
    require(input.getId() == null || input.getId() == id, "Path and body id differ");
    input.setId(id);
    return transactions.setDeleted(users.current(), input, true);
  }

  @PostMapping("/transactions/{id}/restore")
  public GenFinanceTransactionResult financeRestoreTransaction(
      @PathVariable long id, @Valid @RequestBody GenFinanceVersionedInput input) {
    require(input.getId() == null || input.getId() == id, "Path and body id differ");
    input.setId(id);
    return transactions.setDeleted(users.current(), input, false);
  }

  @PostMapping("/transactions/categories")
  public GenFinanceBulkResult financeCategorizeTransactions(
      @Valid @RequestBody GenFinanceBulkCategoryInput input) {
    return transactions.categorize(users.current(), input);
  }

  @PostMapping("/valuations/preview")
  public GenFinanceValuationPreview financePreviewValuation(
      @Valid @RequestBody GenFinanceValuationPreviewInput input) {
    return valuations.preview(users.current(), input);
  }

  @PostMapping("/valuations")
  public GenFinanceValuationResult financeCreateValuation(
      @Valid @RequestBody GenFinanceValuationInput input) {
    return valuations.save(users.current(), input);
  }

  @GetMapping("/valuations")
  public GenFinanceValuationList financeListValuations(
      @Valid @ModelAttribute GenFinanceValuationQuery query) {
    return reads.valuations(users.current(), query.getAccountId());
  }

  @GetMapping("/rates")
  public GenFinanceRateList financeListRates(@Valid @ModelAttribute GenFinanceRateQuery query) {
    return reads.rates(query);
  }

  @PutMapping("/rates/manual")
  public GenFinanceRateResult financeSaveRate(@Valid @RequestBody GenFinanceRateInput input) {
    return rates.save(users.current(), input);
  }

  @PostMapping("/rates/refresh")
  public GenFinanceRateRefreshResult financeRefreshRates(
      @Valid @RequestBody GenFinanceRefreshInput input) {
    return rates.refresh(users.current(), input);
  }

  @GetMapping("/reports/income-expenses")
  public GenFinanceReport financeReportIncomeExpenses(
      @Valid @ModelAttribute GenFinancePeriodQuery query) {
    return reports.report(users.current(), query);
  }

  @GetMapping("/reports/categories")
  public GenFinanceReport financeReportCategories(
      @Valid @ModelAttribute GenFinancePeriodQuery query) {
    return reports.report(users.current(), query);
  }

  @GetMapping("/reports/investments")
  public GenFinanceReport financeReportInvestments(
      @Valid @ModelAttribute GenFinancePeriodQuery query) {
    return reports.report(users.current(), query);
  }
}
