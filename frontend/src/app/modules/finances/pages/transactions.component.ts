import { FinanceDateInputComponent } from "../shared/finance-date-input.component";
import { SelectModule } from "primeng/select";
import { MultiSelectModule } from "primeng/multiselect";
import { TableModule, TableLazyLoadEvent } from "primeng/table";
import { Subject, debounceTime } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
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
  readonly recent = input(false);
  private readonly route = inject(ActivatedRoute);
  private readonly routeParams = toSignal(this.route.queryParamMap);
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
  get accountOptions() {
    return [{ id: 0, name: "All accounts" }, ...this.ownAccounts];
  }
  get categoryOptions() {
    return [{ id: 0, name: "Uncategorized" }, ...this.categories];
  }
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
  constructor() {
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
      this.selected.clear();
      this.load();
    });
  }
  private readonly query = computed(() => ({
    ...this.filters(),
    accountId: this.accountId() || this.filters()?.accountId,
    pageSize: this.recent() ? 8 : 50,
    revision: this.context.revision(),
  }));
  readonly state = loadResource(this.query, (q) =>
    this.context.api.transactions(q),
  );
  get transactions() {
    return this.state().data?.items ?? [];
  }
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
    this.filters.set({
      page: this.page,
      pageSize: 50,
      q: this.q,
      accountId: this.accountFilter || undefined,

      categoryIds: this.categoryFilters,
      sort: this.sort,
      type: this.typeFilter || undefined,
      includeDeleted: this.showDeleted,
      operatingOnly: this.operatingOnly,
      ...(this.useDates ? { from: this.from, to: this.to } : {}),
    });
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
  openCategories() {
    this.dialogs.open({ kind: "categories" });
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
