import {ChangeDetectionStrategy, Component, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CardModule} from 'primeng/card';
import {InputTextModule} from 'primeng/inputtext';
import {FrontendService} from '../../../../generated/backend-api/thereabout';
import {FinanceMcpSettingsComponent} from './finance-mcp-settings.component';

@Component({
  selector: 'app-api-access', imports: [CardModule, InputTextModule, FinanceMcpSettingsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush, styleUrl: './configuration.component.scss',
  template: `<div class="configuration-container">
    <p-card><ng-template #header><div class="card-header"><i class="pi pi-key card-header-icon" aria-hidden="true"></i><span id="mcp-settings" tabindex="-1" class="card-header-title">MCP</span></div></ng-template><app-finance-mcp-settings /></p-card>
    <p-card><ng-template #header><div class="card-header"><i class="pi pi-key card-header-icon" aria-hidden="true"></i><span id="api-settings" tabindex="-1" class="card-header-title">API authentication</span></div></ng-template>
      <label for="thereabout-api-key" class="config-label">API authentication key</label>
      <input id="thereabout-api-key" pInputText class="config-input-full" readonly autocomplete="off" spellcheck="false"
        [type]="revealed() ? 'text' : 'password'" [value]="ingestionKey()" (focus)="revealed.set(true)" (blur)="revealed.set(false)" (keydown.escape)="revealed.set(false)" />
      <p>Authenticates public API requests. Focus the field to reveal the key.</p>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      <p><a href="/swagger-ui/index.html" target="_blank" rel="noopener">Swagger UI (OpenAPI)</a> · <a href="/v3/api-docs" target="_blank" rel="noopener">OpenAPI specification</a></p>
    </p-card>
  </div>`
})
export class ApiAccessComponent {
  readonly ingestionKey = signal('');
  readonly revealed = signal(false);
  readonly error = signal('');
  constructor() {
    const destroyRef = inject(DestroyRef);
    inject(FrontendService).getIngestionKey().pipe(takeUntilDestroyed(destroyRef)).subscribe({
      next: response => this.ingestionKey.set(response.value),
      error: () => this.error.set('API authentication key could not be loaded. Reopen this tab to retry.')
    });
    destroyRef.onDestroy(() => { this.ingestionKey.set(''); this.revealed.set(false); });
  }
}
