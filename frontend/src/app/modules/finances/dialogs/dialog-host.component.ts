import { ChangeDetectionStrategy, Component, inject } from "@angular/core";
import { AppModalComponent } from '../../../shared/modal/app-modal.component';
import { FinanceDialogs, FinanceContext } from "../shared/finance-ui";
import { ImportHintsDialogComponent } from "./import-hints-dialog.component";
import { ImportDialogComponent } from "./import-dialog.component";
import { AccountDialogComponent } from "./account-dialog.component";
import { TransactionDialogComponent } from "./transaction-dialog.component";
import { CategoriesDialogComponent } from "./categories-dialog.component";
import { ValuationDialogComponent } from "./valuation-dialog.component";
import { RatesDialogComponent } from "./rates-dialog.component";
import { HistoryDialogComponent } from "./history-dialog.component";
import { DeletionDialogComponent } from "./deletion-dialog.component";
@Component({
  selector: "finance-dialog-host",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppModalComponent,
    ImportDialogComponent,
    ImportHintsDialogComponent,
    AccountDialogComponent,
    TransactionDialogComponent,
    CategoriesDialogComponent,
    ValuationDialogComponent,
    RatesDialogComponent,
    HistoryDialogComponent,
    DeletionDialogComponent,
  ],
  template: ` @if (dialogs.selected(); as dialog) {
    <app-modal
      [visible]="true"
      (visibleChange)="close()"
      [header]="titles[dialog.kind]"
      [dialogStyle]="{
        width: dialog.kind === 'import' ? '1200px' : dialog.kind === 'categories' ? '900px' : '720px',
        maxWidth: '94vw',
        maxHeight: '92vh',
      }"
      [styleClass]="dialog.kind === 'import' ? 'finance-dialog finance-import-modal' : 'finance-dialog'"
      [dismissible]="!context.saving() && !dialogs.blocked() && !dialogs.hintAccountId()">
      @if (context.error()) {
        <p role="alert">{{ context.error() }}</p>
      }
      @switch (dialog.kind) {
        @case ("hints") { <finance-import-hints-dialog [accountId]="dialog.accountId" (savingChange)="dialogs.blocked.set($event)" /> }
        @case ("import") { <finance-import-dialog [accountId]="dialog.accountId" /> }
        @case ("account") {
          <finance-account-dialog
            [account]="dialog.account"
            [counterparty]="dialog.counterparty ?? false"
            [userId]="dialog.userId"
          />
        }
        @case ("transaction") {
          <finance-transaction-dialog [transaction]="dialog.transaction" />
        }
        @case ("categories") {
          <finance-categories-dialog />
        }
        @case ("valuation") {
          <finance-valuation-dialog [account]="dialog.account" />
        }
        @case ("rates") {
          <finance-rates-dialog />
        }
        @case ("deletion") {
          <finance-deletion-dialog [transaction]="dialog.transaction" />
        }
        @case ("history") {
          <finance-history-dialog [transaction]="dialog.transaction" />
        }
      }
    </app-modal>
  }
  @if (dialogs.hintAccountId(); as accountId) {
    <app-modal [visible]="true" (visibleChange)="dialogs.closeImportHints()" header="Hints"
      [dialogStyle]="{width: '720px', maxWidth: '94vw', maxHeight: '92vh'}"
      styleClass="finance-dialog" [dismissible]="!dialogs.hintsBlocked()">
      <finance-import-hints-dialog [accountId]="accountId" (savingChange)="dialogs.hintsBlocked.set($event)" />
    </app-modal>
  }`,
})
export class DialogHostComponent {
  readonly dialogs = inject(FinanceDialogs);
  readonly context = inject(FinanceContext);
  readonly titles = {
    import: "Import transactions",
    hints: "Hints",
    account: "Account",
    transaction: "Transaction",
    categories: "Categories",
    valuation: "Record total value",
    rates: "Exchange rates",
    history: "Transaction details",
    deletion: "Confirm change",
  };
  close() {
    if (!this.context.saving() && !this.dialogs.blocked() && !this.dialogs.hintAccountId()) this.dialogs.close();
  }
}
