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
  selector: "finance-rates-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./rates-dialog.component.html",
  styleUrl: "./dialog.scss",
})
export class RatesDialogComponent {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private fb = inject(FormBuilder).nonNullable;
  get saving() {
    return this.context.saving();
  }
  get currencies() {
    return this.context.currencies();
  }

  readonly form = this.fb.group({
    fromCurrency: ["EUR", Validators.required],
    toCurrency: ["CHF"],
    date: [today(), Validators.required],
    rate: ["", Validators.required],
    version: [0],
  });
  private query = signal({ currency: "EUR", date: today(), revision: 0 });
  readonly manual = loadResource(this.query, (q) =>
    this.context.api.client.financeListRates(q.currency, q.date),
  );
  readonly latest = loadResource(this.context.revision, () =>
    this.context.api.client.financeListRates(),
  );
  get rateRows() {
    return this.latest().data?.items ?? [];
  }
  get rateReady() {
    return !!this.manual().data && !this.manual().loading && !this.form.invalid;
  }
  constructor() {
    effect(() => {
      const rate = this.manual().data?.items.find(
        (r) => r.source === "MANUAL" && r.toCurrency === "CHF",
      );
      if (this.manual().data)
        this.form.patchValue({
          version: rate?.version ?? 0,
          rate: rate?.rate ?? "",
        });
    });
  }
  prepareRate() {
    const v = this.form.getRawValue();
    this.query.set({
      currency: v.fromCurrency,
      date: v.date,
      revision: this.context.revision(),
    });
  }
  async saveRate() {
    if (!this.rateReady) return;
    const result = await this.context.write(
      "rates.save",
      this.form.getRawValue(),
      (p) => this.context.api.client.financeSaveRate(p),
    );
    if (result) this.prepareRate();
  }
  async refreshRates() {
    const result = await this.context.write("rates.refresh", {}, (p) =>
      this.context.api.client.financeRefreshRates(p),
    );
    if (result) this.prepareRate();
  }
}
