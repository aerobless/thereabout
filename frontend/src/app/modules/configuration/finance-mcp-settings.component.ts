import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { InputTextModule } from "primeng/inputtext";
import { Subscription } from "rxjs";
import { FinancesService, FinanceMcpEndpoint } from "../../../../generated/backend-api/thereabout";

@Component({
  selector: "app-finance-mcp-settings",
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [InputTextModule],
  template: `
    <label for="finance-mcp-key">MCP bearer key</label>
    <input
      id="finance-mcp-key"
      pInputText
      readonly
      autocomplete="off"
      spellcheck="false"
      [type]="revealed() ? 'text' : 'password'"
      [value]="revealed() ? key() : '••••••••••••••••'"
      [attr.aria-busy]="loading()"
      aria-describedby="finance-mcp-help"
      (focus)="reveal()"
      (click)="reveal()"
      (blur)="hide()"
      (keydown.escape)="hide()"
    />
    <p id="finance-mcp-help">
      Click the field to reveal your key. It is hidden again when you leave the
      field.
    </p>
    <p>
      Connect your MCP client with <code>Authorization: Bearer &lt;key&gt;</code>.
      Available endpoint: <code>/mcp/finances</code>.
    </p>
    <h3>Available tools</h3>
    @if (catalogLoading()) { <p role="status">Loading tools…</p> }
    @if (catalogError()) { <p role="alert">{{catalogError()}}</p> }
    @for (endpoint of endpoints(); track endpoint.path) {
      <h4><code>{{endpoint.path}}</code></h4>
      <dl class="mcp-tools">
        @for (tool of endpoint.tools; track tool.name) {
          <div><dt><code>{{tool.name}}</code><span class="tool-mode">{{tool.readOnly ? 'Read' : 'Write'}}</span></dt><dd>{{tool.description}}</dd></div>
        }
      </dl>
    }
    @if (loading()) {
      <p role="status">Loading key…</p>
    }
    @if (error()) {
      <p role="alert">{{ error() }}</p>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    label {
      display: block;
      font-weight: 500;
      margin-bottom: 0.5rem;
    }
    input {
      width: 100%;
      font-family: monospace;
    }
    p {
      color: var(--app-muted);
    }
    code { overflow-wrap: anywhere; }
    .mcp-tools { margin:0; }
    .mcp-tools > div { padding:1rem 0; border-top:1px solid var(--app-border); }
    dt { display:flex; align-items:baseline; justify-content:space-between; gap:1rem; }
    dd { margin:.5rem 0 0; color:var(--app-muted); line-height:1.5; }
    .tool-mode { color:var(--app-muted); font-size:.8rem; flex:none; }
  `,
})
export class FinanceMcpSettingsComponent {
  private readonly api = inject(FinancesService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  readonly key = signal("");
  readonly revealed = signal(false);
  readonly loading = signal(false);
  readonly error = signal("");

  readonly endpoints = signal<FinanceMcpEndpoint[]>([]);
  readonly catalogLoading = signal(true);
  readonly catalogError = signal('');
  constructor() {
    this.destroyRef.onDestroy(() => this.hide());
    this.api.financeMcpCatalog().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: catalog => { this.endpoints.set(catalog.endpoints); this.catalogLoading.set(false); },
      error: () => { this.catalogError.set('Tools could not be loaded. Reopen this tab to retry.'); this.catalogLoading.set(false); }
    });
  }

  reveal() {
    if (this.revealed() || this.loading()) return;
    this.request?.unsubscribe();
    this.error.set("");
    this.loading.set(true);
    this.request = this.api
      .financeGetMcpKey("body", false, { transferCache: false })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (key) => {
          this.key.set(key);
          this.revealed.set(true);
          this.loading.set(false);
        },
        error: (response) => {
          this.loading.set(false);
          this.error.set(
            response.status === 404
              ? "Finances are not enabled on this server."
              : "The key could not be loaded. Please try again.",
          );
        },
      });
  }

  hide() {
    this.request?.unsubscribe();
    this.key.set("");
    this.revealed.set(false);
    this.loading.set(false);
  }
}
