import {CounterpartyListComponent} from './counterparty-list.component';
import { CurrentUserService } from "../../../shared/current-user/current-user.service";
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
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
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
    CounterpartyListComponent,
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
  readonly counterparties = signal(false);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly selectedUserId = signal<number | null>(null);
  private readonly currentUser = inject(CurrentUserService);
  readonly users = loadResource(this.context.revision, () => this.context.api.client.financeUsers());
  readonly otherUsers = computed(() => {
    const current = this.currentUser.state();
    return (this.users().data ?? []).filter(user => current.status !== "loading" && user.id !== current.identityId);
  });
  selectUser(id: number | null) {
    this.counterparties.set(false);
    void this.router.navigate([], {relativeTo: this.route, queryParams: {view: null}, queryParamsHandling: "merge"});
    this.selectedUserId.set(id);
    this.ownSearch = "";
    this.page = 0;
    this.loadAccounts();
  }
  selectCounterparties() {
    this.counterparties.set(true);
    void this.router.navigate([], {relativeTo: this.route, queryParams: {view: "counterparties"}, queryParamsHandling: "merge"});
    this.page = 0;
  }
  page = 0;
  private ownSearch = "";
  private counterpartySearch = "";
  get accountSearch() {
    return this.counterparties() ? this.counterpartySearch : this.ownSearch;
  }
  set accountSearch(value: string) {
    if (this.counterparties()) this.counterpartySearch = value;
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
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(params =>
      this.counterparties.set(params.get("view") === "counterparties"),
    );
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
      scope: this.counterparties() ? "COUNTERPARTY" : "OWN",
      userId: this.counterparties() ? undefined : (this.selectedUserId() ?? undefined),
      page: this.page,
      pageSize: 50,
      q: this.accountSearch,
      kind: this.counterparties() ? (this.kindFilter ?? undefined) : undefined,
      active: this.counterparties()
        ? (this.activeFilter ?? undefined)
        : undefined,
      includeInactive: true,
    });
  }
  openAccount(account?: FinanceAccount) {
    this.dialogs.open({
      kind: "account",
      account,
      counterparty: this.counterparties(),
      userId: this.counterparties() ? undefined : (this.selectedUserId() ?? undefined),
    });
  }
}
