import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { ChartModule } from "primeng/chart";
import { ChartData, ChartOptions } from "chart.js";
import {
  FinanceContext,
  FinanceCashflowRow,
  kindLabel,
  loadResource,
  today,
} from "../shared/finance-ui";
@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, ChartModule],
  templateUrl: "./reports.component.html",
  styleUrl: "./reports.component.scss",
})
export class ReportsComponent {
  readonly context = inject(FinanceContext);
  private router = inject(Router);
  from = today().slice(0, 4) + "-01-01";
  to = today();
  accountFilter = 0;
  reportTab = "cashflow";
  private filters = signal({ from: this.from, to: this.to, accountId: 0 });
  private query = computed(() => ({
    ...this.filters(),
    revision: this.context.revision(),
  }));
  readonly state = loadResource(this.query, (q) =>
    this.context.api.client.financeReportIncomeExpenses(
      q.from,
      q.to,
      q.accountId || undefined,
    ),
  );
  readonly money = this.context.money.bind(this.context);
  readonly label = kindLabel;
  get ownAccounts() {
    return this.context.accounts();
  }
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
    const rows = this.state().data?.months ?? [];
    return {
      labels: rows.map((r) => r.name),
      datasets: [
        {
          label: "Income",
          data: rows.map((r) => Number(r.income)),
          backgroundColor: "#5a9d8a",
          borderRadius: 5,
        },
        {
          label: "Expenses",
          data: rows.map((r) => Number(r.expenses)),
          backgroundColor: "#d79375",
          borderRadius: 5,
        },
      ],
    };
  }
  loadReport() {
    this.filters.set({
      from: this.from,
      to: this.to,
      accountId: this.accountFilter,
    });
  }
  monthDrill(month: string) {
    const end = new Date(
      Number(month.slice(0, 4)),
      Number(month.slice(5, 7)),
      0,
    ).getDate();
    const f = this.filters();
    return this.router.navigate(["/finances/transactions"], {
      queryParams: {
        from: f.from > month + "-01" ? f.from : month + "-01",
        to: f.to < month + "-" + end ? f.to : month + "-" + end,
        account: f.accountId || undefined,
        operating: true,
      },
    });
  }
  categoryDrill(category: FinanceCashflowRow) {
    const f = this.filters();
    return this.router.navigate(["/finances/transactions"], {
      queryParams: {
        category: category.categoryId ?? 0,
        from: f.from,
        to: f.to,
        account: f.accountId || undefined,
        operating: true,
      },
    });
  }
}
