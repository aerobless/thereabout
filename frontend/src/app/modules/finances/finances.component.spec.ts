import { MessageService } from "primeng/api";
import { TestBed } from "@angular/core/testing";
import { signal } from "@angular/core";
import { provideRouter } from "@angular/router";
import { of, throwError, Subject } from "rxjs";
import {
  FinanceApi,
  FinanceAccount,
  FinanceContext,
  FinanceDialogs,
  FinancesService,
  loadResource,
} from "./shared/finance-ui";
import { DeletionDialogComponent } from "./dialogs/deletion-dialog.component";
import { TransactionDialogComponent } from "./dialogs/transaction-dialog.component";

describe("Finance boundaries", () => {
  const messages = { add: vi.fn() };
  const api = {
    financeListAccounts: vi.fn(() =>
      of({ items: [] as FinanceAccount[], total: 0, page: 0, pageSize: 200 }),
    ),
    financeListCategories: vi.fn(() => of({ items: [] })),
    financeCurrencies: vi.fn(() => of({ items: [] })),
    financeDeleteTransaction: vi.fn(() => of({ transaction: {} })),
    financeRestoreTransaction: vi.fn(() => of({ transaction: {} })),
    financeUpdateTransactions: vi.fn(() => of({ transaction: {} })),
  };
  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        FinanceApi,
        FinanceContext,
        { provide: MessageService, useValue: messages },
        FinanceDialogs,
        { provide: FinancesService, useValue: api },
      ],
    });
  });
  it("shows a transient app toast only after a successful write", async () => {
    const context = TestBed.inject(FinanceContext);
    await context.write("save", {}, () => of({}));
    expect(messages.add).toHaveBeenCalledWith({
      severity: "success",
      summary: "Saved",
      life: 3000,
    });
    messages.add.mockClear();
    await context.write("save", {}, () =>
      throwError(() => new Error("Failed")),
    );
    expect(messages.add).not.toHaveBeenCalled();
    expect(context.error()).toBe("Failed");
  });
  it("preserves imported decimal strings and timestamp while editing notes", async () => {
    const fixture = TestBed.createComponent(TransactionDialogComponent);
    fixture.componentRef.setInput("transaction", {
      id: 12,
      version: 0,
      type: "WITHDRAWAL",
      effect: "OPERATING",
      description: "Fixture",
      occurredAt: "2026-01-01T12:34:56",
      sourceAccountId: 1,
      destinationAccountId: 3,
      sourceName: "Cash",
      destinationName: "Shop",
      sourceAmount: "10.000000000001",
      destinationAmount: "10.000000000002",
      sourceCurrency: "CHF",
      destinationCurrency: "CHF",
      foreignCurrency: "EUR",
      foreignAmount: "-11.000000000001",
      deleted: false,
    });
    fixture.detectChanges();
    await fixture.componentInstance.saveTransaction();
    expect(api.financeUpdateTransactions).toHaveBeenCalledWith(
      12,
      expect.objectContaining({
        sourceAmount: "10.000000000001",
        destinationAmount: "10.000000000002",
        foreignAmount: "11.000000000001",
        date: "2026-01-01T12:34:56",
      }),
    );
  });
  it("loads every page of own accounts without exposing account pagination", async () => {
    const account = (id: number): FinanceAccount => ({
      id,
      name: `Account ${id}`,
      kind: "CASH",
      currency: "CHF",
      active: true,
      deleted: false,
      includeNetWorth: true,
      version: 0,
      balance: "0",
    });
    api.financeListAccounts.mockReturnValueOnce(
      of({
        items: Array.from({ length: 200 }, (_, i) => account(i + 1)),
        total: 201,
        page: 0,
        pageSize: 200,
      }),
    );
    api.financeListAccounts.mockReturnValueOnce(
      of({ items: [account(201)], total: 201, page: 1, pageSize: 200 }),
    );
    const { firstValueFrom } = await import("rxjs");
    const result = await firstValueFrom(
      TestBed.inject(FinanceApi).allOwnAccounts(),
    );
    expect(result.items).toHaveLength(201);
    expect(result.items.at(-1)?.id).toBe(201);
    expect(result.total).toBe(201);
  });
  it("requires selecting the searched counterparty instead of retaining a stale account id", () => {
    const fixture = TestBed.createComponent(TransactionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.destinationId.setValue(3);
    fixture.componentInstance.counterpartyChanged("another shop");
    expect(fixture.componentInstance.form.controls.destinationId.value).toBe(0);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });
  it("selecting a counterparty preserves existing precise posting amounts", async () => {
    const fixture = TestBed.createComponent(TransactionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({
      sourceAmount: "10.000000000001",
      destinationAmount: "10.000000000002",
    });
    await fixture.componentInstance.selectCounterparty({
      id: 3,
      name: "Shop",
      currency: "CHF",
    });
    expect(fixture.componentInstance.form.controls.destinationId.value).toBe(3);
    expect(
      fixture.componentInstance.form.controls.destinationAmount.value,
    ).toBe("10.000000000002");
  });
  it("waits for in-app confirmation and carries the displayed version", async () => {
    const fixture = TestBed.createComponent(DeletionDialogComponent);
    fixture.componentRef.setInput("transaction", {
      id: 12,
      version: 7,
      description: "Disposable fixture",
      deleted: false,
    });
    fixture.detectChanges();
    expect(api.financeDeleteTransaction).not.toHaveBeenCalled();
    const button = fixture.nativeElement.querySelector(
      "button.primary",
    ) as HTMLButtonElement;
    button.click();
    await fixture.whenStable();
    expect(api.financeDeleteTransaction).toHaveBeenCalledWith(
      12,
      expect.objectContaining({
        id: 12,
        version: 7,
        requestKey: expect.any(String),
      }),
    );
    expect(TestBed.inject(FinanceDialogs).selected()).toBeNull();
  });
  it("reuses the idempotency key for an unchanged retry after a lost response", async () => {
    const context = TestBed.inject(FinanceContext);
    const execute = vi.fn((_input: { name: string; requestKey: string }) =>
      throwError(() => new Error("Network interrupted")),
    );
    await context.write("categories.save", { name: "Example" }, execute);
    await context.write("categories.save", { name: "Example" }, execute);
    await context.write("categories.save", { name: "Different" }, execute);
    expect(execute.mock.calls[0][0].requestKey).toBe(
      execute.mock.calls[1][0].requestKey,
    );
    expect(execute.mock.calls[2][0].requestKey).not.toBe(
      execute.mock.calls[1][0].requestKey,
    );
    expect(context.error()).toBe("Network interrupted");
  });
  it("cancels obsolete loads and can reload after an error", () => {
    const query = signal(1);
    const first = new Subject<string>(),
      second = new Subject<string>(),
      third = new Subject<string>();
    const resource = TestBed.runInInjectionContext(() =>
      loadResource(query, (n) => (n === 1 ? first : n === 2 ? second : third)),
    );
    TestBed.tick();
    expect(first.observed).toBe(true);
    query.set(2);
    TestBed.tick();
    expect(first.observed).toBe(false);
    first.next("obsolete");
    second.error(new Error("Temporary failure"));
    expect(resource().error).toBe("Temporary failure");
    query.set(3);
    TestBed.tick();
    third.next("current");
    expect(resource().data).toBe("current");
  });
});
