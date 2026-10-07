import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MessageService } from 'primeng/api';
import { of, Subject } from 'rxjs';
import { FinanceContext, FinanceDialogs, FinanceImportJob } from '../shared/finance-ui';
import { DialogHostComponent } from './dialog-host.component';
import { ImportDialogComponent } from './import-dialog.component';
import { ImportHintsEditorComponent } from './import-hints-editor.component';

const draft: FinanceImportJob = {jobId: 'draft-1', accountId: 7, fileName: 'synthetic.csv', status: 'READY',
  revision: 1, processed: 1, total: 1, page: 0, pageSize: 50, readyToApprove: false, balanceImpact: '0',
  rows: [{rowId: 'row-1', source: ['2026-01-01', 'Coffee', '12.00'], type: 'WITHDRAWAL', date: '2026-01-01', description: 'Coffee', amount: '12.00'}]};

describe('Import hints overlay', () => {
  const cancel = vi.fn(); const add = vi.fn(); const review = vi.fn(); const get = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('matchMedia', () => ({matches: false, addEventListener() {}, removeEventListener() {}}));
    cancel.mockReset().mockReturnValue(of(draft));
    review.mockReset().mockReturnValue(of({...draft, revision: 2})); get.mockReset().mockReturnValue(of({...draft, revision: 2}));
    add.mockReset().mockReturnValue(of({id: 11, accountId: 7, text: 'Keep the hint', version: 0, createdAt: '2026-10-04T12:00:00Z'}));
    TestBed.configureTestingModule({providers: [provideZonelessChangeDetection(), FinanceDialogs, MessageService,
      {provide: FinanceContext, useValue: {accounts: signal([{id: 7, userName: 'Theo', name: 'Cash', currency: 'CHF', active: true, kind: 'CASH'}]),
        categories: signal([]), currencies: signal([{code: 'CHF'}]), saving: signal(false), error: signal(''), money: (amount: string) => amount,
        api: {allAccounts: () => of({items: []}), client: {financeCancelImport: cancel, financeReviewImport: review, financeGetImport: get, financeListImportHints: () => of({items: []}), financeAddImportHint: add}}}},
    ]});
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.unstubAllGlobals(); });

  it('saves in the shared hints modal and returns to the same draft and page', async () => {
    const dialogs = TestBed.inject(FinanceDialogs); dialogs.open({kind: 'import', accountId: 7});
    const fixture = TestBed.createComponent(DialogHostComponent); await fixture.whenStable();
    const component = fixture.debugElement.query(By.directive(ImportDialogComponent)).componentInstance as ImportDialogComponent;
    component.job.set(draft); component.page.set(2);
    await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('finance-import-dialog .import-summary-header button')!.click(); await fixture.whenStable();
    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(2);
    expect(document.querySelector('finance-import-hints-dialog')?.textContent).toContain('future imports');
    expect(document.querySelector('finance-import-hints-dialog .dialog-footer')).toBeNull();
    const input = document.querySelector<HTMLInputElement>('finance-import-hints-dialog input[aria-label="New hint"]')!;
    expect(input.placeholder).toBe('New hint'); input.value = 'Keep the hint'; input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('finance-import-hints-dialog button[type="submit"]')!.click(); await fixture.whenStable();
    expect(add).toHaveBeenCalledWith(7, expect.objectContaining({text: 'Keep the hint'}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true})); await fixture.whenStable();
    expect(dialogs.hintAccountId()).toBeNull(); expect(dialogs.selected()?.kind).toBe('import');
    expect(fixture.debugElement.query(By.directive(ImportDialogComponent)).componentInstance).toBe(component);
    expect(component.job()).toEqual(draft); expect(component.page()).toBe(2);
    expect(component.editor()).toBeUndefined(); expect(cancel).not.toHaveBeenCalled();
    fixture.destroy(); expect(cancel).toHaveBeenCalledWith({jobId: draft.jobId});
  });

  it('edits a row in a separate modal and discards changes without closing the preview or changing pages', async () => {
    const dialogs = TestBed.inject(FinanceDialogs); dialogs.open({kind: 'import', accountId: 7});
    const fixture = TestBed.createComponent(DialogHostComponent); await fixture.whenStable();
    const component = fixture.debugElement.query(By.directive(ImportDialogComponent)).componentInstance as ImportDialogComponent;
    component.job.set(draft); component.page.set(2); await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('button[aria-label="Edit row-1"]')!.click(); await fixture.whenStable();
    const modal = document.querySelector('.finance-import-row-modal')!;
    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(2);
    expect(modal.textContent).toContain('Edit row-1'); expect(modal.textContent).toContain('Original CSV row');
    expect(modal.querySelector('.import-summary-header')).toBeNull(); expect(modal.querySelector('.import-summary-cards')).toBeNull();
    expect(document.querySelector('finance-import-dialog p-table')).not.toBeNull();
    expect(document.querySelector<HTMLButtonElement>('.finance-import-modal .p-dialog-close-button')!.disabled).toBe(true);
    const input = modal.querySelector<HTMLInputElement>('input[maxlength="1024"]')!;
    input.value = 'Unsaved correction'; input.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(component.editor()?.description).toBe('Unsaved correction');
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true})); await fixture.whenStable();
    expect(document.querySelector('.finance-import-row-modal')).toBeNull(); expect(dialogs.selected()?.kind).toBe('import');
    expect(component.editor()).toBeUndefined(); expect(component.job()).toEqual(draft); expect(component.page()).toBe(2);
    expect(cancel).not.toHaveBeenCalled(); expect(review).not.toHaveBeenCalled();
    expect(document.querySelector<HTMLButtonElement>('.finance-import-modal .p-dialog-close-button')!.disabled).toBe(false);
    fixture.destroy();
  });

  it('keeps both modals locked during a delayed review and returns to the same preview page after saving', async () => {
    const pending = new Subject<FinanceImportJob>(); review.mockReturnValue(pending);
    const dialogs = TestBed.inject(FinanceDialogs); dialogs.open({kind: 'import', accountId: 7});
    const fixture = TestBed.createComponent(DialogHostComponent); await fixture.whenStable();
    const component = fixture.debugElement.query(By.directive(ImportDialogComponent)).componentInstance as ImportDialogComponent;
    component.job.set(draft); component.page.set(2); component.edit(draft.rows[0]); component.patch({description: 'Corrected coffee'});
    await fixture.whenStable();
    const saving = vi.spyOn(component, 'review');
    document.querySelector<HTMLButtonElement>('.finance-import-row-modal button.primary')!.click(); await fixture.whenStable();
    expect([...document.querySelectorAll<HTMLButtonElement>('.p-dialog-close-button')].every(button => button.disabled)).toBe(true);
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true})); await fixture.whenStable();
    expect(component.editor()?.description).toBe('Corrected coffee'); expect(dialogs.selected()?.kind).toBe('import');
    pending.next({...draft, revision: 2}); pending.complete(); await saving.mock.results[0].value; await fixture.whenStable();
    expect(review).toHaveBeenCalledWith(expect.objectContaining({rows: [expect.objectContaining({description: 'Corrected coffee'})]}));
    expect(get).toHaveBeenCalledWith(draft.jobId, 2, 50); expect(component.page()).toBe(2);
    expect(document.querySelector('.finance-import-row-modal')).toBeNull(); expect(component.job()?.revision).toBe(2);
    expect(cancel).not.toHaveBeenCalled(); fixture.destroy();
  });

  it('keeps a failed review and its error visible in the row modal for correction', async () => {
    const pending = new Subject<FinanceImportJob>(); review.mockReturnValue(pending);
    const dialogs = TestBed.inject(FinanceDialogs); dialogs.open({kind: 'import', accountId: 7});
    const fixture = TestBed.createComponent(DialogHostComponent); await fixture.whenStable();
    const component = fixture.debugElement.query(By.directive(ImportDialogComponent)).componentInstance as ImportDialogComponent;
    component.job.set(draft); component.edit(draft.rows[0]); await fixture.whenStable();
    const saving = component.review(); pending.error(new Error('Review failed')); await saving; await fixture.whenStable();
    expect(document.querySelector('.finance-import-row-modal [role="alert"]')?.textContent).toContain('Review failed');
    expect(component.editor()?.rowId).toBe('row-1'); expect(component.busy()).toBe(false);
    expect(document.querySelector<HTMLButtonElement>('.finance-import-row-modal .p-dialog-close-button')!.disabled).toBe(false);
    expect(dialogs.blocked()).toBe(true); expect(cancel).not.toHaveBeenCalled(); fixture.destroy();
  });

  it('locks both modal dismissal paths while a hint is being saved', async () => {
    const pending = new Subject(); add.mockReturnValue(pending);
    const dialogs = TestBed.inject(FinanceDialogs); dialogs.open({kind: 'import', accountId: 7});
    const fixture = TestBed.createComponent(DialogHostComponent); await fixture.whenStable();
    dialogs.openImportHints(7); await fixture.whenStable();
    const saving = vi.spyOn(fixture.debugElement.query(By.directive(ImportHintsEditorComponent)).componentInstance as ImportHintsEditorComponent, 'add');
    const input = document.querySelector<HTMLInputElement>('finance-import-hints-dialog input')!;
    input.value = 'Keep the hint'; input.dispatchEvent(new Event('input')); await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('finance-import-hints-dialog button[type="submit"]')!.click(); await fixture.whenStable();
    expect([...document.querySelectorAll<HTMLButtonElement>('.p-dialog-close-button')].every(button => button.disabled)).toBe(true);
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true})); await fixture.whenStable();
    expect(dialogs.hintAccountId()).toBe(7); expect(dialogs.selected()?.kind).toBe('import');
    pending.next({id: 11, accountId: 7, text: 'Keep the hint'}); pending.complete(); await saving.mock.results[0].value; await fixture.whenStable();
    expect(dialogs.hintsBlocked()).toBe(false);
    expect(document.querySelectorAll<HTMLButtonElement>('.p-dialog-close-button')[1].disabled).toBe(false);
    document.querySelectorAll<HTMLButtonElement>('.p-dialog-close-button')[1].click(); await fixture.whenStable();
    expect(dialogs.hintAccountId()).toBeNull(); expect(dialogs.selected()?.kind).toBe('import');
    fixture.destroy();
  });
});
