import { signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { UIChart } from "primeng/chart";
import { of } from "rxjs";
import { FinanceContext } from "../shared/finance-context.service";
import { ReportsComponent } from "./reports.component";

const report = {
  months: [
    { name: "2026-01", income: "100.00", expenses: "40.00", net: "60.00" },
  ],
  categories: [],
  investments: [],
  exchangeRates: [],
  warnings: [],
  income: "100.00",
  expenses: "40.00",
  net: "60.00",
  currency: "CHF",
};

describe("Finance report chart updates", () => {
  const revision = signal(0);
  const fetchReport = vi.fn(() => of(report));

  beforeEach(() => {
    revision.set(0);
    fetchReport.mockReset().mockReturnValue(of(report));
    // Observe PrimeNG's redraw boundary without requiring a browser canvas in jsdom.
    vi.spyOn(UIChart.prototype, "initChart").mockImplementation(() => {});
    vi.spyOn(UIChart.prototype, "reinit").mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: FinanceContext,
          useValue: {
            revision,
            accounts: signal([]),
            money: (value: string) => value,
            api: { client: { financeReportIncomeExpenses: fetchReport } },
          },
        },
      ],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  async function render() {
    const fixture = TestBed.createComponent(ReportsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it("does not redraw or reload on date-field focus and blur", async () => {
    const fixture = await render();
    const chart = fixture.debugElement.query(By.directive(UIChart))
      .componentInstance as UIChart;
    const originalData = chart.data();
    vi.mocked(UIChart.prototype.reinit).mockClear();
    const dateInput = fixture.nativeElement.querySelector(
      'input[aria-label="Report start"]',
    ) as HTMLInputElement;
    dateInput.dispatchEvent(new FocusEvent("focus"));
    dateInput.dispatchEvent(new FocusEvent("blur"));
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(chart.data()).toBe(originalData);
    expect(UIChart.prototype.reinit).not.toHaveBeenCalled();
    expect(fetchReport).toHaveBeenCalledTimes(1);
  });

  it("does not reload when the same filter values are submitted again", async () => {
    const fixture = await render();
    const originalData = fixture.componentInstance.chart();
    fixture.componentInstance.loadReport();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchReport).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.chart()).toBe(originalData);
  });

  it("updates the chart when the date filter changes", async () => {
    const fixture = await render();
    fetchReport.mockReturnValue(
      of({ ...report, months: [{ ...report.months[0], income: "200.00" }] }),
    );
    fixture.componentInstance.from = "2025-01-01";
    fixture.componentInstance.to = "2026-12-31";
    fixture.componentInstance.loadReport();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchReport).toHaveBeenLastCalledWith(
      "2025-01-01",
      "2026-12-31",
      undefined,
    );
    const chart = fixture.debugElement.query(By.directive(UIChart))
      .componentInstance as UIChart;
    expect(chart.data()?.datasets[0].data).toEqual([200]);
  });

  it("still reloads for a changed account or a successful finance write", async () => {
    const fixture = await render();
    fixture.componentInstance.accountFilter = 5;
    fixture.componentInstance.loadReport();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchReport).toHaveBeenCalledTimes(2);
    expect(fetchReport).toHaveBeenLastCalledWith(
      fixture.componentInstance.from,
      fixture.componentInstance.to,
      5,
    );
    revision.update((value) => value + 1);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchReport).toHaveBeenCalledTimes(3);
  });
});
