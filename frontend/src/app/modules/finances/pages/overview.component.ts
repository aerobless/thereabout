import { FinanceDateInputComponent } from "../shared/finance-date-input.component";
import { AccountLogoComponent } from "../shared/account-logo.component";
import { SelectModule } from "primeng/select";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ChartModule } from "primeng/chart";
import { ChartData, ChartOptions } from "chart.js";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceAccount,
  kindLabel,
  loadResource,
  today,
} from "../shared/finance-ui";
import { TransactionsComponent } from "./transactions.component";
@Component({
  selector: "finance-overview",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FinanceDateInputComponent,
    AccountLogoComponent,
    SelectModule,
    CommonModule,
    FormsModule,
    RouterLink,
    ChartModule,
    TransactionsComponent,
  ],
  templateUrl: "./overview.component.html",
  styleUrl: "./overview.component.scss",
})
export class OverviewComponent {
  readonly context = inject(FinanceContext);
  private dialogs = inject(FinanceDialogs);
  readonly accountId = input(0);
  from = today().slice(0, 4) + "-01-01";
  to = today();
  range = "year";
  readonly rangeOptions = [
    { label: "This year", value: "year" },
    { label: "This month", value: "month" },
    { label: "All history", value: "all" },
    { label: "Custom", value: "custom" },
  ];
  private readonly period = signal({ from: this.from, to: this.to });
  private readonly query = computed(() => ({
    ...this.period(),
    id: this.accountId(),
    revision: this.context.revision(),
  }));
  readonly state = loadResource(this.query, (q) =>
    this.context.api.client.financeOverview(q.from, q.to, q.id || undefined),
  );
  get currentAccount() {
    return this.state().data?.accounts.find((a) => a.id === this.accountId());
  }
  get ownAccounts() {
    return this.state().data?.accounts ?? [];
  }
  readonly money = this.context.money.bind(this.context);
  readonly label = kindLabel;
  readonly chartOptions: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { display: false } },
      y: {
        ticks: {
          callback: (value) =>
            new Intl.NumberFormat("en-CH", { notation: "compact" }).format(
              Number(value),
            ),
        },
      },
    },
  };
  get chart(): ChartData {
    const data = this.state().data;
    return {
      labels: data?.series.map((p) => p.date) ?? [],
      datasets: [
        {
          label: this.accountId() ? "Balance" : "Net worth",
          data: data?.series.map((p) => Number(p.value)) ?? [],
          borderColor: "#477eca",
          backgroundColor: "rgba(71,126,202,.08)",
          fill: true,
          tension: 0.2,
          pointRadius: 0,
          pointHitRadius: 15,
          borderWidth: 2.5,
        },
      ],
    };
  }
  load() {
    if (!this.from || !this.to || this.from > this.to) return;
    this.period.set({ from: this.from, to: this.to });
  }
  chooseRange() {
    if (this.range === "custom") return;
    this.from =
      this.range === "all"
        ? (this.state().data?.earliestTransaction?.slice(0, 10) ?? "2018-01-01")
        : this.range === "month"
          ? today().slice(0, 7) + "-01"
          : today().slice(0, 4) + "-01-01";
    this.to = today();
    this.load();
  }
  openAccount(account?: FinanceAccount) {
    this.dialogs.open({ kind: "account", account });
  }
  openValuation() {
    const account = this.currentAccount;
    if (account) this.dialogs.open({ kind: "valuation", account });
  }
}
