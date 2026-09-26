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
  selector: "finance-valuation-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./valuation-dialog.component.html",
  styleUrl: "./dialog.scss",
})
export class ValuationDialogComponent {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private fb = inject(FormBuilder).nonNullable;
  get saving() {
    return this.context.saving();
  }
  get currencies() {
    return this.context.currencies();
  }

  readonly account = input.required<FinanceAccount>();
  readonly money = this.context.money.bind(this.context);
  get currentAccount() {
    return this.account();
  }
  readonly form = this.fb.group({
    date: [localNow(), Validators.required],
    reportedValue: ["", Validators.required],
    reference: ["", Validators.required],
  });
  private previewState = signal<FinanceValuationPreview | null>(null);
  get preview() {
    return this.previewState();
  }
  set preview(v: FinanceValuationPreview | null) {
    this.previewState.set(v);
  }
  private query = computed(() => ({
    id: this.account().id,
    revision: this.context.revision(),
  }));
  readonly history = loadResource(this.query, (q) =>
    this.context.api.client.financeListValuations(q.id),
  );
  get valuationHistory() {
    return this.history().data?.items ?? [];
  }
  private previewGeneration = 0;
  async previewValuation() {
    if (this.form.invalid) return;
    const generation = ++this.previewGeneration;
    const v = this.form.getRawValue();
    this.preview = null;
    try {
      const result = await firstValueFrom(
        this.context.api.client.financePreviewValuation({
          accountId: this.account().id,
          date: v.date,
          reportedValue: v.reportedValue,
        }),
      );
      if (
        generation === this.previewGeneration &&
        JSON.stringify(v) === JSON.stringify(this.form.getRawValue())
      )
        this.preview = result;
    } catch (e) {
      this.context.error.set(errorMessage(e));
    }
  }
  async saveValuation() {
    const preview = this.preview;
    if (!preview || this.form.invalid) return;
    const result = await this.context.write(
      "valuations.save",
      {
        ...this.form.getRawValue(),
        accountId: this.account().id,
        expectedBalance: preview.previousBalance,
      },
      (p) => this.context.api.client.financeCreateValuation(p),
    );
    if (result) this.dialogs.close();
  }
}
