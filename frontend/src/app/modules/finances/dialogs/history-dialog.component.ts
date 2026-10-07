import { ChangeDetectionStrategy, Component, computed, inject, input } from "@angular/core";
import { CommonModule } from "@angular/common";
import {RouterLink} from "@angular/router";
import {CategoryLabelComponent} from "../shared/category-label.component";
import { ReactiveFormsModule } from "@angular/forms";

import { FinanceContext, FinanceDialogs, FinanceTransaction, loadResource } from "../shared/finance-ui";
@Component({
  selector: "finance-history-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CategoryLabelComponent],
  templateUrl: "./history-dialog.component.html",
  styleUrls: ["./dialog.scss", "./history-dialog.component.scss"],
})
export class HistoryDialogComponent {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  readonly transaction = input.required<FinanceTransaction>();
  private id = computed(() => this.transaction().id);
  readonly state = loadResource(this.id, (id) =>
    this.context.api.client.financeTransactionDetail(id),
  );
  get form() {
    return this.state().data?.transaction ?? this.transaction();
  }
  get history() {
    return this.state().data?.history ?? [];
  }
  changes(beforeJson: string | undefined, afterJson: string | undefined): {label:string; before:string; after:string}[] {
    const parse = (text:string | undefined): Record<string,unknown> => {
      try { const value:unknown=JSON.parse(text ?? '{}'); return value && typeof value==='object' && !Array.isArray(value) ? value as Record<string,unknown> : {}; } catch { return {}; }
    };
    const before=parse(beforeJson), after=parse(afterJson);
    const labels: Record<string,string> = {description:'Description',occurredAt:'Date',sourceName:'From',destinationName:'To',sourceAmount:'Source amount',destinationAmount:'Destination amount',sourceCurrency:'Source currency',destinationCurrency:'Destination currency',categoryName:'Category',categoryId:'Category',notes:'Notes',deleted:'Deleted',type:'Type',effect:'Effect',externalReference:'Source reference'};
    const display = (key:string,value:unknown):string => {
      if(value == null || value === '') return '—';
      if(key === 'occurredAt' && typeof value === 'string') {
        const date = new Date(value.replace(' ','T'));
        if(Number.isFinite(date.getTime())) return new Intl.DateTimeFormat('en-US',{dateStyle:'medium',timeStyle:'short'}).format(date);
      }
      if(typeof value === 'boolean') return value ? 'Yes' : 'No';
      return typeof value === 'object' ? JSON.stringify(value) : String(value);
    };
    return Object.keys(labels).filter(key=>JSON.stringify(before[key])!==JSON.stringify(after[key]) && !(key==='categoryId' && (after['categoryName'] || before['categoryName'])))
      .map(key=>({label:labels[key],before:display(key,before[key]),after:display(key,after[key])}));
  }
  txAmount(t: FinanceTransaction) {
    return this.context.money(t.sourceAmount, t.sourceCurrency);
  }
  openTransaction(transaction: FinanceTransaction) {
    this.dialogs.open({ kind: "transaction", transaction });
  }
}
