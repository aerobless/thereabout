import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { FinanceContext, FinanceImportHint, errorMessage } from '../shared/finance-ui';

@Component({
  selector: 'finance-import-hints-editor',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p>
      @if (!hintsLoaded()) { <button class="app-button" type="button" (click)="reload.update(increment)">Retry</button> }
    }
    @if (loading()) { <p role="status">Loading hints…</p> }
    @else {
      @if (!hints().length) { <p class="empty">No hints yet.</p> }
      <ul class="hint-list">
        @for (hint of hints(); track hint.id) {
          <li><span>{{ hint.text }}</span><button class="app-button" type="button" (click)="remove(hint)" [disabled]="saving()"
            [attr.aria-label]="'Remove hint: ' + hint.text" title="Remove hint"><i class="pi pi-times" aria-hidden="true"></i></button></li>
        }
      </ul>
      <form class="hint-form" (ngSubmit)="add()">
        <input aria-label="New hint" name="hint" [ngModel]="text()" (ngModelChange)="text.set($event)"
          maxlength="1000" placeholder="New hint" [disabled]="saving()" />
        <button type="submit" class="app-button primary" [disabled]="saving() || !hintsLoaded() || !text().trim() || hints().length >= 50">Add hint</button>
      </form>
      @if (hints().length >= 50) { <p class="empty">An account can have up to 50 hints.</p> }
    }
  `,
  styleUrls: ['./dialog.scss', './import-hints-editor.component.scss'],
})
export class ImportHintsEditorComponent {
  readonly accountId = input.required<number>();
  readonly savingChange = output<boolean>();
  readonly hints = signal<FinanceImportHint[]>([]);
  readonly reload = signal(0);
  readonly increment = (value: number) => value + 1;
  readonly text = signal('');
  readonly error = signal('');
  readonly loading = signal(false);
  readonly hintsLoaded = signal(false);
  readonly saving = signal(false);
  private readonly api = inject(FinanceContext).api.client;
  private readonly messages = inject(MessageService);
  private pending?: { fingerprint: string; key: string };

  constructor() {
    effect(() => this.savingChange.emit(this.saving()));
    effect(cleanup => {
      const id = this.accountId(); this.reload();
      this.hints.set([]); this.text.set(''); this.error.set(''); this.pending = undefined;
      this.loading.set(true); this.hintsLoaded.set(false);
      const request = this.api.financeListImportHints(id).subscribe({
        next: result => { this.hints.set(result.items); this.loading.set(false); this.hintsLoaded.set(true); },
        error: error => { this.error.set(errorMessage(error)); this.loading.set(false); },
      });
      cleanup(() => request.unsubscribe());
    });
  }

  async add() {
    const text = this.text().trim();
    if (!text || this.saving() || !this.hintsLoaded() || this.hints().length >= 50) return;
    const accountId = this.accountId();
    const key = this.requestKey(['add', accountId, text]);
    this.saving.set(true); this.error.set('');
    try {
      const hint = await firstValueFrom(this.api.financeAddImportHint(accountId, {accountId, text, requestKey: key}));
      if (this.accountId() !== accountId) return;
      this.hints.update(hints => hints.some(h => h.id === hint.id) ? hints : [...hints, hint]);
      this.text.set(''); this.pending = undefined;
      this.messages.add({severity: 'success', summary: 'Hint added', life: 3000});
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.saving.set(false); }
  }

  async remove(hint: FinanceImportHint) {
    if (this.saving()) return;
    const accountId = this.accountId();
    const key = this.requestKey(['remove', accountId, hint.id, hint.version]);
    this.saving.set(true); this.error.set('');
    try {
      await firstValueFrom(this.api.financeRemoveImportHint(accountId, hint.id, {accountId, id: hint.id, version: hint.version, requestKey: key}));
      if (this.accountId() !== accountId) return;
      this.hints.update(hints => hints.filter(h => h.id !== hint.id)); this.pending = undefined;
      this.messages.add({severity: 'success', summary: 'Hint removed', life: 3000});
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.saving.set(false); }
  }

  private requestKey(args: unknown[]) {
    const fingerprint = JSON.stringify(args);
    if (this.pending?.fingerprint !== fingerprint) this.pending = {fingerprint, key: crypto.randomUUID()};
    return this.pending.key;
  }
}
