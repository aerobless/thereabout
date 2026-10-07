import { ChangeDetectionStrategy, Component, DestroyRef, PendingTasks, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { MessageService } from 'primeng/api';
import { firstValueFrom, timer, exhaustMap, catchError, EMPTY, forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { SplitwiseService, SplitwiseSettings, SplitwiseCatalog, SplitwiseStatus, SplitwiseRow, SplitwiseMemberMapping, SplitwiseCategoryMapping } from '../../../../generated/backend-api/thereabout';
import { FinanceDateInputComponent } from '../finances/shared/finance-date-input.component';
import { AppModalComponent } from '../../shared/modal/app-modal.component';
import { errorMessage } from '../finances/shared/finance-resource';
import {ConfigurationEditor} from './configuration-navigation';
import {ConnectionSummaries, splitwiseSummary} from './connection-summaries';

type MemberDraft = { memberId: number; accountId: number | null; bankAccountId: number | null; startDate: string | null };
const runningStates = ['PREVIEW_QUEUED', 'PREVIEW_RUNNING', 'INITIALIZE_QUEUED', 'INITIALIZING', 'SYNC_QUEUED', 'SYNCING'];
@Component({
  selector: 'app-splitwise-settings',
  imports: [RouterLink, FormsModule, DecimalPipe, ButtonModule, SelectModule, InputTextModule, CheckboxModule, TableModule, FinanceDateInputComponent, AppModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './splitwise-settings.component.html',
  styleUrls: ['./splitwise-settings.component.scss', './configuration-panel.scss'],
})
export class SplitwiseSettingsComponent implements ConfigurationEditor {
  private readonly summaries = inject(ConnectionSummaries, {optional: true});
  private readonly api = inject(SplitwiseService);
  private readonly messages = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pendingTasks = inject(PendingTasks);
  readonly settings = signal<SplitwiseSettings | null>(null);
  readonly catalog = signal<SplitwiseCatalog | null>(null);
  readonly status = signal<SplitwiseStatus | null>(null);
  readonly key = signal('');
  readonly error = signal('');
  readonly networkBusy = signal(false);
  readonly group = signal<number | null>(null);
  readonly members = signal<MemberDraft[]>([]);
  readonly categories = signal<SplitwiseCategoryMapping[]>([]);
  readonly enabled = signal(false);
  readonly previewOpen = signal(false);
  readonly corrections = signal<number[]>([]);
  readonly categoryOpen = signal(false);
  readonly reviewOpen = signal(false);
  readonly categoryDraft = signal<SplitwiseCategoryMapping[]>([]);
  readonly categoryRevision = signal(0);
  readonly categoryError = signal('');
  readonly categoryConflict = signal(false);
  readonly savingCategories = signal(false);
  readonly categoryCount = computed(() => this.categories().length);
  readonly categoryGroups = computed(() => {
    const groups = new Map<string, NonNullable<SplitwiseCatalog['sourceCategories']>>();
    for (const category of this.catalog()?.sourceCategories ?? []) {
      const name = category.parentName || 'Other';
      groups.set(name, [...(groups.get(name) ?? []), category]);
    }
    return [...groups].map(([name, categories]) => ({ name, categories }));
  });
  readonly busy = computed(() => this.networkBusy() || runningStates.includes(this.status()?.state ?? ''));
  readonly unsaved = computed(() => {
    const saved = this.settings(); if (!saved) return false;
    const members = this.members().filter(m => m.accountId !== null).map(m => ({memberId: m.memberId, accountId: m.accountId, bankAccountId: m.bankAccountId, startDate: m.startDate || null})).sort((a,b) => a.memberId - b.memberId);
    const previous = saved.members.map(m => ({memberId: m.memberId, accountId: m.accountId, bankAccountId: m.bankAccountId, startDate: m.startDate || null})).sort((a,b) => a.memberId - b.memberId);
    return this.group() !== (saved.groupId ?? null) || this.enabled() !== saved.enabled || !!this.key()
      || JSON.stringify(members) !== JSON.stringify(previous);
  });
  readonly selectedGroup = computed(() => this.catalog()?.groups.find(g => g.id === this.group()));
  readonly accountOptions = computed(() => this.catalog()?.accounts.map(a => ({ id: a.id, label: `${a.name} · ${a.userName} (${a.currency})` })) ?? []);
  readonly categoryOptions = computed(() => [{ id: null, label: 'Uncategorized' }, ...(this.catalog()?.categories.map(c => ({ id: c.id, label: c.name })) ?? [])]);
  hasUnsavedChanges(): boolean {
    return this.unsaved() || this.corrections().length > 0 || this.categoryOpen() && JSON.stringify(this.categoryDraft()) !== JSON.stringify(this.categories());
  }
  isNavigationBlocked(): boolean { return this.networkBusy() || this.savingCategories(); }
  constructor() {
    effect(() => {
      const settings = this.settings();
      if (settings) this.summaries?.update('splitwise', splitwiseSummary(settings, this.status()));
      else if (this.error()) this.summaries?.unavailable('splitwise');
    });
    void this.run(() => this.load());
    timer(2000, 2000).pipe(exhaustMap(() => forkJoin({
      status: this.api.splitwiseStatus(), settings: this.api.splitwiseSettings(),
    }).pipe(catchError(e => { this.error.set(errorMessage(e)); return EMPTY; }))), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.status.set(value.status); this.acceptSettings(value.settings); },
      error: e => this.error.set(errorMessage(e)),
    });
    this.destroyRef.onDestroy(() => this.key.set(''));
  }
  private async load() {
    const settings = await firstValueFrom(this.api.splitwiseSettings());
    const catalog = await firstValueFrom(this.api.splitwiseCatalog());
    this.catalog.set(catalog); this.acceptSettings(settings);
    if (settings.initialized) this.previewOpen.set(false);
    this.status.set(await firstValueFrom(this.api.splitwiseStatus()));
  }
  private acceptSettings(settings: SplitwiseSettings) {
    const current = this.settings();
    if (current && settings.revision < current.revision) return;
    const dirty = this.unsaved();
    this.settings.set(settings); this.categories.set(settings.categories);
    if (settings.initialized) this.previewOpen.set(false);
    if (!dirty) {
      this.group.set(settings.groupId ?? null);
      this.members.set(settings.members.map(m => ({ ...m, startDate: m.startDate ?? null })));
      this.enabled.set(settings.enabled);
    }
  }
  private run(action: () => Promise<void>) { this.pendingTasks.run(async () => {
    this.networkBusy.set(true); this.error.set('');
    try { await action(); } catch (e) { this.error.set(errorMessage(e)); }
    finally { this.networkBusy.set(false); }
  }); }
  selectGroup(id: number | null) {
    this.group.set(id);
    this.members.set((this.catalog()?.groups.find(g => g.id === id)?.members ?? []).map(m => ({ memberId: m.id, accountId: null, bankAccountId: null, startDate: null })));
  }
  draft(id: number): MemberDraft { return this.members().find(m => m.memberId === id) ?? { memberId: id, accountId: null, bankAccountId: null, startDate: null }; }
  editMember(id: number, patch: Partial<MemberDraft>) {
    this.members.update(values => {
      const next = { ...this.draft(id), ...patch };
      return [...values.filter(m => m.memberId !== id), next].sort((a, b) => a.memberId - b.memberId);
    });
  }
  selectAccount(id: number, accountId: number | null) {
    const account = this.catalog()?.accounts.find(a => a.id === accountId);
    this.editMember(id, { accountId, bankAccountId: null, startDate: account?.suggestedStartDate ?? null });
  }
  bankOptions(memberId: number) {
    const own = this.catalog()?.accounts.find(a => a.id === this.draft(memberId).accountId);
    return this.catalog()?.accounts.filter(a => a.userId === own?.userId && a.currency === own?.currency && a.id !== own?.id)
      .map(a => ({ id: a.id, label: a.name })) ?? [];
  }
  mapping(id: number): number | null | undefined { return this.categoryDraft().find(c => c.sourceCategoryId === id)?.categoryId; }
  mapCategory(sourceCategoryId: number, categoryId: number | null) {
    this.categoryDraft.update(values => [...values.filter(v => v.sourceCategoryId !== sourceCategoryId), { sourceCategoryId, categoryId: categoryId ?? undefined }]);
  }
  isMapped(id: number) { return this.categoryDraft().some(c => c.sourceCategoryId === id); }
  unmapCategory(id: number) { this.categoryDraft.update(values => values.filter(v => v.sourceCategoryId !== id)); }
  openCategories() {
    this.categoryDraft.set(this.categories().map(c => ({ ...c })));
    this.categoryRevision.set(this.settings()?.revision ?? 0);
    this.categoryError.set(''); this.categoryConflict.set(false);
    for (const c of this.catalog()?.sourceCategories ?? []) if (!this.isMapped(c.id)) {
      const matches = this.catalog()?.categories.filter(v => v.name.trim().toLowerCase() === c.name.trim().toLowerCase()) ?? [];
      if (matches.length === 1) this.mapCategory(c.id, matches[0].id);
    }
    this.categoryOpen.set(true);
  }
  closeCategories() {
    if (this.savingCategories()) return;
    this.categoryOpen.set(false); this.categoryDraft.set([]); this.categoryError.set('');
  }
  reloadCategories() {
    this.pendingTasks.run(async () => {
      this.savingCategories.set(true); this.categoryError.set('');
      try {
        this.acceptSettings(await firstValueFrom(this.api.splitwiseSettings()));
        this.catalog.set(await firstValueFrom(this.api.splitwiseCatalog()));
        this.openCategories();
      } catch (e) { this.categoryError.set(errorMessage(e)); }
      finally { this.savingCategories.set(false); }
    });
  }
  saveCategories() {
    this.pendingTasks.run(async () => {
      this.savingCategories.set(true); this.categoryError.set('');
      try {
        const saved = await firstValueFrom(this.api.splitwiseSaveCategories({
          revision: this.categoryRevision(), requestKey: crypto.randomUUID(), categories: this.categoryDraft(),
        }));
        this.acceptSettings(saved); this.categoryOpen.set(false); this.categoryDraft.set([]);
        this.messages.add({ severity: 'success', summary: 'Splitwise category mapping saved', life: 3000 });
      } catch (e) {
        this.categoryError.set(errorMessage(e));
        this.categoryConflict.set(typeof e === 'object' && e !== null && 'status' in e && e.status === 409);
      } finally { this.savingCategories.set(false); }
    });
  }
  toggleHistory(memberId: number, full: boolean) {
    const account = this.catalog()?.accounts.find(a => a.id === this.draft(memberId).accountId);
    const today = new Date();
    const fallback = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    this.editMember(memberId, { startDate: full ? null : account?.suggestedStartDate ?? fallback });
  }
  save(removeKey = false, credentialsOnly = false) {
    void this.run(async () => {
      const members: SplitwiseMemberMapping[] = credentialsOnly ? [...(this.settings()?.members ?? [])] : [];
      for (const m of credentialsOnly ? [] : this.members()) {
        if (m.accountId === null) continue;
        if (m.bankAccountId === null) throw new Error('Select a bank account for every mapped member.');
        members.push({ memberId: m.memberId, accountId: m.accountId, bankAccountId: m.bankAccountId, startDate: m.startDate ?? undefined });
      }
      const settings = await firstValueFrom(this.api.splitwiseSaveSettings({ revision: this.settings()?.revision ?? 0,
        apiKey: removeKey ? undefined : this.key() || undefined, removeKey, groupId: (credentialsOnly ? this.settings()?.groupId : this.group()) ?? undefined,
        enabled: this.enabled(), members, categories: credentialsOnly ? this.settings()?.categories ?? [] : this.categories() }));
      this.key.set(''); this.settings.set(settings); this.categories.set(settings.categories); this.enabled.set(settings.enabled);
      this.messages.add({ severity: 'success', summary: 'Splitwise settings saved', life: 3000 });
      this.status.set(await firstValueFrom(this.api.splitwiseStatus()));
    });
  }
  test() {
    void this.run(async () => {
      const catalog = await firstValueFrom(this.api.splitwiseTest()); this.catalog.set(catalog);
      this.settings.set(await firstValueFrom(this.api.splitwiseSettings()));
      this.messages.add({ severity: 'success', summary: 'Splitwise connection successful', life: 3000 });
    });
  }
  preview() {
    void this.run(async () => { this.corrections.set([]); this.status.set(await firstValueFrom(this.api.splitwisePreview())); this.previewOpen.set(true); });
  }
  initialize() {
    const preview = this.status()?.preview;
    if (!preview) return;
    void this.run(async () => {
      this.status.set(await firstValueFrom(this.api.splitwiseInitialize({ previewId: preview.id, requestKey: `sw-init:${preview.id}`, correctionMembers: this.corrections() })));
      this.corrections.set([]);
    });
  }
  sync() { void this.run(async () => { this.status.set(await firstValueFrom(this.api.splitwiseSync())); }); }
  correction(member: number, selected: boolean) { this.corrections.update(values => selected ? [...values.filter(v => v !== member), member] : values.filter(v => v !== member)); }
  count(action: string) { return this.status()?.preview?.rows.filter(r => r.action === action).length ?? 0; }
  name(row: SplitwiseRow) { return this.catalog()?.accounts.find(a => a.id === row.accountId)?.userName ?? String(row.memberId); }
}
