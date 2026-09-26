import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  OnInit,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { firstValueFrom } from "rxjs";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceAccount,
  FinanceAccountKind,
  FinanceCategory,
  FinanceTransaction,
  FinanceValuationPreview,
  assetKinds,
  loadResource,
  localNow,
  today,
  errorMessage,
} from "../shared/finance-ui";
@Component({
  selector: "finance-history-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./history-dialog.component.html",
  styleUrl: "./dialog.scss",
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
  txAmount(t: FinanceTransaction) {
    return this.context.money(t.sourceAmount, t.sourceCurrency);
  }
  openTransaction(transaction: FinanceTransaction) {
    this.dialogs.open({ kind: "transaction", transaction });
  }
}
