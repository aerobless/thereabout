import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { FinanceAccount, SplitwiseCatalog, SplitwiseService, SplitwiseSettings, SplitwiseStatus } from '../../../../generated/backend-api/thereabout';
import { SplitwiseSettingsComponent } from './splitwise-settings.component';
const account = (id: number, userId: number, currency = 'CHF'): FinanceAccount => ({id, userId, userName: userId === 1 ? 'Theo' : 'Heidi', name: `Account ${id}`, kind: 'CASH', currency, active: true, deleted: false, includeNetWorth: true, version: 0, balance: '0', suggestedStartDate: id === 1 ? '2026-08-30' : undefined});
const settings: SplitwiseSettings = {configured: true, tested: true, initialized: false, enabled: false, revision: 2, groupId: 99, members: [], categories: []};
const catalog: SplitwiseCatalog = {groups: [{id: 99, name: 'Couple', members: [{id: 11, name: 'Theo'}, {id: 22, name: 'Heidi'}]}], sourceCategories: [{id: 10, name: 'Food', parentName: 'Food'}], accounts: [account(1,1), account(2,1), account(3,2), account(4,1,'EUR')], categories: [{id: 1, name: 'Food', deleted: false, version: 0}]};
describe('Splitwise configuration', () => {
  const api = {splitwiseSettings: vi.fn(), splitwiseCatalog: vi.fn(), splitwiseStatus: vi.fn(), splitwiseSaveSettings: vi.fn(), splitwiseSaveCategories: vi.fn(), splitwiseTest: vi.fn()};
  beforeEach(() => {
    vi.clearAllMocks();
    api.splitwiseSettings.mockReturnValue(of(settings)); api.splitwiseCatalog.mockReturnValue(of(catalog)); api.splitwiseStatus.mockReturnValue(of({state: 'IDLE', processed: 0, rows: []}));
    api.splitwiseSaveSettings.mockReturnValue(of({...settings, revision: 3})); api.splitwiseSaveCategories.mockReturnValue(of({...settings, revision: 3, categories: [{sourceCategoryId: 10}]})); api.splitwiseTest.mockReturnValue(of(catalog));
    TestBed.configureTestingModule({providers: [provideZonelessChangeDetection(), provideRouter([]), {provide: SplitwiseService, useValue: api}, {provide: MessageService, useValue: {add: vi.fn()}}]});
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });
  it('allows navigation during server synchronization, protects drafts and stops polling on destruction', async () => {
    const fixture = TestBed.createComponent(SplitwiseSettingsComponent);
    await fixture.whenStable();
    const component = fixture.componentInstance;
    component.status.set({state: 'SYNCING', processed: 4, rows: []});
    expect(component.busy()).toBe(true);
    expect(component.isNavigationBlocked()).toBe(false);
    component.openCategories();
    expect(component.hasUnsavedChanges()).toBe(true);
    component.closeCategories();
    component.key.set('synthetic-draft');
    fixture.destroy();
    expect(component.key()).toBe('');
    const reads = api.splitwiseStatus.mock.calls.length;
    await new Promise(resolve => setTimeout(resolve, 2100));
    expect(api.splitwiseStatus).toHaveBeenCalledTimes(reads);
  });
  it('renders delayed status without a user click and filters bank accounts by owner and currency', async () => {
    const pending = new Subject<SplitwiseSettings>(); api.splitwiseSettings.mockReturnValue(pending);
    const fixture = TestBed.createComponent(SplitwiseSettingsComponent); fixture.detectChanges(); pending.next(settings); pending.complete(); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Connected');
    const component=fixture.componentInstance; component.selectGroup(99); component.selectAccount(11,1);
    expect(component.unsaved()).toBe(true); expect(component.draft(11).startDate).toBe('2026-08-30'); expect(component.bankOptions(11).map(a=>a.id)).toEqual([2]);
    component.selectAccount(11,3); expect(component.draft(11).bankAccountId).toBeNull(); expect(component.draft(11).startDate).toBeNull();
  });
  it('saves full history and clears credentials without including an open category draft', async () => {
    const fixture=TestBed.createComponent(SplitwiseSettingsComponent); await fixture.whenStable(); const c=fixture.componentInstance;
    c.selectGroup(99); c.selectAccount(11,1); c.editMember(11,{bankAccountId:2}); c.toggleHistory(11,true); c.openCategories(); c.mapCategory(10,null); c.key.set('fixture'); c.save(); await fixture.whenStable();
    expect(api.splitwiseSaveSettings).toHaveBeenCalledWith(expect.objectContaining({apiKey:'fixture',revision:2,members:[{memberId:11,accountId:1,bankAccountId:2,startDate:undefined}],categories:[]}));
    expect(c.key()).toBe(''); expect(c.isMapped(10)).toBe(true);
  });
  it('blocks incomplete member mappings and keeps exact-name suggestions unsaved until modal save', async () => {
    const fixture=TestBed.createComponent(SplitwiseSettingsComponent); await fixture.whenStable(); const c=fixture.componentInstance;
    c.selectGroup(99); c.selectAccount(11,1); c.save(); await fixture.whenStable();
    expect(api.splitwiseSaveSettings).not.toHaveBeenCalled(); expect(fixture.nativeElement.textContent).toContain('Select a bank account');
    c.test(); await fixture.whenStable(); expect(c.categories()).toEqual([]);
    c.openCategories(); expect(c.mapping(10)).toBe(1); c.closeCategories(); expect(c.categoryDraft()).toEqual([]); expect(c.categories()).toEqual([]);
  });
  it('distinguishes unmapped and Uncategorized and saves only categories after a delayed response', async () => {
    const response = new Subject<SplitwiseSettings>(); api.splitwiseSaveCategories.mockReturnValue(response);
    const fixture=TestBed.createComponent(SplitwiseSettingsComponent); await fixture.whenStable(); const c=fixture.componentInstance;
    c.selectGroup(99); c.selectAccount(11,1); c.openCategories(); c.unmapCategory(10);
    expect(c.isMapped(10)).toBe(false); c.mapCategory(10,null); expect(c.isMapped(10)).toBe(true);
    await fixture.whenStable();
    const dialog = document.body.querySelector('[role="dialog"]');
    const save = Array.from(dialog!.querySelectorAll('button')).find(button => button.textContent?.trim() === 'Save');
    save!.click(); expect(c.savingCategories()).toBe(true);
    response.next({...settings,revision:3,categories:[{sourceCategoryId:10}]}); response.complete(); await fixture.whenStable();
    expect(api.splitwiseSaveSettings).not.toHaveBeenCalled();
    expect(api.splitwiseSaveCategories).toHaveBeenCalledWith({revision:2,requestKey:expect.any(String),categories:[{sourceCategoryId:10,categoryId:undefined}]});
    expect(c.categoryOpen()).toBe(false); expect(c.categoryCount()).toBe(1); expect(c.draft(11).accountId).toBe(1); expect(c.unsaved()).toBe(true);
    expect(document.body.querySelector('[role="dialog"]')).toBeNull();
  });
  it('preserves conflicting category drafts and reloads current mappings explicitly', async () => {
    api.splitwiseSaveCategories.mockReturnValue(throwError(() => ({status:409,error:{message:'Record changed; reload before saving'}})));
    const fixture=TestBed.createComponent(SplitwiseSettingsComponent); await fixture.whenStable(); const c=fixture.componentInstance;
    c.openCategories(); c.mapCategory(10,null); c.saveCategories(); await fixture.whenStable();
    expect(c.categoryOpen()).toBe(true); expect(c.categoryConflict()).toBe(true); expect(c.isMapped(10)).toBe(true); expect(c.mapping(10)).toBeUndefined();
    api.splitwiseSettings.mockReturnValue(of({...settings,revision:4,categories:[{sourceCategoryId:10,categoryId:1}]}));
    c.reloadCategories(); await fixture.whenStable(); expect(c.categoryRevision()).toBe(4); expect(c.mapping(10)).toBe(1); expect(c.categoryConflict()).toBe(false);
  });
  it('refreshes MCP changes while preserving unsaved settings and an open category draft', async () => {
    const fixture=TestBed.createComponent(SplitwiseSettingsComponent); await fixture.whenStable(); const c=fixture.componentInstance;
    c.selectAccount(11,1); c.openCategories(); c.mapCategory(10,null);
    api.splitwiseSettings.mockReturnValue(of({...settings,revision:4,categories:[{sourceCategoryId:10,categoryId:1}]}));
    await new Promise(resolve => setTimeout(resolve, 2100)); await fixture.whenStable();
    expect(c.categories()).toEqual([{sourceCategoryId:10,categoryId:1}]); expect(c.categoryRevision()).toBe(2); expect(c.mapping(10)).toBeUndefined(); expect(c.draft(11).accountId).toBe(1);
  });
  it('shows review evidence and links only inside the read-only modal', async () => {
    const status: SplitwiseStatus={state:'IDLE',processed:0,rows:[{expenseId:123,memberId:11,accountId:1,description:'Legacy settlement',date:'2026-09-01T14:00',amount:'-50',currency:'CHF',action:'PENDING',future:false,message:'Confirm matching transaction',classification:'SETTLEMENT',transactionId:456}]};
    api.splitwiseStatus.mockReturnValue(of(status));
    const fixture=TestBed.createComponent(SplitwiseSettingsComponent); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Needs review · 1'); expect(fixture.nativeElement.textContent).not.toContain('Legacy settlement');
    fixture.componentInstance.reviewOpen.set(true); await fixture.whenStable();
    const modal: HTMLElement=document.body.querySelector('app-modal:last-of-type') ?? fixture.nativeElement;
    expect(document.body.textContent).toContain('Splitwise Reconciliation');
    expect(document.body.textContent).toContain('Legacy settlement'); expect(document.body.textContent).toContain('SETTLEMENT');
    expect(document.body.querySelector('a[href="https://secure.splitwise.com/expenses/123"]')).not.toBeNull();
    expect(document.body.querySelector('a[href="/finances/transactions?transaction=456"]')).not.toBeNull();
    expect(modal.textContent).not.toContain('Create entry'); expect(document.body.textContent).not.toContain('Matching bank transaction');
  });
});
