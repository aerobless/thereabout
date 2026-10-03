import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { OpenAIService } from '../../../../generated/backend-api/thereabout';
import { errorMessage } from '../finances/shared/finance-resource';

@Component({
  selector: 'app-openai-settings',
  imports: [FormsModule, InputTextModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p>{{ configured() ? 'API key configured' : 'No API key configured' }}</p>
    <label for="openai-key">{{ configured() ? 'Replace API key' : 'API key' }}</label>
    <input id="openai-key" pInputText type="password" autocomplete="new-password" [ngModel]="key()" (ngModelChange)="key.set($event)" placeholder="Enter a new API key" />
    <label for="openai-model">Model</label>
    <input id="openai-model" pInputText [ngModel]="model()" (ngModelChange)="model.set($event)" />
    <p>CSV contents are sent to OpenAI to prepare suggestions. Transactions are created only after approval.</p>
    <div class="actions">
      <button (click)="save()" [disabled]="busy() || !model().trim()">Save</button>
      <button (click)="test()" [disabled]="busy() || !configured()">Test connection</button>
      <button (click)="save(true)" [disabled]="busy() || !configured()">Remove key</button>
    </div>
    @if (busy()) { <p role="status">Please wait…</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
  `,
  styles: `:host { display:block; } label { display:block; margin:.75rem 0 .4rem; } input { width:100%; } p { color:var(--app-muted); } .actions { display:flex; flex-wrap:wrap; gap:.5rem; } button { padding:.6rem 1rem; border:1px solid var(--app-border); border-radius:6px; cursor:pointer; }`,
})
export class OpenAiSettingsComponent {
  private readonly api = inject(OpenAIService);
  private readonly messages = inject(MessageService);
  readonly key = signal('');
  readonly model = signal('gpt-6-luna');
  readonly configured = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  constructor() {
    this.api.openAiGetSettings('body', false, { transferCache: false }).subscribe({
      next: settings => { this.configured.set(settings.configured); this.model.set(settings.model); },
      error: error => this.error.set(errorMessage(error)),
    });
  }
  async save(removeKey = false) {
    this.busy.set(true); this.error.set('');
    try {
      const settings = await firstValueFrom(this.api.openAiSaveSettings({ model: this.model(), apiKey: removeKey ? undefined : this.key() || undefined, removeKey }));
      this.configured.set(settings.configured); this.model.set(settings.model); this.key.set('');
      this.messages.add({ severity: 'success', summary: 'OpenAI settings saved', life: 3000 });
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
  async test() {
    this.busy.set(true); this.error.set('');
    try {
      const result = await firstValueFrom(this.api.openAiTestSettings());
      if (result.success) this.messages.add({ severity: 'success', summary: result.message, life: 3000 });
      else this.error.set(result.message);
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
}
