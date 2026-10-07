import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {toSignal} from '@angular/core/rxjs-interop';
import {FinanceContext, loadResource} from '../shared/finance-ui';
import {TransactionsComponent} from './transactions.component';
import {CounterpartyLogoComponent} from '../shared/counterparty-logo.component';
import {ChipModule} from 'primeng/chip';
@Component({
  imports:[FormsModule,RouterLink,TransactionsComponent,CounterpartyLogoComponent,ChipModule], changeDetection:ChangeDetectionStrategy.OnPush,
  template:`<a routerLink="/finances/accounts" [queryParams]="{view: 'counterparties'}">← Counterparties</a>
    @if (state().error) { <p class="alert error" role="alert">{{state().error}}</p> }
    @if (state().data; as c) {
      <section class="panel counterparty-detail"><header><finance-counterparty-logo [counterparty]="c" /><div><h2>{{c.name}}</h2><p>{{c.accounts.length}} associated ledger accounts</p></div></header>
      <form (ngSubmit)="save()"><div class="form-grid"><label>Display name<input name="name" [ngModel]="name()" (ngModelChange)="name.set($event)" required [disabled]="context.saving()" /></label><label>Website<input name="website" [ngModel]="website()" (ngModelChange)="website.set($event)" placeholder="https://example.com" [disabled]="context.saving()" /></label></div>
      <div class="alias-editor"><span id="aliases-heading">Aliases</span>
        <ul class="alias-list" aria-labelledby="aliases-heading">@for (alias of aliases(); track alias) {
          <li><p-chip removeIcon="pi pi-times-circle" [label]="alias" [pt]="{removeIcon:{'aria-label':'Remove alias ' + alias}}" [removable]="!context.saving()" [disabled]="context.saving()" (onRemove)="removeAlias(alias)" /></li>
        } @empty { <li class="alias-empty">No aliases yet.</li> }</ul>
        <div class="alias-add"><input name="aliasDraft" aria-label="New alias" placeholder="Add an alias…" maxlength="1024" [ngModel]="aliasDraft()" (ngModelChange)="aliasDraft.set($event)" [disabled]="context.saving()" (keydown.enter)="$event.preventDefault(); addAlias()" /><button type="button" class="app-button" [disabled]="context.saving() || !canAddAlias()" (click)="addAlias()">Add alias</button></div>
        <small>Removing an alias only changes future import matching. Save changes to apply your edits.</small>
      </div>
      <button class="app-button primary" [disabled]="context.saving() || !name().trim()">Save changes</button></form></section>
      <finance-transactions [counterpartyId]="c.id" />
    } @else if(state().loading) { <p role="status">Loading counterparty…</p> }`,
  styles:`@use '../shared/common'; :host { display:grid; grid-template-columns:minmax(0,1fr); gap:1.5rem; min-width:0; } .counterparty-detail { padding:1.5rem; } header { display:flex; align-items:center; gap:1rem; margin-bottom:1.5rem; } h2,p { margin:.2rem 0; } p,small { color:var(--app-muted); } form,label { display:grid; gap:.5rem; } form { gap:1rem; } form > button { justify-self:start; } .form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem; } .alias-editor { display:grid; gap:.75rem; } .alias-list { display:flex; flex-wrap:wrap; gap:.5rem; list-style:none; margin:0; padding:0; } .alias-list li { min-width:0; max-width:100%; } .alias-add { display:flex; gap:.5rem; } .alias-add input { flex:1; } .alias-empty { color:var(--app-muted); font-size:.85rem; } @media(max-width:640px) { .form-grid { grid-template-columns:1fr; } }`
})
export class CounterpartyDetailComponent {
  readonly context=inject(FinanceContext);
  private readonly router=inject(Router);
  private readonly params=toSignal(inject(ActivatedRoute).paramMap);
  private readonly query=computed(()=>({id:Number(this.params()?.get('id')),revision:this.context.revision()}));
  readonly state=loadResource(this.query,q=>this.context.api.client.financeGetCounterparty(q.id));
  readonly name=signal(''); readonly website=signal(''); readonly aliases=signal<string[]>([]);
  readonly aliasDraft=signal('');
  readonly canAddAlias=computed(()=>!!this.aliasDraft().trim() && !this.aliases().includes(this.aliasDraft().trim()));
  constructor() { effect(()=>{ const c=this.state().data; if(!c)return; this.name.set(c.name);this.website.set(c.websiteUrl ?? '');this.aliases.set(c.aliases);this.aliasDraft.set(''); if(c.id!==this.query().id) void this.router.navigate(['/finances/counterparties',c.id],{replaceUrl:true}); }); }
  addAlias() { if(this.context.saving() || !this.canAddAlias())return; this.aliases.update(aliases=>[...aliases,this.aliasDraft().trim()]);this.aliasDraft.set(''); }
  removeAlias(alias:string) { if(!this.context.saving())this.aliases.update(aliases=>aliases.filter(value=>value!==alias)); }
  async save() { const c=this.state().data; if(!c)return; await this.context.write('counterparties.save',{version:c.version,name:this.name(),websiteUrl:this.website(),aliases:this.aliases()},p=>this.context.api.client.financeSaveCounterparty(c.id,p)); }
}
