import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { FinanceContext, FinanceImportHint, FinanceImportHintList } from '../shared/finance-ui';
import { ImportHintsEditorComponent } from './import-hints-editor.component';

const hint: FinanceImportHint = {id: 11, accountId: 7, text: 'Treat IBKR as a transfer', version: 0, createdAt: '2026-10-04T12:00:00+02:00'};
describe('Account import hints', () => {
  const list = vi.fn(); const add = vi.fn(); const remove = vi.fn(); const toast = vi.fn();
  beforeEach(() => {
    list.mockReset().mockReturnValue(of({items: [hint]}));
    add.mockReset().mockReturnValue(of({...hint, id: 12}));
    remove.mockReset().mockReturnValue(of(hint)); toast.mockReset();
    TestBed.configureTestingModule({providers: [provideZonelessChangeDetection(),
      {provide: FinanceContext, useValue: {api: {client: {financeListImportHints: list, financeAddImportHint: add, financeRemoveImportHint: remove}}}},
      {provide: MessageService, useValue: {add: toast}},
    ]});
  });
  afterEach(() => TestBed.resetTestingModule());

  it('renders delayed hints and adds and removes sentences without reloading finance data', async () => {
    const pending = new Subject<FinanceImportHintList>(); list.mockReturnValue(pending);
    const fixture = TestBed.createComponent(ImportHintsEditorComponent); fixture.componentRef.setInput('accountId', 7);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Loading hints');
    pending.next({items: [hint]}); pending.complete(); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(hint.text);
    fixture.componentInstance.text.set('  Treat dividends as income  ');
    await fixture.componentInstance.add(); await fixture.whenStable();
    expect(add).toHaveBeenCalledWith(7, expect.objectContaining({accountId: 7, text: 'Treat dividends as income', requestKey: expect.any(String)}));
    expect(fixture.componentInstance.hints()).toHaveLength(2);
    await fixture.componentInstance.remove(hint); await fixture.whenStable();
    expect(remove).toHaveBeenCalledWith(7, 11, expect.objectContaining({id: 11, version: 0}));
    expect(fixture.componentInstance.hints().map(h => h.id)).toEqual([12]);
    expect(list).toHaveBeenCalledTimes(1);
    expect(toast).toHaveBeenCalledTimes(2);
  });

  it('retains the sentence and reuses the request key when a save is retried', async () => {
    const fixture = TestBed.createComponent(ImportHintsEditorComponent); fixture.componentRef.setInput('accountId', 7);
    await fixture.whenStable(); fixture.componentInstance.text.set('Keep the sentence');
    add.mockReturnValueOnce(throwError(() => new Error('Temporary failure')));
    await fixture.componentInstance.add(); await fixture.whenStable();
    expect(fixture.componentInstance.text()).toBe('Keep the sentence');
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Temporary failure');
    await fixture.componentInstance.add(); await fixture.whenStable();
    expect(add.mock.calls[0][1].requestKey).toBe(add.mock.calls[1][1].requestKey);
    expect(fixture.componentInstance.text()).toBe('');
  });

  it('ignores stale account responses and clears account-specific input on a switch', async () => {
    const old = new Subject<FinanceImportHintList>(); list.mockReturnValueOnce(old).mockReturnValueOnce(of({items: []}));
    const fixture = TestBed.createComponent(ImportHintsEditorComponent); fixture.componentRef.setInput('accountId', 7);
    await fixture.whenStable(); fixture.componentInstance.text.set('Old account input');
    fixture.componentRef.setInput('accountId', 8); await fixture.whenStable();
    old.next({items: [hint]}); await fixture.whenStable();
    expect(fixture.componentInstance.hints()).toEqual([]);
    expect(fixture.componentInstance.text()).toBe('');
    expect(list.mock.calls.map(call => call[0])).toEqual([7, 8]);
  });

  it('offers retry after a load failure and keeps hints visible after a removal failure', async () => {
    list.mockReturnValueOnce(throwError(() => new Error('Loading failed')));
    const fixture = TestBed.createComponent(ImportHintsEditorComponent); fixture.componentRef.setInput('accountId', 7);
    await fixture.whenStable();
    const retry = [...fixture.nativeElement.querySelectorAll('button')].find((button: Element) => button.textContent?.trim() === 'Retry') as HTMLButtonElement;
    retry.click(); await fixture.whenStable();
    expect(fixture.componentInstance.hints()).toEqual([hint]);
    remove.mockReturnValue(throwError(() => new Error('Removal failed')));
    await fixture.componentInstance.remove(hint); await fixture.whenStable();
    expect(fixture.componentInstance.hints()).toEqual([hint]);
    expect(fixture.nativeElement.textContent).toContain('Removal failed');
  });
});
