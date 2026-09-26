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
  selector: "finance-account-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./account-dialog.component.html",
  styleUrl: "./dialog.scss",
})
export class AccountDialogComponent implements OnInit {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private fb = inject(FormBuilder).nonNullable;
  get saving() {
    return this.context.saving();
  }
  get currencies() {
    return this.context.currencies();
  }

  readonly account = input<FinanceAccount>();
  readonly counterparty = input(false);
  readonly kinds = assetKinds;
  readonly form = this.fb.group({
    name: ["", Validators.required],
    kind: this.fb.control<FinanceAccountKind>("CASH"),
    currency: ["CHF", Validators.required],
    active: [true],
    includeNetWorth: [true],
  });
  ngOnInit() {
    const a = this.account();
    if (a) this.form.patchValue(a);
    else if (this.counterparty())
      this.form.patchValue({ kind: "EXPENSE", includeNetWorth: false });
  }
  async saveAccount() {
    if (this.form.invalid) return;
    const a = this.account();
    const result = await this.context.write(
      "accounts.save",
      { ...this.form.getRawValue(), id: a?.id, version: a?.version },
      (p) =>
        a
          ? this.context.api.client.financeUpdateAccounts(a.id, p)
          : this.context.api.client.financeCreateAccounts(p),
    );
    if (result) this.dialogs.close();
  }
}
