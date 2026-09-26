import { TableModule } from "primeng/table";
import { SelectModule } from "primeng/select";
import { Subject, debounceTime } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { AccountLogoComponent } from "../shared/account-logo.component";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceAccount,
  FinanceAccountKind,
  FinanceAccountQuery,
  assetKinds,
  kindLabel,
  loadResource,
} from "../shared/finance-ui";
@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TableModule,
    SelectModule,
    AccountLogoComponent,
    CommonModule,
    FormsModule,
    RouterLink,
  ],
  templateUrl: "./accounts.component.html",
  styleUrl: "./accounts.component.scss",
})
export class AccountsComponent {
  readonly context = inject(FinanceContext);
  private dialogs = inject(FinanceDialogs);
  counterparties = false;
  page = 0;
  private ownSearch = "";
  private counterpartySearch = "";
  get accountSearch() {
    return this.counterparties ? this.counterpartySearch : this.ownSearch;
  }
  set accountSearch(value: string) {
    if (this.counterparties) this.counterpartySearch = value;
    else this.ownSearch = value;
  }
  kindFilter: FinanceAccountKind | null = null;
  activeFilter: boolean | null = null;
  readonly kindOptions = [
    { label: "All kinds", value: null },
    { label: "Expense", value: "EXPENSE" },
    { label: "Revenue", value: "REVENUE" },
  ];
  readonly statusOptions = [
    { label: "All statuses", value: null },
    { label: "Active", value: true },
    { label: "Inactive", value: false },
  ];
  readonly searchChanges = new Subject<void>();
  constructor() {
    this.searchChanges
      .pipe(debounceTime(250), takeUntilDestroyed())
      .subscribe(() => this.filterAccounts());
  }
  filterAccounts() {
    this.page = 0;
    this.loadAccounts();
  }
  pageChanged(event: { first?: number }) {
    this.page = Math.floor((event.first ?? 0) / 50);
    this.loadAccounts();
  }

  readonly kinds = assetKinds;
  readonly label = kindLabel;
  readonly money = this.context.money.bind(this.context);
  private readonly filters = signal<FinanceAccountQuery>({
    scope: "OWN",
    page: 0,
    pageSize: 50,
    includeInactive: true,
  });
  private readonly query = computed(() => ({
    ...this.filters(),
    revision: this.context.revision(),
  }));
  readonly state = loadResource(this.query, (q) =>
    q.scope === "OWN"
      ? this.context.api.allOwnAccounts(q)
      : this.context.api.accounts(q),
  );
  get accounts() {
    return this.state().data?.items ?? [];
  }
  get total() {
    return this.state().data?.total ?? 0;
  }
  get busy() {
    return this.state().loading;
  }
  accountGroup(kind: FinanceAccountKind) {
    return this.accounts.filter((a) => a.kind === kind);
  }
  loadAccounts() {
    this.filters.set({
      scope: this.counterparties ? "COUNTERPARTY" : "OWN",
      page: this.page,
      pageSize: 50,
      q: this.accountSearch,
      kind: this.counterparties ? (this.kindFilter ?? undefined) : undefined,
      active: this.counterparties
        ? (this.activeFilter ?? undefined)
        : undefined,
      includeInactive: true,
    });
  }
  openAccount(account?: FinanceAccount) {
    this.dialogs.open({
      kind: "account",
      account,
      counterparty: this.counterparties,
    });
  }
}
