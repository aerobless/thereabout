import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {SelectModule} from 'primeng/select';
@Component({
  selector: 'finance-original-amount',
  imports: [FormsModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="original-amount">
    <input aria-label="Original amount" inputmode="decimal" [ngModel]="amount()" [ngModelOptions]="{standalone:true}"
      (ngModelChange)="amountChange.emit($event)" (blur)="amountBlur.emit()" [disabled]="disabled()" placeholder="Amount" />
    <p-select ariaLabel="Original currency" [options]="currencies()" optionLabel="code" optionValue="code"
      [ngModel]="currency() || null" [ngModelOptions]="{standalone:true}" (ngModelChange)="currencyChange.emit($event ?? '')"
      [showClear]="true" [filter]="true" filterBy="code" [disabled]="disabled()" appendTo="body" placeholder="Currency" />
  </div>`,
  styles: [`:host {display:block; min-width:0;} .original-amount {display:flex; align-items:stretch; border:1px solid var(--p-inputtext-border-color); border-radius:var(--p-inputtext-border-radius); overflow:hidden;}
    input {flex:1; min-width:0; width:0; border:0; border-radius:0; padding:.65rem .75rem; background:var(--p-inputtext-background); color:var(--p-inputtext-color); font:inherit;}
    p-select {flex:0 0 9rem; border:0; border-left:1px solid var(--p-inputtext-border-color); border-radius:0;}
    .original-amount:focus-within {outline:1px solid var(--p-primary-color);}`]
})
export class OriginalAmountComponent {
  readonly amount = input(''); readonly currency = input('');
  readonly currencies = input<{code: string}[]>([]); readonly disabled = input(false);
  readonly amountChange = output<string>(); readonly currencyChange = output<string>(); readonly amountBlur = output<void>();
}
