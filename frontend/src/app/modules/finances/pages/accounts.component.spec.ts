import { provideZonelessChangeDetection, signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { Router, provideRouter } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { MessageService } from "primeng/api";
import { of, Subject } from "rxjs";
import { CurrentUserService } from "../../../shared/current-user/current-user.service";
import { FinanceAccount, FinanceApi, FinanceContext, FinanceDialogs, FinancesService, FinanceUser } from "../shared/finance-ui";
import { AccountDialogComponent } from "../dialogs/account-dialog.component";
import { AccountsComponent } from "./accounts.component";

const account: FinanceAccount = {
  id: 11, name: "Heidi savings", kind: "CASH", currency: "CHF", userId: 2, userName: "Heidi",
  active: true, deleted: false, includeNetWorth: true, version: 0, balance: "10",
};

describe("Shared finance accounts", () => {
  let users: Subject<FinanceUser[]>;
  const api = {
    financeUsers: vi.fn(),
    financeListCounterparties: vi.fn(() => of({items:[],total:0,page:0,pageSize:50})),
    financeListAccounts: vi.fn(),
    financeListCategories: vi.fn(() => of({ items: [] })),
    financeCurrencies: vi.fn(() => of({ items: [] })),
    financeCreateAccounts: vi.fn(() => of({ account })),
  };
  beforeEach(() => {
    vi.clearAllMocks();
    users = new Subject<FinanceUser[]>();
    api.financeUsers.mockReturnValue(users);
    api.financeListAccounts.mockImplementation((_page, _size, scope, _q, _kind, _id, _asOf, _deleted, _inactive, userId) => {
      const items = scope === "ALL_OWN" || userId === 2 ? [account] : [];
      return of({ items, total: items.length, page: 0, pageSize: 200 });
    });
    TestBed.configureTestingModule({providers: [
      provideZonelessChangeDetection(), provideRouter([{path: "finances/accounts", component: AccountsComponent}]), FinanceApi, FinanceContext, FinanceDialogs,
      { provide: FinancesService, useValue: api },
      { provide: MessageService, useValue: { add: vi.fn() } },
      { provide: CurrentUserService, useValue: {state: signal({status: "resolved", identityId: 1})} },
    ]});
  });

  it("renders other users after a delayed response and loads their accounts without impersonation", async () => {
    const fixture = TestBed.createComponent(AccountsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    users.next([{id: 1, name: "Theo"}, {id: 2, name: "Heidi"}, {id: 3, name: "Nullpaw"}]);
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;
    const buttons = Array.from(root.querySelectorAll<HTMLButtonElement>(".segmented button"));
    expect(buttons.map(b => b.textContent?.trim())).toEqual(["Your accounts", "Heidi", "Nullpaw", "Counterparties"]);
    buttons[1].click();
    await fixture.whenStable();
    expect(root.querySelectorAll(".account-card")).toHaveLength(1);
    expect(root.querySelector(".account-card")?.textContent).toContain("Heidi savings");
    expect(root.querySelectorAll(".account-group")).toHaveLength(1);
    expect(root.querySelector(".segmented button.active")?.textContent?.trim()).toBe("Heidi");
    root.querySelector<HTMLButtonElement>(".toolbar > button")?.click();
    expect(TestBed.inject(FinanceDialogs).selected()).toEqual({kind: "account", account: undefined, counterparty: false, userId: 2});
    buttons[2].click();
    await fixture.whenStable();
    expect(root.querySelectorAll(".account-group")).toHaveLength(0);
    buttons[3].click();
    await fixture.whenStable();
    expect(api.financeListCounterparties).toHaveBeenCalled();
  });

  it("opens the Counterparties return destination and keeps tab selection in the URL", async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl("/finances/accounts?view=counterparties", AccountsComponent);
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.querySelector(".segmented button.active")?.textContent?.trim()).toBe("Counterparties");
    expect(harness.routeNativeElement?.querySelector("finance-counterparties")).not.toBeNull();

    component.selectUser(null);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe("/finances/accounts");
    expect(harness.routeNativeElement?.querySelector(".segmented button.active")?.textContent?.trim()).toBe("Your accounts");
    component.selectCounterparties();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe("/finances/accounts?view=counterparties");

    await harness.navigateByUrl("/finances/accounts", AccountsComponent);
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.querySelector(".segmented button.active")?.textContent?.trim()).toBe("Your accounts");
  });

  it("creates a main account for the selected owner", async () => {
    const fixture = TestBed.createComponent(AccountDialogComponent);
    fixture.componentRef.setInput("userId", 2);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.form.controls.name.setValue("New Heidi account");
    await fixture.componentInstance.saveAccount();
    expect(api.financeCreateAccounts).toHaveBeenCalledWith(expect.objectContaining({userId: 2, name: "New Heidi account", kind: "CASH"}));
  });
});
