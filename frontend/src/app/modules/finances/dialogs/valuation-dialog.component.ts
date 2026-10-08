import { FinanceDateInputComponent } from "../shared/finance-date-input.component";
import { moneyInput, dateTimeInput } from "../shared/finance-format";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal, untracked } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { firstValueFrom, of } from "rxjs";
import { AppModalComponent } from "../../../shared/modal/app-modal.component";
import {
  FinanceContext, FinanceDialogs, FinanceAccount, FinanceValuation, FinanceValuationPreview,
  loadResource, localNow, errorMessage,
} from "../shared/finance-ui";
@Component({
  selector: "finance-valuation-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FinanceDateInputComponent, CommonModule, ReactiveFormsModule, AppModalComponent],
  templateUrl: "./valuation-dialog.component.html",
  styleUrl: "./dialog.scss",
})
export class ValuationDialogComponent {
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private fb = inject(FormBuilder).nonNullable;
  readonly account = input<FinanceAccount>();
  readonly valuationId = input<number>();
  readonly editing = signal<FinanceValuation | null>(null);
  readonly deletion = signal<FinanceValuation | null>(null);
  readonly money = this.context.money.bind(this.context);
  get saving() { return this.context.saving(); }
  get currentAccount() { return this.account() ?? this.context.accounts().find(a => a.id === this.editing()?.accountId); }
  readonly form = this.fb.group({
    date: [localNow(), Validators.required], reportedValue: ["", Validators.required], reference: ["", Validators.required],
  });
  private previewState = signal<FinanceValuationPreview | null>(null);
  get preview() { return this.previewState(); }
  set preview(v: FinanceValuationPreview | null) { this.previewState.set(v); }
  private readonly lookup = computed(() => ({id: this.valuationId()}));
  readonly loaded = loadResource(this.lookup, q => q.id ? this.context.api.client.financeListValuations(undefined, q.id) : of({items: []}));
  private readonly query = computed(() => ({id: this.currentAccount?.id, revision: this.context.revision()}));
  readonly history = loadResource(this.query, q => q.id ? this.context.api.client.financeListValuations(q.id) : of({items: []}));
  get valuationHistory() { return this.history().data?.items ?? []; }
  private previewGeneration = 0;
  private previewInput = "";
  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => { this.previewGeneration++; this.preview = null; });
    effect(() => {
      const valuation = this.loaded().data?.items[0];
      if (valuation) untracked(() => this.edit(valuation));
    });
    effect(() => this.dialogs.blocked.set(!!this.deletion()));
    inject(DestroyRef).onDestroy(() => { this.previewGeneration++; this.dialogs.blocked.set(false); });
  }
  edit(valuation: FinanceValuation) {
    this.editing.set(valuation); this.previewGeneration++; this.preview = null;
    if (valuation.deleted) this.form.disable(); else this.form.enable();
    this.form.reset({date: dateTimeInput(valuation.occurredAt), reportedValue: moneyInput(valuation.reportedValue, this.currencyPlaces()), reference: valuation.reference});
  }
  private currencyPlaces() { return this.context.currencies().find(c => c.code === this.currentAccount?.currency)?.decimalPlaces ?? 2; }
  formatValue() {
    const control = this.form.controls.reportedValue, formatted = moneyInput(control.value, this.currencyPlaces());
    if (formatted !== control.value) control.setValue(formatted);
  }
  private values() {
    const v = this.form.getRawValue(), original = this.editing();
    return {...v, id: original?.id, version: original?.version, accountId: this.currentAccount?.id ?? 0,
      date: original && !this.form.controls.date.dirty ? original.occurredAt.replace(" ", "T") : v.date,
      reportedValue: original && !this.form.controls.reportedValue.dirty ? original.reportedValue : v.reportedValue};
  }
  async previewValuation() {
    if (this.form.invalid || !this.currentAccount || this.editing()?.deleted) return;
    const generation = ++this.previewGeneration, values = this.values();
    this.preview = null;
    try {
      const result = await firstValueFrom(this.context.api.client.financePreviewValuation(values));
      if (generation === this.previewGeneration && JSON.stringify(values) === JSON.stringify(this.values())) {
        this.previewInput = JSON.stringify(values); this.preview = result;
      }
    } catch (e) { if (generation === this.previewGeneration) this.context.error.set(errorMessage(e)); }
  }
  async saveValuation() {
    const preview = this.preview;
    if (!preview || this.form.invalid || JSON.stringify(this.values()) !== this.previewInput) return;
    const result = await this.context.write("valuations.save", {...this.values(), expectedBalance: preview.previousBalance}, p => this.context.api.client.financeCreateValuation(p));
    if (result) this.dialogs.close();
  }
  async confirmDeletion() {
    const valuation = this.deletion(); if (!valuation) return;
    const result = await this.context.write(valuation.deleted ? "valuations.restore" : "valuations.delete", {id: valuation.id, version: valuation.version},
      p => valuation.deleted ? this.context.api.client.financeRestoreValuation(valuation.id, p) : this.context.api.client.financeDeleteValuation(valuation.id, p));
    if (result) { this.deletion.set(null); if (this.editing()?.id === result.id) this.edit(result); }
  }
}
