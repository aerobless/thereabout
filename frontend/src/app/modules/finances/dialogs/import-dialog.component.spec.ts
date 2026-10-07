import { MessageService } from 'primeng/api';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { FinanceContext, FinanceDialogs, FinanceImportJob } from '../shared/finance-ui';
import { ImportDialogComponent } from './import-dialog.component';

const account = { id: 7, userName: 'Another user', name: 'Cash', currency: 'CHF', active: true, kind: 'CASH' };
const draft: FinanceImportJob = {
  jobId: 'draft-1', accountId: 7, fileName: 'synthetic.csv', status: 'READY', revision: 1, processed: 1, total: 1,
  page: 0, pageSize: 50, readyToApprove: false, balanceImpact: '0',
  rows: [{ rowId: 'row-1', source: ['2026-01-01', 'Coffee', '12.123456789'], type: 'WITHDRAWAL', date: '2026-01-01', description: 'Coffee', amount: '', counterpartyName: 'Coffee shop', categoryName: 'Food', issues: ['Enter a booked amount'] }],
};
describe('Finance import review', () => {
  const accounts = signal([account]);
  const prepare = vi.fn(); const get = vi.fn(); const review = vi.fn(); const cancel = vi.fn(); const write = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
    accounts.set([account]);
    prepare.mockReset(); get.mockReset().mockReturnValue(of(draft)); review.mockReset(); cancel.mockReset().mockReturnValue(of(draft)); write.mockReset();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), FinanceDialogs, MessageService, { provide: FinanceContext, useValue: {
      accounts, categories: signal([]), currencies: signal([{ code: 'CHF' }]), saving: signal(false),
      money: (amount: string) => amount, write, error: signal(''),
      api: { allAccounts: () => of({ items: [] }), client: { financePrepareImport: prepare, financeGetImport: get, financeReviewImport: review, financeCancelImport: cancel, financeListImportHints: () => of({items: []}), financeAddImportHint: (accountId: number, input: {text: string}) => of({id: 11, accountId, text: input.text, version: 0, createdAt: '2026-10-04T12:00:00Z'}) } },
    } }] });
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.unstubAllGlobals(); });
  it('preselects an account, renders a delayed preview without a click and sends exact corrections', async () => {
    const pending = new Subject<FinanceImportJob>(); prepare.mockReturnValue(pending);
    const fixture = TestBed.createComponent(ImportDialogComponent); fixture.componentRef.setInput('accountId', 7); fixture.detectChanges(); await fixture.whenStable();
    const component = fixture.componentInstance;
    component.file.set(new File(['synthetic'], 'synthetic.csv', { type: 'text/csv' }));
    const request = component.prepare();
    expect(prepare).toHaveBeenCalledWith(7, expect.any(File));
    pending.next(draft); pending.complete(); await request; await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Another user');
    const approve = [...fixture.nativeElement.querySelectorAll('button')].find((b: Element) => b.textContent?.trim() === 'Import') as HTMLButtonElement;
    expect(approve.disabled).toBe(true);
    component.edit(draft.rows[0]); component.patch({ amount: '12.123456789012345678901234' });
    review.mockReturnValue(of({ ...draft, revision: 2 })); get.mockReturnValue(of({ ...draft, revision: 2, readyToApprove: true }));
    await component.review(); await fixture.whenStable();
    expect(review).toHaveBeenCalledWith(expect.objectContaining({ revision: 1, rows: [expect.objectContaining({ rowId: 'row-1', amount: '12.123456789012345678901234', source: draft.rows[0].source })] }));
    expect(component.job()?.readyToApprove).toBe(true);
    fixture.destroy(); expect(cancel).toHaveBeenCalledWith({ jobId: 'draft-1' });
  });
  it('only offers account hints once interpretation is ready', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent); fixture.componentRef.setInput('accountId', 7);
    await fixture.whenStable();
    const hintButton = () => [...fixture.nativeElement.querySelectorAll('button')].find((button: Element) => button.textContent?.trim() === 'Import hints') as HTMLButtonElement | undefined;
    expect(hintButton()).toBeUndefined();
    fixture.componentInstance.job.set({...draft, status: 'RUNNING'}); await fixture.whenStable();
    expect(hintButton()).toBeUndefined();
    fixture.componentInstance.job.set(draft); await fixture.whenStable();
    hintButton()!.click();
    expect(fixture.componentInstance.dialogs.hintAccountId()).toBe(7);
    expect(fixture.nativeElement.querySelectorAll('.import-summary-card')).toHaveLength(4);
    fixture.destroy();
  });

  it('requires an account globally and clears a selected identity when text changes', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent); fixture.detectChanges(); await fixture.whenStable();
    const component = fixture.componentInstance;
    component.file.set(new File(['a,b'], 'synthetic.csv'));
    await component.prepare(); expect(prepare).not.toHaveBeenCalled();
    component.edit({ ...draft.rows[0], otherAccountId: 2 });
    component.counterChange('Different shop');
    expect(component.editor()?.otherAccountId).toBeUndefined();
    expect(component.editor()?.counterpartyName).toBe('Different shop');
    component.chooseFile({ target: { files: [] } } as unknown as Event);
    fixture.destroy();
  });
  it('reloads approval-time validation so new duplicate issues can be corrected', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent); fixture.detectChanges(); await fixture.whenStable();
    const component = fixture.componentInstance; component.job.set({ ...draft, readyToApprove: true });
    write.mockResolvedValue(undefined); get.mockReturnValue(of({ ...draft, revision: 2, readyToApprove: false }));
    await component.approve(); await fixture.whenStable();
    expect(get).toHaveBeenCalledWith('draft-1', 0, 50);
    expect(component.job()?.revision).toBe(2);
    expect(component.job()?.readyToApprove).toBe(false);
    fixture.destroy();
  });

  it('shows a spinner during interpretation and replaces it with tags and three actions', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent);
    const component = fixture.componentInstance;
    component.job.set({...draft,status:'RUNNING',processed:0,total:45,rows:[]});
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('p-progress-spinner')).not.toBeNull();
    expect(element.textContent).not.toContain('0 / 45');
    const row = {...draft.rows[0], amount:'12.12', reason:'Category is tentative', issues:['Review interpretation: Category is tentative']};
    component.job.set({...draft, rows:[row]});
    await fixture.whenStable();
    expect(element.querySelector('p-progress-spinner')).toBeNull();
    expect(element.querySelector('.type-cell p-tag')?.textContent).toContain('Withdrawal');
    expect(element.querySelectorAll('.import-row-actions button')).toHaveLength(3);
    expect(element.textContent).not.toContain('Draft expires');
    expect(element.textContent).not.toContain('Resolve the highlighted');
    expect(element.querySelector('.review-note')?.textContent).toContain('Category is tentative');
    expect(element.textContent).not.toContain('Review interpretation:');
    expect(element.querySelector('.import-row')?.classList.contains('requires-review')).toBe(true);
    expect(element.querySelector('button[aria-label="Edit row-1"]')?.classList.contains('needs-review')).toBe(false);
    review.mockReturnValue(of({...draft,revision:2,readyToApprove:true}));
    get.mockReturnValue(of({...draft,revision:2,readyToApprove:true}));
    element.querySelector<HTMLButtonElement>('button[aria-label="Accept row-1"]')!.click();
    await fixture.whenStable();
    expect(review).toHaveBeenCalledWith(expect.objectContaining({rows:[expect.objectContaining({skip:false,reason:'Category is tentative',amount:'12.12'})]}));
    expect(component.job()?.readyToApprove).toBe(true);
    fixture.destroy();
  });

  it('keeps required edits separate from accepting an uncertain interpretation and shows issues in the editor', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent);
    const component = fixture.componentInstance; component.job.set(draft);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector<HTMLButtonElement>('button[aria-label="Accept row-1"]')!.disabled).toBe(true);
    element.querySelector<HTMLButtonElement>('button[aria-label="Edit row-1"]')!.click();
    await fixture.whenStable();
    expect(document.querySelector('.finance-import-row-modal .row-editor')?.textContent).toContain('Enter a booked amount');
    fixture.destroy();
  });

  it.each([true, false])('explicitly keeps a duplicate with skip=%s and clears the unresolved styling', async skip => {
    const fixture = TestBed.createComponent(ImportDialogComponent);
    const component = fixture.componentInstance;
    const duplicate = 'Matches an imported row or existing transaction';
    const row = {...draft.rows[0], amount:'12.123456789', skip,
      reason:skip ? `Duplicate: ${duplicate}` : '', duplicate:skip ? '' : duplicate,
      issues:skip ? [] : ['Skip this duplicate or explicitly keep it']};
    component.job.set({...draft,rows:[row]});
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.duplicate-tag')?.textContent).toContain('Duplicate');
    const accept = element.querySelector<HTMLButtonElement>('button[aria-label="Accept row-1"]')!;
    expect(accept.disabled).toBe(false);
    expect(accept.title).toBe('Keep this duplicate');
    const kept = {...draft,revision:2,readyToApprove:true,rows:[{...row,skip:false,reviewed:true,duplicateOverride:true,duplicate,issues:[]}]};
    review.mockReturnValue(of(kept)); get.mockReturnValue(of(kept));
    accept.click(); await fixture.whenStable();
    expect(review).toHaveBeenCalledWith(expect.objectContaining({rows:[expect.objectContaining({skip:false,duplicateOverride:true,amount:'12.123456789'})]}));
    expect(element.querySelector('.import-row')?.classList.contains('requires-review')).toBe(false);
    expect(element.querySelector('.import-row')?.classList.contains('skipped')).toBe(false);
    expect(element.querySelector('.review-note')).toBeNull();
    expect(element.querySelector('.duplicate-tag')?.textContent).toContain('Duplicate');
    fixture.destroy();
  });

  it('still requires missing fields to be edited before keeping a possible duplicate', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent);
    const component = fixture.componentInstance;
    component.job.set({...draft,rows:[{...draft.rows[0],duplicate:'Possible duplicate: same date and amount',issues:['Enter a booked amount','Skip this duplicate or explicitly keep it']}]});
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button[aria-label="Accept row-1"]').disabled).toBe(true);
    expect(review).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('preserves a skipped duplicate warning and the explicit keep option when editing it', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent); const component = fixture.componentInstance;
    component.job.set(draft);
    component.edit({...draft.rows[0], skip:true, reason:'Duplicate: Matches an existing transaction', issues:[]});
    component.patch({skip:false}); await fixture.whenStable();
    const modal = document.querySelector('.finance-import-row-modal')!;
    expect(modal.textContent).toContain('Matches an existing transaction');
    expect(modal.textContent).toContain('Keep this transaction despite the duplicate warning');
    expect(component.editor()?.duplicate).toBe('Matches an existing transaction');
    fixture.destroy();
  });

  it('shows named transfer directions and only asks for a second amount across currencies', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent);
    const component = fixture.componentInstance;
    accounts.set([account, {...account,id:8,name:'Wallet'}, {...account,id:9,name:'Euro wallet',currency:'EUR'}]);
    component.job.set(draft);
    component.edit({...draft.rows[0],type:'TRANSFER',otherAccountId:8,amount:'12.123456789',incoming:false});
    await fixture.whenStable();
    const element = document.querySelector('.finance-import-row-modal') as HTMLElement;
    expect(element.querySelector('input[aria-label="Other account amount"]')).toBeNull();
    expect(element.textContent).toContain('Cash → Wallet');
    component.patch({incoming:true});
    await fixture.whenStable();
    expect(element.textContent).toContain('Wallet → Cash');
    component.patch({otherAccountId:9});
    await fixture.whenStable();
    expect(element.textContent).toContain('Other account amount · EUR');
    expect(element.querySelector('input[aria-label="Other account amount"]')).not.toBeNull();
    fixture.destroy();
  });

  it('restricts category editing to existing choices and clears a legacy proposed category name', async () => {
    const fixture = TestBed.createComponent(ImportDialogComponent);
    const component = fixture.componentInstance;
    component.job.set(draft); component.edit({...draft.rows[0],categoryName:'Invented category'});
    await fixture.whenStable();
    const element = document.querySelector('.finance-import-row-modal') as HTMLElement;
    expect(element.querySelector('p-select[ariaLabel="Category"]')).not.toBeNull();
    expect(element.querySelector('.row-editor')?.textContent).not.toContain('A new category');
    expect(component.categoryName({...draft.rows[0], categoryName:'Food'})).toBe('Food');
    component.patch({categoryId:10,categoryName:''});
    review.mockReturnValue(of({...draft,revision:2}));
    await component.review();
    expect(review).toHaveBeenCalledWith(expect.objectContaining({rows:[expect.objectContaining({categoryId:10,categoryName:''})]}));
    fixture.destroy();
  });

  it('renews an open draft in the background without discarding an in-progress edit', async () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(ImportDialogComponent);
      const component = fixture.componentInstance;
      component.job.set(draft); component.edit(draft.rows[0]); component.patch({description:'Unsaved correction'});
      get.mockReturnValue(of({...draft,expiresAt:'2026-10-03T15:00:00Z'}));
      await vi.advanceTimersByTimeAsync(60000);
      expect(get).toHaveBeenCalledWith('draft-1',0,1);
      expect(component.editor()?.description).toBe('Unsaved correction');
      expect(component.job()?.expiresAt).toBe('2026-10-03T15:00:00Z');
      expect(component.busy()).toBe(false);
      fixture.destroy();
    } finally { vi.useRealTimers(); }
  });

});
