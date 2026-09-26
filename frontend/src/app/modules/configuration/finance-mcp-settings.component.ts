import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { InputTextModule } from "primeng/inputtext";
import { Subscription } from "rxjs";
import { FinancesService } from "../../../../generated/backend-api/thereabout";

@Component({
  selector: "app-finance-mcp-settings",
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
      Use this key with <code>Authorization: Bearer &lt;key&gt;</code> at
      <code>/mcp/finances</code>.
    </p>
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
    code {
      overflow-wrap: anywhere;
    }
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
