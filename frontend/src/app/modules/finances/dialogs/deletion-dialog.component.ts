import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
} from "@angular/core";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceTransaction,
} from "../shared/finance-ui";
@Component({
  selector: "finance-deletion-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: "./dialog.scss",
  template: ` <div class="editor">
    <p>
      {{ transaction().deleted ? "Restore" : "Delete" }} “{{
        transaction().description
      }}”?
    </p>
    @if (!transaction().deleted) {
      <p>The transaction can be restored from the deleted entries view.</p>
    }
    <div class="dialog-footer">
      <button [disabled]="context.saving()" (click)="dialogs.close()">
        Cancel</button
      ><button
        class="primary"
        [disabled]="context.saving()"
        (click)="confirm()"
      >
        {{
          transaction().deleted ? "Restore transaction" : "Delete transaction"
        }}
      </button>
    </div>
  </div>`,
})
export class DeletionDialogComponent {
  readonly transaction = input.required<FinanceTransaction>();
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  async confirm() {
    const t = this.transaction();
    const result = await this.context.write(
      t.deleted ? "transactions.restore" : "transactions.delete",
      { id: t.id, version: t.version },
      (p) =>
        t.deleted
          ? this.context.api.client.financeRestoreTransaction(t.id, p)
          : this.context.api.client.financeDeleteTransaction(t.id, p),
    );
    if (result) this.dialogs.close();
  }
}
