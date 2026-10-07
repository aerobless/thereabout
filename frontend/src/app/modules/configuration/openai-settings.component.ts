import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import {ButtonModule} from 'primeng/button';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { OpenAIService } from '../../../../generated/backend-api/thereabout';
import { errorMessage } from '../finances/shared/finance-resource';
import {ConfigurationEditor} from './configuration-navigation';
import {ConnectionSummaries} from './connection-summaries';

@Component({
  selector: 'app-openai-settings',
  imports: [FormsModule, InputTextModule, ButtonModule],
  styleUrl: './configuration-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="service-heading"><h2 id="openai-settings" tabindex="-1">OpenAI</h2><p>Prepare suggestions for finance CSV imports.</p></header>
    <p>{{ loaded() ? (configured() ? 'API key configured' : 'No API key configured') : (error() ? 'Settings unavailable' : 'Loading settings…') }}</p>
    <label for="openai-key">{{ configured() ? 'Replace API key' : 'API key' }}</label>
    <input id="openai-key" pInputText type="password" autocomplete="new-password" [ngModel]="key()" (ngModelChange)="key.set($event)" [disabled]="busy() || !loaded()" placeholder="Enter a new API key" />
    <label for="openai-model">Model</label>
    <input id="openai-model" pInputText [ngModel]="model()" (ngModelChange)="model.set($event)" [disabled]="busy() || !loaded()" />
    <p>CSV contents are sent to OpenAI to prepare suggestions. Transactions are created only after approval.</p>
    <div class="actions">
      <p-button label="Save" (onClick)="save()" [disabled]="busy() || !loaded() || !model().trim()" />
      <p-button label="Test connection" severity="secondary" (onClick)="test()" [disabled]="busy() || !configured()" />
      <p-button label="Remove key" severity="secondary" (onClick)="save(true)" [disabled]="busy() || !configured()" />
    </div>
    @if (busy()) { <p role="status">Please wait…</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
  `,
  styles: `:host { display:block; } label { display:block; margin:.75rem 0 .4rem; } input { width:100%; } p { color:var(--app-muted); } .actions { display:flex; flex-wrap:wrap; gap:.5rem; }`,
})
export class OpenAiSettingsComponent implements ConfigurationEditor {
  private readonly api = inject(OpenAIService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly summaries = inject(ConnectionSummaries, {optional: true});
  private readonly messages = inject(MessageService);
  readonly key = signal('');
  readonly model = signal('gpt-6-luna');
  readonly configured = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly loaded = signal(false);
  private savedModel = '';
  hasUnsavedChanges(): boolean { return !!this.key() || this.loaded() && this.model() !== this.savedModel; }
  isNavigationBlocked(): boolean { return this.busy(); }
  private accept(settings: {configured: boolean; model: string}): void {
    this.configured.set(settings.configured); this.model.set(settings.model); this.savedModel = settings.model; this.loaded.set(true);
    this.summaries?.update('openai', {state: settings.configured ? 'configured' : 'unconfigured', label: settings.configured ? 'Key configured' : 'Not configured'});
  }
  constructor() {
    this.destroyRef.onDestroy(() => this.key.set(''));
    this.api.openAiGetSettings('body', false, { transferCache: false }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: settings => this.accept(settings),
      error: error => { this.error.set(errorMessage(error)); this.summaries?.unavailable('openai'); },
    });
  }
  async save(removeKey = false) {
    this.busy.set(true); this.error.set('');
    try {
      const settings = await firstValueFrom(this.api.openAiSaveSettings({ model: this.model(), apiKey: removeKey ? undefined : this.key() || undefined, removeKey }));
      if (this.destroyRef.destroyed) return;
      this.accept(settings); this.key.set('');
      this.messages.add({ severity: 'success', summary: 'OpenAI settings saved', life: 3000 });
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
  async test() {
    this.busy.set(true); this.error.set('');
    try {
      const result = await firstValueFrom(this.api.openAiTestSettings());
      if (this.destroyRef.destroyed) return;
      this.summaries?.update('openai', result.success ? {state: 'connected', label: 'Connected'} : {state: 'attention', label: 'Test failed'});
      if (result.success) this.messages.add({ severity: 'success', summary: result.message, life: 3000 });
      else this.error.set(result.message);
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
}
