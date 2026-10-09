import { Component, provideZonelessChangeDetection, signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { of, Subject } from "rxjs";
import { AppModalComponent } from "../../../shared/modal/app-modal.component";
import { FinanceAccount, FinanceAccountPage, FinanceContext, FinanceDialogs } from "../shared/finance-ui";
import { TransactionDialogComponent } from "./transaction-dialog.component";

@Component({
  imports: [AppModalComponent, TransactionDialogComponent],
  template: `<app-modal header="Transaction" [visible]="true"><finance-transaction-dialog /></app-modal>`,
})
class Host {}

const cash: FinanceAccount = {
  id: 1, name: "Cash", kind: "CASH", currency: "CHF", active: true, deleted: false,
  includeNetWorth: true, version: 0, balance: "0",
};
const counterparty: FinanceAccount = { ...cash, id: 2, name: "Google", kind: "EXPENSE" };

describe("Transaction counterparty selection", () => {
  let response: Subject<FinanceAccountPage>;
  const accounts = vi.fn();
  const scrollIntoView = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollIntoView");
  beforeEach(() => {
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {value: vi.fn(), configurable: true});
    response = new Subject<FinanceAccountPage>();
    accounts.mockReset().mockImplementation(q => q.q === "google"
      ? response : of({ items: [], total: 0, page: 0, pageSize: 200 }));
    vi.stubGlobal("matchMedia", () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
    TestBed.configureTestingModule({providers: [
      provideZonelessChangeDetection(), FinanceDialogs,
      { provide: FinanceContext, useValue: {
        revision: signal(0), saving: signal(false), accounts: signal([cash]), ownAccounts: signal([cash]),
        categories: signal([]), currencies: signal([{code: "CHF", decimalPlaces: 2, enabled: true}]),
        api: {accounts, client: {financeListTransferAccounts: () => of({items: []})}},
      } },
    ]});
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.unstubAllGlobals();
    if (scrollIntoView) Object.defineProperty(HTMLElement.prototype, "scrollIntoView", scrollIntoView);
    else Reflect.deleteProperty(HTMLElement.prototype, "scrollIntoView");
  });

  it("keeps decimal strings and marks original-amount edits for submission", async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const component = fixture.debugElement.query(By.directive(TransactionDialogComponent)).componentInstance as TransactionDialogComponent;
    const input = document.querySelector<HTMLInputElement>('input[aria-label="Original amount"]')!;
    input.value = '12.123456789012345678901234';
    input.dispatchEvent(new Event('input', {bubbles:true}));
    await fixture.whenStable();
    expect(component.form.controls.foreignAmount.value).toBe('12.123456789012345678901234');
    expect(component.form.controls.foreignAmount.dirty).toBe(true);
    component.originalAmountChanged('foreignCurrency', 'CHF');
    expect(component.form.controls.foreignCurrency.dirty).toBe(true);
    component.originalAmountChanged('foreignCurrency', '');
    expect(component.form.controls.foreignAmount.value).toBe('');
    fixture.destroy();
  });

  it("keeps a hovered suggestion clickable and explains missing fields until the form is complete", async () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.debugElement.query(By.directive(TransactionDialogComponent)).componentInstance as TransactionDialogComponent;
    const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!;
    const save = dialog.querySelector<HTMLButtonElement>('button.primary')!;
    const description = dialog.querySelector<HTMLInputElement>('[formcontrolname="description"]')!;
    const amount = dialog.querySelector<HTMLInputElement>('[formcontrolname="sourceAmount"]')!;
    const input = dialog.querySelector<HTMLInputElement>('[aria-label="To counterparty"]')!;
    expect(save.disabled).toBe(true);
    expect(dialog.querySelector('.validation-note')?.textContent).toContain('Enter a description.');
    description.value = "Google purchase";
    description.dispatchEvent(new Event('input', {bubbles: true}));
    amount.value = "222.00";
    amount.dispatchEvent(new Event('input', {bubbles: true}));
    input.value = "google";
    input.dispatchEvent(new Event('input', {bubbles: true}));
    await vi.waitFor(() => expect(accounts).toHaveBeenCalledWith(expect.objectContaining({q: "google"})));
    response.next({items: [counterparty], total: 1, page: 0, pageSize: 200});
    await fixture.whenStable();
    const option = document.querySelector<HTMLElement>('[role="option"][aria-label="Google"]')!;
    expect(option).not.toBeNull();
    option.dispatchEvent(new MouseEvent('mouseenter', {bubbles: true}));
    await fixture.whenStable();
    expect(document.getElementById(option.id)).toBe(option);
    expect(dialog.querySelector('.validation-note')?.textContent).toContain('Select a counterparty from the suggestions.');
    option.click();
    await fixture.whenStable();
    expect(component.form.controls.destinationId.value).toBe(counterparty.id);
    expect(component.counterpartySelection).toMatchObject({id: counterparty.id, name: 'Google'});
    expect(save.disabled).toBe(false);
    expect(dialog.querySelector('.validation-note')).toBeNull();
    input.value = "another shop";
    input.dispatchEvent(new Event('input', {bubbles: true}));
    await fixture.whenStable();
    expect(component.form.controls.destinationId.value).toBe(0);
    expect(save.disabled).toBe(true);
    expect(dialog.querySelector('.validation-note')?.textContent).toContain('Select a counterparty from the suggestions.');
  });
});
