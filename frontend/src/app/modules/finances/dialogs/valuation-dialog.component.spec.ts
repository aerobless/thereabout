import { provideZonelessChangeDetection, signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { firstValueFrom, of, Subject } from "rxjs";
import { describe, beforeEach, afterEach, expect, it, vi } from "vitest";
import { FinanceAccount, FinanceContext, FinanceDialogs, FinanceValuation, FinanceValuationPreview, FinanceTransaction } from "../shared/finance-ui";
import { ValuationDialogComponent } from "./valuation-dialog.component";

const account: FinanceAccount = {id: 2, name: "Investments", kind: "INVESTMENT", currency: "CHF", active: true, deleted: false, includeNetWorth: true, version: 0, balance: "120"};
const valuation: FinanceValuation = {id: 7, accountId: 2, occurredAt: "2026-01-31 18:00:00.123456", reportedValue: "120.123456", previousBalance: "100", reference: "Statement", origin: "FIREFLY_INFERRED", transactionId: 11, version: 3, deleted: false};
const preview: FinanceValuationPreview = {accountId: 2, date: "2026-01-31T18:00:00.123456", reportedValue: "90", previousBalance: "100", difference: "-10", currency: "CHF", laterValuations: true};

describe("Valuation corrections", () => {
  let loaded: Subject<{items: FinanceValuation[]}>;
  let previewResponse: Subject<FinanceValuationPreview>;
  const client = {financeListValuations: vi.fn(), financePreviewValuation: vi.fn(), financeCreateValuation: vi.fn(), financeDeleteValuation: vi.fn(), financeRestoreValuation: vi.fn()};
  beforeEach(() => {
    loaded = new Subject(); previewResponse = new Subject();
    Object.values(client).forEach(fn => fn.mockReset());
    client.financeListValuations.mockImplementation((_account, id) => id ? loaded : of({items: [valuation]}));
    client.financePreviewValuation.mockReturnValue(previewResponse);
    client.financeCreateValuation.mockReturnValue(of({valuation, preview}));
    client.financeDeleteValuation.mockReturnValue(of({...valuation, deleted: true, version: 4}));
    client.financeRestoreValuation.mockReturnValue(of({...valuation, deleted: false, version: 5}));
    vi.stubGlobal("matchMedia", () => ({matches: false, addEventListener() {}, removeEventListener() {}}));
    TestBed.configureTestingModule({providers: [provideZonelessChangeDetection(), FinanceDialogs, {provide: FinanceContext, useValue: {
      accounts: signal([account]), currencies: signal([{code: "CHF", decimalPlaces: 2}]), revision: signal(0), saving: signal(false), error: signal(""),
      money: (value: string, currency = "CHF") => `${currency} ${value}`, api: {client},
      write: async (_operation: string, input: object, action: (input: object) => ReturnType<typeof of>) => firstValueFrom(action({...input, requestKey: "valuation-test"})),
    }}]});
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.unstubAllGlobals(); });
  async function editor() {
    const fixture = TestBed.createComponent(ValuationDialogComponent);
    fixture.componentRef.setInput("valuationId", valuation.id); fixture.detectChanges(); await fixture.whenStable();
    loaded.next({items: [valuation]}); await fixture.whenStable(); return fixture;
  }
  it("routes linked gains and losses into the editable valuation rather than the ordinary editor", () => {
    const dialogs = TestBed.inject(FinanceDialogs);
    dialogs.open({kind: "transaction", transaction: {valuationId: 7} as FinanceTransaction});
    expect(dialogs.selected()).toEqual({kind: "valuation", valuationId: 7});
  });
  it("loads a valuation asynchronously and retains untouched original date and amount precision", async () => {
    const fixture = await editor(), component = fixture.componentInstance;
    expect(fixture.nativeElement.textContent).toContain("Correct recorded total value");
    expect(component.form.controls.reportedValue.value).toBe("120.12");
    const pending = component.previewValuation();
    previewResponse.next({...preview, reportedValue: valuation.reportedValue}); await pending; await fixture.whenStable();
    expect(client.financePreviewValuation).toHaveBeenCalledWith(expect.objectContaining({id: 7, version: 3, reportedValue: "120.123456", date: "2026-01-31T18:00:00.123456"}));
    expect(fixture.nativeElement.textContent).toContain("Later recorded valuations stay unchanged");
    await component.saveValuation();
    expect(client.financeCreateValuation).toHaveBeenCalledWith(expect.objectContaining({id: 7, version: 3, expectedBalance: "100", reportedValue: "120.123456"}));
  });
  it("ignores a delayed preview after the user changes the requested correction", async () => {
    const fixture = await editor(), component = fixture.componentInstance;
    component.form.controls.reportedValue.setValue("90"); component.form.controls.reportedValue.markAsDirty();
    const pending = component.previewValuation();
    component.form.controls.reportedValue.setValue("80"); previewResponse.next(preview); await pending; await fixture.whenStable();
    expect(component.preview).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain("Save correction");
    await component.saveValuation(); expect(client.financeCreateValuation).not.toHaveBeenCalled();
  });
  it("offers reversible deletion with a danger confirmation even when there is no correction posting", async () => {
    const fixture = await editor(), component = fixture.componentInstance;
    const zero = {...valuation, transactionId: undefined, reportedValue: "100", previousBalance: "100"};
    component.deletion.set(zero); await fixture.whenStable();
    expect(document.querySelector('[role="dialog"] button.danger')?.textContent).toContain("Delete valuation");
    await component.confirmDeletion(); await fixture.whenStable();
    expect(client.financeDeleteValuation).toHaveBeenCalledWith(7, expect.objectContaining({id: 7, version: 3}));
    expect(component.editing()?.deleted).toBe(true);
    component.deletion.set(component.editing()); await fixture.whenStable(); await component.confirmDeletion();
    expect(client.financeRestoreValuation).toHaveBeenCalledWith(7, expect.objectContaining({id: 7, version: 4}));
    expect(component.editing()?.deleted).toBe(false);
  });
  it("keeps a different history entry selected after loading the original transaction's valuation", async () => {
    const fixture = await editor(), component = fixture.componentInstance;
    component.edit({...valuation, id: 8, reference: "Another statement"}); await fixture.whenStable();
    expect(component.editing()?.id).toBe(8);
    expect(component.form.controls.reference.value).toBe("Another statement");
  });
});
