import {ChangeDetectionStrategy, Component, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {FinanceAccount} from '../../../../../generated/backend-api/thereabout';
import {AccountLogoComponent} from './account-logo.component';
import {CounterpartyLogoComponent} from './counterparty-logo.component';
import {FinanceContext} from './finance-context.service';

@Component({
  selector: 'finance-transaction-account',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, AccountLogoComponent, CounterpartyLogoComponent],
  template: `@if (counterpartyId(); as id) {
    <a [routerLink]="['/finances/counterparties', id]">
      <finance-counterparty-logo [counterpartyId]="id" [iconRevision]="context.revision()" /><span>{{name()}}</span>
    </a>
  } @else {
    <span class="account-name">
      @if (account(); as a) { <finance-account-logo [account]="a" /> }
      @else { <span class="fallback"><i class="pi pi-wallet" aria-hidden="true"></i></span> }
      <span>{{name()}}</span>
    </span>
  }`,
  styles: `
    :host { display:block; min-width:140px; max-width:220px; }
    a, .account-name { display:flex; align-items:center; gap:8px; font-size:12px; line-height:1.4; }
    a { color:var(--app-link); text-decoration:none; }
    a:focus-visible { outline:2px solid var(--app-accent); outline-offset:2px; border-radius:4px; }
    a > span, .account-name > span:last-child { min-width:0; overflow-wrap:anywhere; }
    finance-account-logo, finance-counterparty-logo, .fallback { width:28px; height:28px; flex:0 0 28px; border-radius:7px; font-size:13px; }
    .fallback { display:grid; place-items:center; background:var(--app-hover); color:var(--app-link); }
  `,
})
export class TransactionAccountComponent {
  readonly context = inject(FinanceContext);
  readonly account = input<FinanceAccount>();
  readonly counterpartyId = input<number>();
  readonly name = input.required<string>();
}
