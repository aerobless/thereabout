import { ChangeDetectionStrategy, Component, computed, effect, DestroyRef, inject, input, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AppModalComponent } from '../../../shared/modal/app-modal.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SelectModule } from 'primeng/select';
import { AutoCompleteCompleteEvent, AutoCompleteModule, AutoCompleteSelectEvent } from 'primeng/autocomplete';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';
import { TableModule, TablePageEvent } from 'primeng/table';
import { firstValueFrom, interval, Subscription } from 'rxjs';
import { FinanceAccount, FinanceContext, FinanceDialogs, FinanceImportJob, FinanceImportRow, errorMessage } from '../shared/finance-ui';
import { FinanceDateInputComponent } from '../shared/finance-date-input.component';

@Component({
  selector: 'finance-import-dialog',
  imports: [ AppModalComponent, FormsModule, SelectModule, AutoCompleteModule, TableModule, FinanceDateInputComponent, ProgressSpinnerModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.import-preview]': "job()?.status === 'READY'" },
  templateUrl: './import-dialog.component.html',
  styleUrls: ['./dialog.scss', './import-dialog.component.scss'],
})
export class ImportDialogComponent implements OnDestroy {
  readonly accountId = input<number>();
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = this.context.api;
  readonly selectedAccount = signal<number | undefined>(undefined);
  readonly file = signal<File | undefined>(undefined);
  readonly job = signal<FinanceImportJob | undefined>(undefined);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly editor = signal<FinanceImportRow | undefined>(undefined);
  readonly page = signal(0);
  readonly counterparties = signal<FinanceAccount[]>([]);
  readonly counterSuggestions = signal<FinanceAccount[]>([]);
  readonly counterValue = signal<FinanceAccount | string | null>(null);
  readonly activeAccounts = computed(() => this.context.accounts().filter(a => a.active));
  readonly accountChoices = computed(() => this.activeAccounts().map(a => ({ label: `${a.userName} · ${a.name} · ${a.currency}`, value: a.id })));
  readonly account = computed(() => this.context.accounts().find(a => a.id === (this.job()?.accountId ?? this.selectedAccount() ?? this.accountId())));
  readonly otherAccount = computed(() => this.activeAccounts().find(a => a.id === this.editor()?.otherAccountId));
  readonly crossCurrencyTransfer = computed(() => !!this.otherAccount() && this.otherAccount()?.currency !== this.account()?.currency);
  readonly transferChoices = computed(() => this.accountChoices().filter(a => a.value !== this.account()?.id));
  readonly transferDirections = computed(() => {
    const selected = this.account()?.name ?? 'Selected account';
    const other = this.otherAccount()?.name ?? 'Other account';
    return [{label: `${selected} → ${other}`, value: false}, {label: `${other} → ${selected}`, value: true}];
  });
  readonly running = computed(() => ['QUEUED', 'RUNNING'].includes(this.job()?.status ?? ''));
  readonly types = [{ label: 'Expense', value: 'WITHDRAWAL' }, { label: 'Income', value: 'DEPOSIT' }, { label: 'Transfer', value: 'TRANSFER' }];
  private polling?: Subscription;
  private approved = false;
  constructor() {
    effect(() => this.dialogs.blocked.set(this.busy() || !!this.editor()));
    interval(60000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.job()?.status === 'READY' && !this.busy()) void this.keepAlive();
    });
    this.api.allAccounts({ scope: 'COUNTERPARTY' }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: page => this.counterparties.set(page.items), error: error => this.error.set(errorMessage(error)) });
  }
  chooseFile(event: Event) {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    const file = target.files?.[0];
    this.error.set('');
    if (file && (!file.name.toLowerCase().endsWith('.csv') || file.size > 2 * 1024 * 1024)) { this.error.set('Select a CSV up to 2 MiB.'); this.file.set(undefined); return; }
    this.file.set(file);
  }
  async prepare() {
    const account = this.selectedAccount() ?? this.accountId(); const file = this.file();
    if (!account || !file || this.dialogs.hintsBlocked()) return;
    this.busy.set(true); this.error.set('');
    try {
      this.job.set(await firstValueFrom(this.api.client.financePrepareImport(account, file)));
      this.polling = interval(1500).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => { if (this.running() && !this.busy()) void this.load(); });
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
  async load(page = this.page()) {
    const job = this.job(); if (!job) return;
    this.busy.set(true);
    try { this.job.set(await firstValueFrom(this.api.client.financeGetImport(job.jobId, page, 50))); this.page.set(page); if (!this.running()) this.polling?.unsubscribe(); }
    catch (error) { this.error.set(errorMessage(error)); this.polling?.unsubscribe(); }
    finally { this.busy.set(false); }
  }
  private async keepAlive() {
    const draft = this.job(); if (!draft) return;
    try {
      const refreshed = await firstValueFrom(this.api.client.financeGetImport(draft.jobId, 0, 1));
      this.job.update(current => current?.jobId === refreshed.jobId ? {...current, expiresAt: refreshed.expiresAt} : current);
    } catch (error) { this.error.set(errorMessage(error)); }
  }
  paginate(event: TablePageEvent) { if (!this.editor()) void this.load(event.first / 50); }
  edit(row: FinanceImportRow) {
    this.error.set('');
    const copy = structuredClone(row);
    // Preserve the warning when a skipped duplicate is reopened and then unskipped.
    copy.duplicate = this.duplicateDetails(row) || row.duplicate;
    this.editor.set(copy);
    this.counterValue.set(this.counterparties().find(a => a.id === row.otherAccountId) ?? row.counterpartyName ?? '');
  }
  discardEdit() {
    if (!this.busy() && !this.dialogs.hintsBlocked()) { this.editor.set(undefined); this.error.set(''); }
  }
  patch(change: Partial<FinanceImportRow>) { this.editor.update(row => row ? { ...row, ...change } : row); }
  counterSearch(event: AutoCompleteCompleteEvent) {
    const kind = this.editor()?.type === 'DEPOSIT' ? 'REVENUE' : 'EXPENSE';
    this.counterSuggestions.set(this.counterparties().filter(a => a.kind === kind && a.name.toLowerCase().includes(event.query.toLowerCase())).slice(0, 50));
  }
  counterChange(value: FinanceAccount | string | null) {
    this.counterValue.set(value);
    if (typeof value === 'string' || value === null) this.patch({ otherAccountId: undefined, counterpartyName: value ?? '' });
  }
  counterSelect(event: AutoCompleteSelectEvent) {
    const value: unknown = event.value;
    if (!value || typeof value !== 'object' || !('id' in value)) return;
    const account = this.counterparties().find(a => a.id === value.id);
    if (!account) return;
    this.counterValue.set(account); this.patch({ otherAccountId: account.id, counterpartyName: '' });
  }
  async review(row = this.editor()) {
    const job = this.job(); if (!row || !job) return;
    this.busy.set(true); this.error.set('');
    try {
      await firstValueFrom(this.api.client.financeReviewImport({ jobId: job.jobId, revision: job.revision, rows: [row] }));
      this.editor.set(undefined); await this.load();
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
  accept(row: FinanceImportRow) { void this.review({...row, skip: false, duplicateOverride: !!this.duplicateDetails(row) || row.duplicateOverride}); }
  // Skipped rows retain the backend's duplicate reason when another row is revalidated.
  duplicateDetails(row: FinanceImportRow) { return row.duplicate || (row.skip && row.reason?.startsWith('Duplicate: ') ? row.reason.slice('Duplicate: '.length) : ''); }
  needsEdit(row: FinanceImportRow) {
    return !row.skip && !!row.issues?.some(issue => this.issueText(issue) !== this.issueText(row.reason ?? '')
      && !(this.duplicateDetails(row) && issue === 'Skip this duplicate or explicitly keep it'));
  }
  issueText(issue: string) { return issue.replace(/^Review interpretation:\s*/, ''); }
  reviewNotes(row: FinanceImportRow) { return row.issues?.map(issue => this.issueText(issue)) ?? []; }
  reviewTitle(row: FinanceImportRow) { return row.issues?.length ? this.reviewNotes(row).join('\n') : row.skip ? `Skipped: ${row.reason}` : 'Edit transaction'; }
  typeLabel(row: FinanceImportRow) { return row.type === 'WITHDRAWAL' ? 'Withdrawal' : row.type === 'DEPOSIT' ? 'Deposit' : row.type === 'TRANSFER' ? 'Transfer' : '—'; }
  typeSeverity(row: FinanceImportRow): 'danger' | 'success' | 'info' | 'secondary' {
    return row.type === 'WITHDRAWAL' ? 'danger' : row.type === 'DEPOSIT' ? 'success' : row.type === 'TRANSFER' ? 'info' : 'secondary';
  }
  skip(row: FinanceImportRow) { void this.review({ ...row, skip: true, reason: row.reason || 'Skipped by reviewer' }); }
  async approve() {
    const job = this.job(); if (!job?.readyToApprove || this.editor() || this.dialogs.hintsBlocked()) return;
    const result = await this.context.write('imports.approve', { jobId: job.jobId, revision: job.revision }, request => this.api.client.financeApproveImport(request));
    if (result) { this.approved = true; this.dialogs.close(); }
    else await this.load();
  }
  counterName(row: FinanceImportRow) { return this.counterparties().find(a => a.id === row.otherAccountId)?.name ?? this.context.accounts().find(a => a.id === row.otherAccountId)?.name ?? (row.counterpartyName ? `${row.counterpartyName} (new)` : '—'); }
  categoryName(row: FinanceImportRow) { return this.context.categories().find(c => c.id === row.categoryId)?.name ?? (row.categoryName || 'Uncategorised'); }
  ngOnDestroy() {
    this.dialogs.blocked.set(false);
    this.polling?.unsubscribe();
    const job = this.job();
    if (job && !this.approved && job.status !== 'APPROVED') this.api.client.financeCancelImport({ jobId: job.jobId }).subscribe({ error: () => {} });
  }
}
