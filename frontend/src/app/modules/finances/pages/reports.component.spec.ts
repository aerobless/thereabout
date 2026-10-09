import {FinanceReport} from "../../../../../generated/backend-api/thereabout";
import { signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { UIChart } from "primeng/chart";
import { of } from "rxjs";
import { FinanceContext } from "../shared/finance-context.service";
import { ReportsComponent } from "./reports.component";

const report: FinanceReport = {
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
    vi.stubGlobal("matchMedia", () => ({
      matches: false,
      addEventListener() {},
      removeEventListener() {},
    }));
    revision.set(0);
    fetchReport.mockReset().mockReturnValue(of(report));
    // Observe PrimeNG's redraw boundary without requiring a browser canvas in jsdom.
    vi.spyOn(UIChart.prototype, "initChart").mockImplementation(function (this: UIChart) {
      this.initialized = true;
    });
    vi.spyOn(UIChart.prototype, "reinit").mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: FinanceContext,
          useValue: {
            revision,
            accounts: signal([{ id: 5, name: "Test account" }]),
            money: (value: string) => value,
            api: { client: { financeReportIncomeExpenses: fetchReport } },
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

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
    fixture.nativeElement.querySelector('button[aria-label="Choose report period"]').click();
    fixture.detectChanges();
    await fixture.whenStable();
    const dateInput = document.querySelector(
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

  it("does not redraw on account-option hover, but reloads on selection", async () => {
    const fixture = await render();
    const chart = fixture.debugElement.query(By.directive(UIChart))
      .componentInstance as UIChart;
    const originalData = chart.data();
    vi.mocked(UIChart.prototype.reinit).mockClear();
    const dropdown = fixture.nativeElement.querySelector(
      '[role="combobox"][aria-label="Report account"]',
    ) as HTMLElement;
    dropdown.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const option = Array.from(document.querySelectorAll('[role="option"]')).find(
      (element) => element.textContent?.trim() === "Test account",
    );
    expect(option).toBeDefined();
    option!.dispatchEvent(new MouseEvent("mouseenter"));
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(chart.data()).toBe(originalData);
    expect(UIChart.prototype.reinit).not.toHaveBeenCalled();
    expect(fetchReport).toHaveBeenCalledTimes(1);
    (option as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchReport).toHaveBeenCalledTimes(2);
    expect(fetchReport).toHaveBeenLastCalledWith(
      fixture.componentInstance.from,
      fixture.componentInstance.to,
      5,
    );
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
  it("uses calendar-year presets and retains zero-valued asset rows", async () => {
    const fixture = await render();
    const component = fixture.componentInstance;
    const end = component.to;
    const year = Number(end.slice(0, 4));
    for (const [years, from, to] of [
      [1, `${year}-01-01`, component.to],
      [0, `${year - 1}-01-01`, `${year - 1}-12-31`],
      [2, `${year - 1}-01-01`, end],
      [5, `${year - 4}-01-01`, end],
    ] as const) {
      component.preset(years);
      fixture.detectChanges();
      await fixture.whenStable();
      expect(fetchReport).toHaveBeenLastCalledWith(from, to, undefined);
    }
    expect(component.investmentSections()).toEqual([]);
    fetchReport.mockReturnValue(of({...report, investments: [{id: 5, name: 'Zero account', kind: 'OTHER_ASSET', currency:'CHF', start:'0', end:'0', inflows:'0', outflows:'0', valuationChange:'0', adjustments:'0'}]}));
    revision.update(value => value + 1);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.investmentSections()).toHaveLength(1);
    expect(component.investmentSections()[0].rows[0].name).toBe('Zero account');
  });

});
