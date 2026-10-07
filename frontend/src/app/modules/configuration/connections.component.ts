import {ChangeDetectionStrategy, Component, DestroyRef, inject, signal} from '@angular/core';
import {NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {SelectModule} from 'primeng/select';
import {filter} from 'rxjs';
import {ConnectionSummaries, ConnectionSummary, connections} from './connection-summaries';

@Component({
  selector: 'app-connections',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, FormsModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="connections-workspace">
      <nav class="connection-list" aria-label="Connections">
        <h2>Your connections</h2>
        @for (connection of connections; track connection.id) {
          <a [routerLink]="connection.id" routerLinkActive="selected" ariaCurrentWhenActive="page">
            <i [class]="connection.icon" aria-hidden="true"></i>
            <span><strong>{{ connection.label }}</strong><small [attr.data-state]="summaries.values()[connection.id].state">{{ summaries.values()[connection.id].label }}</small></span>
            <i class="pi pi-angle-right chevron" aria-hidden="true"></i>
          </a>
        }
      </nav>
      <div class="connection-selector">
        <label for="connection-selector">Connection</label>
        <p-select inputId="connection-selector" ariaLabel="Connection" [options]="connections" optionLabel="label" optionValue="id"
          [ngModel]="selected()" (ngModelChange)="choose($event)" appendTo="body" [style]="{width: '100%'}">
          <ng-template #item let-connection><span>{{ connection.label }} · {{ summaryFor(connection.id).label }}</span></ng-template>
        </p-select>
      </div>
      <section class="connection-detail" aria-label="Connection settings"><router-outlet /></section>
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .connections-workspace { display: grid; grid-template-columns: 240px minmax(0, 1fr); background: var(--app-surface, #fff); border: 1px solid var(--app-border); border-radius: 12px; overflow: clip; }
    .connection-list { padding: 1rem; border-right: 1px solid var(--app-border); }
    h2 { font-family: inherit; font-size: 1rem; margin: .5rem .5rem 1rem; }
    .connection-list a { display: flex; align-items: center; gap: .8rem; padding: 1rem .7rem; color: var(--app-ink); text-decoration: none; border-radius: 8px; margin-bottom: .3rem; }
    .connection-list a.selected { background: var(--app-selection); }
    .connection-list a:focus-visible { outline: 2px solid var(--app-accent); }
    .connection-list a > i:first-child { color: var(--app-accent); font-size: 1.25rem; }
    .connection-list span { display: grid; gap: .35rem; }
    .connection-list strong { font-size: .9rem; }
    small { font-size: .75rem; color: var(--app-muted); }
    small[data-state="connected"] { color: var(--app-success); }
    small[data-state="attention"], small[data-state="unavailable"] { color: var(--app-warning); }
    .chevron { margin-left: auto; font-size: .8rem; }
    .connection-detail { padding: 1.5rem; min-width: 0; }
    .connection-selector { display: none; }
    @media (max-width: 768px) {
      .connections-workspace { display: block; }
      .connection-list { display: none; }
      .connection-selector { display: grid; gap: .5rem; padding: 1rem; border-bottom: 1px solid var(--app-border); }
      .connection-detail { padding: 1rem; }
    }
  `
})
export class ConnectionsComponent {
  readonly summaries = inject(ConnectionSummaries);
  readonly connections = [...connections];
  private readonly router = inject(Router);
  readonly selected = signal(this.selectedId());
  constructor() {
    this.summaries.load();
    this.router.events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.selected.set(this.selectedId()));
  }
  summaryFor(id: string): ConnectionSummary {
    const connection = connections.find(connection => connection.id === id);
    return connection ? this.summaries.values()[connection.id] : {state: 'unavailable', label: 'Unavailable'};
  }
  private selectedId(): string { return this.router.url.split(/[?#]/)[0].split('/').at(-1) ?? 'google-calendar'; }
  async choose(id: string): Promise<void> {
    this.selected.set(id);
    await this.router.navigate(['/configuration/connections', id]);
    this.selected.set(this.selectedId());
  }
}
