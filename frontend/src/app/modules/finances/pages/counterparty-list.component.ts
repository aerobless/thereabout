import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {TableModule} from 'primeng/table';
import {SelectModule} from 'primeng/select';
import {Subject, debounceTime, firstValueFrom} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AppModalComponent} from '../../../shared/modal/app-modal.component';
import {FinanceCounterparty, FinanceCounterpartyMergeResult, FinanceCounterpartyQuery} from '../../../../../generated/backend-api/thereabout';
import {FinanceContext, loadResource} from '../shared/finance-ui';
import {errorMessage} from '../shared/finance-resource';
import {CounterpartyLogoComponent} from '../shared/counterparty-logo.component';
import {counterpartyAccountKinds} from '../shared/counterparty-presentation';
@Component({
  selector: 'finance-counterparties',
  imports: [FormsModule, RouterLink, TableModule, SelectModule, AppModalComponent, CounterpartyLogoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './counterparty-list.component.html', styleUrl: './accounts.component.scss'
})
export class CounterpartyListComponent {
  readonly context = inject(FinanceContext);
  q = ''; kind: FinanceCounterpartyQuery.KindEnum | null = null; active: boolean | null = null;
  readonly kindOptions = [{label:'All kinds', value:null}, {label:'Expense', value:'EXPENSE'}, {label:'Revenue', value:'REVENUE'}];
  readonly statusOptions = [{label:'All statuses', value:null}, {label:'Active', value:true}, {label:'Inactive', value:false}];
  readonly filters = signal<FinanceCounterpartyQuery>({page:0, pageSize:50});
  private readonly query = computed(() => ({...this.filters(), revision:this.context.revision()}));
  readonly state = loadResource(this.query, q => this.context.api.client.financeListCounterparties(q.q,q.page,q.pageSize,q.kind,q.active));
  readonly items = computed(() => (this.state().data?.items ?? []).map(c => ({...c, accountKinds:counterpartyAccountKinds(c.accounts)})));
  readonly selected = signal<FinanceCounterparty[]>([]);
  readonly visible = signal(false);
  readonly loadingPreview = signal(false);
  readonly preview = signal<FinanceCounterpartyMergeResult | null>(null);
  readonly previewSelection = computed(() => (this.preview()?.selected ?? []).map(c => ({...c, accountKinds:counterpartyAccountKinds(c.accounts)})));
  readonly error = signal('');
  name = ''; websiteUrl = ''; targetId = 0;
  readonly searchChanges = new Subject<void>();
  constructor() { this.searchChanges.pipe(debounceTime(250),takeUntilDestroyed()).subscribe(() => this.filter()); }
  filter() { this.filters.set({q:this.q, kind:this.kind ?? undefined, active:this.active ?? undefined, page:0,pageSize:50}); }
  page(event:{first?:number}) { this.filters.update(q => ({...q,page:Math.floor((event.first ?? 0)/50)})); }
  checked(id:number) { return this.selected().some(c => c.id === id); }
  toggle(c:FinanceCounterparty,event:Event) {
    if (!(event.target instanceof HTMLInputElement)) return;
    this.selected.update(items => event.target instanceof HTMLInputElement && event.target.checked ? [...items,c] : items.filter(item => item.id !== c.id));
  }
  open() { const first = this.selected()[0]; if (!first) return; this.targetId=first.id; this.name=first.name; this.websiteUrl=first.websiteUrl ?? ''; this.preview.set(null); this.visible.set(true); void this.review(); }
  targetChanged() { const selected = this.selected().find(c => c.id === this.targetId); if (selected) { this.name = selected.name; this.websiteUrl = selected.websiteUrl ?? ""; } this.changed(); }
  changed() { this.preview.set(null); this.error.set(''); }
  close() { if (!this.context.saving() && !this.loadingPreview()) { this.visible.set(false); this.preview.set(null); } }
  async review() {
    if (this.loadingPreview() || this.context.saving()) return;
    this.loadingPreview.set(true); this.error.set('');
    try { this.preview.set(await firstValueFrom(this.context.api.client.financePreviewCounterpartyMerge({ids:this.selected().map(c=>c.id),targetId:this.targetId,name:this.name,websiteUrl:this.websiteUrl}))); }
    catch(error) { this.error.set(errorMessage(error)); }
    finally { this.loadingPreview.set(false); }
  }
  async combine() {
    const preview=this.preview(); if (!preview || preview.conflicts.length) return;
    const result=await this.context.write('counterparties.merge', {
      ids:preview.selected.map(c=>c.id),targetId:preview.counterparty.id,name:preview.counterparty.name,websiteUrl:preview.counterparty.websiteUrl,
      versions:preview.selected.map(c=>({id:c.id,version:c.version}))
    }, request=>this.context.api.client.financeMergeCounterparties(request));
    if(result) { this.selected.set([]); this.close(); }
    else this.error.set(this.context.error());
  }
}
