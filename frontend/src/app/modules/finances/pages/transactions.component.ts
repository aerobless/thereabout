import {CategoryLabelComponent} from '../shared/category-label.component';
import {localDateString, parseLocalDate} from '../../../shared/dates/local-date';
import {FilterMetadata} from 'primeng/api';
import {ColumnFilter} from 'primeng/table';
import {FinanceDateFilter, FinanceDateRule} from '../../../../../generated/backend-api/thereabout';
import { FinanceDateInputComponent } from "../shared/finance-date-input.component";
import { SelectModule } from "primeng/select";
import { MultiSelectModule } from "primeng/multiselect";
import { TableModule, TableLazyLoadEvent } from "primeng/table";
import { Subject, debounceTime, map, distinctUntilChanged, switchMap, catchError, of } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, RouterLink } from "@angular/router";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceTransaction,
  FinanceTransactionQuery,
  FinanceTransactionType,
  loadResource,
  today,
} from "../shared/finance-ui";
@Component({
  selector: "finance-transactions",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FinanceDateInputComponent,
    CategoryLabelComponent,
    SelectModule,
    MultiSelectModule,
    TableModule,
    CommonModule,
    FormsModule,
    RouterLink,
  ],
  templateUrl: "./transactions.component.html",
  styleUrl: "./transactions.component.scss",
})
export class TransactionsComponent {
  readonly context = inject(FinanceContext);
  private dialogs = inject(FinanceDialogs);
  readonly accountId = input(0);
  readonly counterpartyId = input(0);
  readonly recent = input(false);
  private readonly route = inject(ActivatedRoute);
  private readonly routeParams = toSignal(this.route.queryParamMap);
  private readonly linkedTransaction = toSignal(this.route.queryParamMap.pipe(
    map(params => Number(params.get("transaction"))), distinctUntilChanged(),
    switchMap(id => Number.isSafeInteger(id) && id > 0
      ? this.context.api.client.financeTransactionDetail(id).pipe(catchError(() => of(null))) : of(null)),
  ));
  q = "";
  accountFilter = 0;
  categoryFilter = "";
  categoryFilters: number[] = [];
  sort: FinanceTransactionQuery.SortEnum = "DATE_DESC";
  readonly typeOptions = [
    { label: "All types", value: "" },
    { label: "Expenses", value: "WITHDRAWAL" },
    { label: "Income", value: "DEPOSIT" },
    { label: "Transfers", value: "TRANSFER" },
  ];
  readonly accountOptions = computed(() => [{ id: 0, name: "All accounts" }, ...this.context.accounts()]);
  readonly categoryOptions = computed(() => [{ id: 0, name: "Uncategorized" }, ...this.context.categories()]);
  readonly searchChanges = new Subject<void>();

  typeFilter: FinanceTransactionType | "" = "";
  showDeleted = false;
  useDates = false;
  operatingOnly = false;
  from = today().slice(0, 4) + "-01-01";
  to = today();
  page = 0;
  bulkCategory = 0;
  readonly selected = new Map<number, number>();
  private readonly filters = signal<FinanceTransactionQuery | null>(null);
  readonly dateFilter = signal<FinanceDateFilter | undefined>(undefined);
  tableFilters: Record<string, FilterMetadata | FilterMetadata[]> = {};
  readonly dateModes = [
    {label:'Date is',value:'dateIs'}, {label:'Date is not',value:'dateIsNot'},
    {label:'Date is before',value:'dateBefore'}, {label:'Date is after',value:'dateAfter'},
    {label:'Date is on or before',value:'dateOnOrBefore'}, {label:'Date is on or after',value:'dateOnOrAfter'}
  ];
  dateValue(value: unknown): string { return value instanceof Date ? localDateString(value) : ''; }
  setDate(constraint: FilterMetadata, date: string) { constraint.value = parseLocalDate(date); }
  completeDates(constraints: FilterMetadata | FilterMetadata[] | null | undefined): boolean {
    const rules = Array.isArray(constraints) ? constraints : constraints ? [constraints] : [];
    return rules.length > 0 && rules.every(rule => rule.value instanceof Date && Number.isFinite(rule.value.getTime()));
  }
  applyDates(column: ColumnFilter): void {
    const constraints = column.fieldConstraints;
    if (!this.completeDates(constraints)) return;
    const rules = (Array.isArray(constraints) ? constraints : [constraints]).filter((rule): rule is FilterMetadata => !!rule);
    this.dateFilter.set({operator:rules[0].operator === 'or' ? 'or' : 'and', rules:rules.map(rule => ({mode:rule.matchMode as FinanceDateRule.ModeEnum, date:this.dateValue(rule.value)}))});
    this.useDates=false; column.setHasFilter(true); column.hide(); this.filterTransactions();
  }
  clearDates(column: ColumnFilter): void { this.dateFilter.set(undefined); this.useDates=false; column.clearFilter(); column.hide(); this.filterTransactions(); }
  constructor() {
    effect(() => {
      const detail = this.linkedTransaction();
      if (detail) this.dialogs.open({ kind: "history", transaction: detail.transaction });
    });
    this.searchChanges
      .pipe(debounceTime(250), takeUntilDestroyed())
      .subscribe(() => this.filterTransactions());
    effect(() => {
      const params = this.routeParams();
      this.q = "";
      this.page = 0;
      this.accountFilter = Number(params?.get("account") ?? 0);
      this.categoryFilter = params?.get("category") ?? "";
      this.categoryFilters =
        this.categoryFilter === "" ? [] : [Number(this.categoryFilter)];
      this.operatingOnly = params?.get("operating") === "true";
      this.useDates = !!(params?.has("from") || params?.has("to"));
      this.from = params?.get("from") ?? today().slice(0, 4) + "-01-01";
      this.to = params?.get("to") ?? today();
      this.dateFilter.set(undefined);
      this.tableFilters = this.useDates ? {occurredAt:[
        ...(params?.has('from') ? [{value:parseLocalDate(this.from.slice(0,10)),matchMode:'dateOnOrAfter',operator:'and'}] : []),
        ...(params?.has('to') ? [{value:parseLocalDate(this.to.slice(0,10)),matchMode:'dateOnOrBefore',operator:'and'}] : [])
      ]} : {};
      this.selected.clear();
      untracked(() => this.load());
    });
  }
  private readonly query = computed(() => ({
    ...this.filters(),
    accountId: this.accountId() || this.filters()?.accountId,
    counterpartyId: this.counterpartyId() || undefined,
    pageSize: this.recent() ? 8 : 50,
    revision: this.context.revision(),
  }));
  readonly state = loadResource(this.query, (q) =>
    this.context.api.transactions(q),
  );
  readonly transactions = computed(() => this.state().data?.items ?? []);
  get total() {
    return this.state().data?.total ?? 0;
  }
  get busy() {
    return this.state().loading;
  }
  get saving() {
    return this.context.saving();
  }
  get categories() {
    return this.context.categories();
  }
  get ownAccounts() {
    return this.context.accounts();
  }
  get currentAccount() {
    return this.ownAccounts.find((a) => a.id === this.accountId());
  }
  readonly money = this.context.money.bind(this.context);
  filterTransactions() {
    if (this.useDates && (!this.from || !this.to || this.from > this.to))
      return;
    this.page = 0;
    this.selected.clear();
    this.load();
  }
  private load() {
    const next: FinanceTransactionQuery = {
      page: this.page,
      pageSize: 50,
      q: this.q,
      accountId: this.accountFilter || undefined,

      categoryIds: this.categoryFilters,
      sort: this.sort,
      type: this.typeFilter || undefined,
      includeDeleted: this.showDeleted,
      operatingOnly: this.operatingOnly,
      ...(this.useDates ? { from: this.from, to: this.to } : {dateFilter:this.dateFilter()}),
    };
    if (JSON.stringify(next) !== JSON.stringify(this.filters())) this.filters.set(next);
  }
  tableChanged(event: TableLazyLoadEvent) {
    this.page = Math.floor((event.first ?? 0) / 50);
    const field = event.sortField === "description" ? "DESCRIPTION" : "DATE";
    this.sort =
      `${field}_${event.sortOrder === 1 ? "ASC" : "DESC"}` as FinanceTransactionQuery.SortEnum;
    this.selected.clear();
    this.load();
  }
  toggle(transaction: FinanceTransaction, event: Event) {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    target.checked
      ? this.selected.set(transaction.id, transaction.version)
      : this.selected.delete(transaction.id);
  }
  async bulk() {
    const result = await this.context.write(
      "transactions.categorize",
      {
        items: [...this.selected].map(([id, version]) => ({ id, version })),
        categoryId: this.bulkCategory,
      },
      (p) => this.context.api.client.financeCategorizeTransactions(p),
    );
    if (result) this.selected.clear();
  }
  openTransaction(transaction: FinanceTransaction) {
    this.dialogs.open({ kind: "transaction", transaction });
  }
  showHistory(transaction: FinanceTransaction) {
    this.dialogs.open({ kind: "history", transaction });
  }
  deletion(transaction: FinanceTransaction) {
    this.dialogs.open({ kind: "deletion", transaction });
  }
  txAmount(t: FinanceTransaction) {
    if (this.accountId())
      return t.sourceAccountId === this.accountId()
        ? "-" + this.money(t.sourceAmount, t.sourceCurrency)
        : "+" + this.money(t.destinationAmount, t.destinationCurrency);
    return (
      (t.type === "WITHDRAWAL" ? "-" : t.type === "DEPOSIT" ? "+" : "") +
      this.money(t.sourceAmount, t.sourceCurrency)
    );
  }
}
