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
  imports: [CommonModule, FormsModule, RouterLink],
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
    effect(() => {
      const params = this.routeParams();
      this.q = "";
      this.page = 0;
      this.accountFilter = Number(params?.get("account") ?? 0);
      this.categoryFilter = params?.get("category") ?? "";
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
      categoryId:
        this.categoryFilter === "" ? undefined : Number(this.categoryFilter),
      type: this.typeFilter || undefined,
      includeDeleted: this.showDeleted,
      operatingOnly: this.operatingOnly,
      ...(this.useDates ? { from: this.from, to: this.to } : {}),
    });
  }
  goPage(delta: number) {
    this.page += delta;
    this.selected.clear();
    const current = this.query();
    this.filters.set({ ...current, page: this.page });
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
