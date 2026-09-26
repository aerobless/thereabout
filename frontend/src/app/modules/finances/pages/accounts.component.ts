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
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: "./accounts.component.html",
  styleUrl: "./accounts.component.scss",
})
export class AccountsComponent {
  readonly context = inject(FinanceContext);
  private dialogs = inject(FinanceDialogs);
  counterparties = false;
  page = 0;
  accountSearch = "";
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
    this.context.api.accounts(q),
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
      includeInactive: true,
    });
  }
  goPage(delta: number) {
    this.page += delta;
    this.loadAccounts();
  }
  openAccount(account?: FinanceAccount) {
    this.dialogs.open({
      kind: "account",
      account,
      counterparty: this.counterparties,
    });
  }
}
