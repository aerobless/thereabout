import { Component, input, signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { UIChart } from "primeng/chart";
import { of } from "rxjs";
import { FinanceOverview } from "../../../../../generated/backend-api/thereabout";
import { FinanceContext } from "../shared/finance-context.service";
import { FinanceDialogs } from "../shared/finance-dialogs.service";
import { OverviewComponent } from "./overview.component";
import { TransactionsComponent } from "./transactions.component";

@Component({ selector: "finance-transactions", template: "" })
class TransactionsStub {
  accountId = input(0);
  recent = input(false);
}

const overview: FinanceOverview = {
  netWorth: "100.00",
  totals: { cash: "100.00", investment: "0", realEstate: "0", otherAsset: "0" },
  accounts: [],
  series: [{ date: "2026-01-01", value: "100.00" }],
  chartCurrency: "CHF",
  warnings: [],
  complete: true,
  asOf: "2026-09-26",
  earliestTransaction: "2020-01-01",
};

describe("Finance overview chart updates", () => {
  const revision = signal(0);
  const fetchOverview = vi.fn(() => of(overview));

  beforeEach(() => {
    vi.stubGlobal("matchMedia", () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
    revision.set(0);
    fetchOverview.mockReset().mockReturnValue(of(overview));
    // Keep the real input effect active while avoiding jsdom's missing canvas.
    vi.spyOn(UIChart.prototype, "initChart").mockImplementation(function (this: UIChart) {
      this.initialized = true;
    });
    vi.spyOn(UIChart.prototype, "reinit").mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        FinanceDialogs,
        {
          provide: FinanceContext,
          useValue: {
            revision,
            money: (value: string) => value,
            api: { client: { financeOverview: fetchOverview } },
          },
        },
      ],
    }).overrideComponent(OverviewComponent, {
      remove: { imports: [TransactionsComponent] },
      add: { imports: [TransactionsStub] },
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function render(accountId = 0) {
    const fixture = TestBed.createComponent(OverviewComponent);
    fixture.componentRef.setInput("accountId", accountId);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it.each([0, 624])("keeps chart inputs stable on dropdown hover for account %s", async (accountId) => {
    const fixture = await render(accountId);
    const chart = fixture.debugElement.query(By.directive(UIChart)).componentInstance as UIChart;
    const originalData = chart.data();
    const originalOptions = chart.options();
    vi.mocked(UIChart.prototype.reinit).mockClear();
    const dropdown = fixture.nativeElement.querySelector('[role="combobox"]') as HTMLElement;
    dropdown.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const option = Array.from(document.querySelectorAll('[role="option"]')).find(
      (element) => element.textContent?.trim() === "This month",
    );
    expect(option).toBeDefined();
    option!.dispatchEvent(new MouseEvent("mouseenter"));
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(chart.data()).toBe(originalData);
    expect(chart.options()).toBe(originalOptions);
    expect(UIChart.prototype.reinit).not.toHaveBeenCalled();
    expect(fetchOverview).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.range).toBe("year");

    fetchOverview.mockReturnValue(of({ ...overview, series: [{ date: "2026-09-01", value: "200.00" }] }));
    (option as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.range).toBe("month");
    expect(fetchOverview).toHaveBeenCalledTimes(2);
    expect(chart.data()?.datasets[0].data).toEqual([200]);
    expect(UIChart.prototype.reinit).toHaveBeenCalled();
  });

  it("ignores unchanged and invalid date ranges", async () => {
    const fixture = await render();
    const originalData = fixture.componentInstance.chart();
    fixture.componentInstance.chooseRange();
    fixture.componentInstance.load();
    fixture.componentInstance.from = "";
    fixture.componentInstance.load();
    fixture.componentInstance.from = "2027-01-01";
    fixture.componentInstance.to = "2026-01-01";
    fixture.componentInstance.load();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchOverview).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.chart()).toBe(originalData);
  });

  it("reloads on custom dates, account navigation and finance writes", async () => {
    const fixture = await render();
    fixture.componentInstance.from = "2025-01-01";
    fixture.componentInstance.load();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchOverview).toHaveBeenCalledTimes(2);
    fixture.componentRef.setInput("accountId", 624);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchOverview).toHaveBeenLastCalledWith("2025-01-01", fixture.componentInstance.to, 624);
    expect(fixture.componentInstance.chart().datasets[0].label).toBe("Balance");
    revision.update((value) => value + 1);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fetchOverview).toHaveBeenCalledTimes(4);
  });
});
