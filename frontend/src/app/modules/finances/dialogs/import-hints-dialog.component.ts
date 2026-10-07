import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ImportHintsEditorComponent } from './import-hints-editor.component';

@Component({
  selector: 'finance-import-hints-dialog',
  imports: [ImportHintsEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p class="hint-explanation">Hints help AI interpret transactions from files for this account. They guide transaction types, counterparties and categories in future imports.</p>
    <finance-import-hints-editor [accountId]="accountId()" (savingChange)="savingChange.emit($event)" />`,
  styles: `.hint-explanation { color: var(--app-muted); font-size: 13px; line-height: 1.6; margin: 0 0 20px; }`,
})
export class ImportHintsDialogComponent {
  readonly accountId = input.required<number>();
  readonly savingChange = output<boolean>();
}
