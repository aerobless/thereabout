import { ChangeDetectionStrategy, Component, inject } from "@angular/core";
import { DialogModule } from "primeng/dialog";
import { FinanceDialogs, FinanceContext } from "../shared/finance-ui";
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
    DialogModule,
    AccountDialogComponent,
    TransactionDialogComponent,
    CategoriesDialogComponent,
    ValuationDialogComponent,
    RatesDialogComponent,
    HistoryDialogComponent,
    DeletionDialogComponent,
  ],
  template: ` @if (dialogs.selected(); as dialog) {
    <p-dialog
      [visible]="true"
      (visibleChange)="close()"
      [modal]="true"
      [header]="titles[dialog.kind]"
      [style]="{ width: '680px', maxWidth: '94vw' }"
      [draggable]="false"
      [closable]="!context.saving()"
    >
      @if (context.error()) {
        <p role="alert">{{ context.error() }}</p>
      }
      @switch (dialog.kind) {
        @case ("account") {
          <finance-account-dialog
            [account]="dialog.account"
            [counterparty]="dialog.counterparty ?? false"
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
    </p-dialog>
  }`,
})
export class DialogHostComponent {
  readonly dialogs = inject(FinanceDialogs);
  readonly context = inject(FinanceContext);
  readonly titles = {
    account: "Account",
    transaction: "Transaction",
    categories: "Categories",
    valuation: "Record total value",
    rates: "Exchange rates",
    history: "Transaction details",
    deletion: "Confirm change",
  };
  close() {
    if (!this.context.saving()) this.dialogs.close();
  }
}
