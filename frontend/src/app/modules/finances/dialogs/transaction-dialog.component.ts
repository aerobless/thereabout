import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  FormBuilder,
  ReactiveFormsModule,
  FormsModule,
  Validators,
} from "@angular/forms";
import { map, of } from "rxjs";
import {
  FinanceContext,
  FinanceDialogs,
  FinanceTransaction,
  FinanceTransactionType,
  FinanceEffect,
  FinanceTransactionInput,
  FinanceAccount,
  loadResource,
  localNow,
} from "../shared/finance-ui";
@Component({
  selector: "finance-transaction-dialog",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: "./transaction-dialog.component.html",
  styleUrl: "./dialog.scss",
})
export class TransactionDialogComponent implements OnInit {
  readonly transaction = input<FinanceTransaction>();
  readonly context = inject(FinanceContext);
  readonly dialogs = inject(FinanceDialogs);
  private fb = inject(FormBuilder).nonNullable;
  readonly form = this.fb.group({
    type: this.fb.control<FinanceTransactionType>("WITHDRAWAL"),
    effect: this.fb.control<FinanceEffect>("OPERATING"),
    description: ["", [Validators.required, Validators.maxLength(1024)]],
    date: [localNow(), Validators.required],
    sourceId: [0, Validators.min(1)],
    destinationId: [0, Validators.min(1)],
    sourceAmount: ["", Validators.required],
    destinationAmount: ["", Validators.required],
    sourceCurrency: ["CHF", Validators.required],
    destinationCurrency: ["CHF", Validators.required],
    categoryId: [0],
    notes: [""],
    externalReference: [""],
    foreignCurrency: [""],
    foreignAmount: [""],
  });
  counterQuery = "";
  private search = signal({
    type: "WITHDRAWAL" as FinanceTransactionType,
    q: "",
  });
  private counterRequest = computed(() => ({
    ...this.search(),
    revision: this.context.revision(),
  }));
  readonly counterparties = loadResource(this.counterRequest, (q) =>
    q.type === "TRANSFER"
      ? of([] as FinanceAccount[])
      : this.context.api
          .accounts({
            scope: "COUNTERPARTY",
            kind: q.type === "DEPOSIT" ? "REVENUE" : "EXPENSE",
            q: q.q,
            pageSize: 200,
          })
          .pipe(map((r) => r.items)),
  );
  private originalCounter = signal<FinanceAccount[]>([]);
  ngOnInit() {
    const t = this.transaction();
    if (t) {
      this.form.patchValue({
        type: t.type,
        effect: t.effect,
        description: t.description,
        date: t.occurredAt.replace(" ", "T"),
        sourceId: t.sourceAccountId,
        destinationId: t.destinationAccountId,
        sourceAmount: t.sourceAmount,
        destinationAmount: t.destinationAmount,
        sourceCurrency: t.sourceCurrency,
        destinationCurrency: t.destinationCurrency,
        categoryId: t.categoryId ?? 0,
        notes: t.notes ?? "",
        externalReference: t.externalReference ?? "",
        foreignCurrency: t.foreignCurrency ?? "",
        foreignAmount: t.foreignAmount?.replace(/^-/, "") ?? "",
      });
      this.originalCounter.set([
        {
          id: t.type === "DEPOSIT" ? t.sourceAccountId : t.destinationAccountId,
          name: t.type === "DEPOSIT" ? t.sourceName : t.destinationName,
          kind: t.type === "DEPOSIT" ? "REVENUE" : "EXPENSE",
          currency: t.sourceCurrency,
          active: true,
          deleted: false,
          includeNetWorth: false,
          version: 0,
          balance: "0",
        },
      ]);
    } else {
      this.form.patchValue({
        sourceId:
          this.context.accounts().find((a) => a.kind === "CASH" && a.active)
            ?.id ?? 0,
      });
    }
    this.transactionOptions();
  }
  get categories() {
    return this.context.categories();
  }
  get currencies() {
    return this.context.currencies();
  }
  get saving() {
    return this.context.saving();
  }
  private get own() {
    return this.context.accounts().filter((a) => a.active);
  }
  private get counters() {
    const list = this.counterparties().data ?? [];
    return [
      ...list,
      ...this.originalCounter().filter((a) => !list.some((b) => a.id === b.id)),
    ];
  }
  private get importedTechnical() {
    const t = this.transaction();
    return t &&
      t.type === this.form.controls.type.value &&
      (t.type === "OPENING" || t.type === "RECONCILIATION")
      ? t
      : undefined;
  }
  get sourceOptions(): ReadonlyArray<
    Pick<FinanceAccount, "id" | "name" | "currency">
  > {
    const t = this.importedTechnical;
    if (t)
      return [
        {
          id: t.sourceAccountId,
          name: t.sourceName,
          currency: t.sourceCurrency,
        },
      ];
    return this.form.controls.type.value === "DEPOSIT"
      ? this.counters
      : this.own;
  }
  get destinationOptions(): ReadonlyArray<
    Pick<FinanceAccount, "id" | "name" | "currency">
  > {
    const t = this.importedTechnical;
    if (t)
      return [
        {
          id: t.destinationAccountId,
          name: t.destinationName,
          currency: t.destinationCurrency,
        },
      ];
    return this.form.controls.type.value === "TRANSFER"
      ? this.own
      : this.form.controls.type.value === "DEPOSIT"
        ? this.own
        : this.counters;
  }
  transactionOptions() {
    this.search.set({
      type: this.form.controls.type.value,
      q: this.counterQuery,
    });
  }
  changeType() {
    this.form.patchValue({ sourceId: 0, destinationId: 0 });
    this.originalCounter.set([]);
    this.transactionOptions();
  }
  alignCurrency() {
    const v = this.form.getRawValue();
    const s = this.sourceOptions.find((a) => a.id === v.sourceId),
      d = this.destinationOptions.find((a) => a.id === v.destinationId);
    const sourceCurrency =
      v.type === "DEPOSIT" ? (d?.currency ?? "CHF") : (s?.currency ?? "CHF");
    const destinationCurrency =
      v.type === "TRANSFER" ? (d?.currency ?? "CHF") : sourceCurrency;
    this.form.patchValue({
      sourceCurrency,
      destinationCurrency,
      ...(sourceCurrency === destinationCurrency
        ? { destinationAmount: v.sourceAmount }
        : {}),
    });
  }
  async saveTransaction() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const t = this.transaction(),
      v = this.form.getRawValue();
    const input: Omit<FinanceTransactionInput, "requestKey"> = {
      ...v,
      id: t?.id,
      version: t?.version,
      foreignCurrency: v.foreignCurrency || undefined,
      foreignAmount: v.foreignCurrency ? v.foreignAmount : undefined,
    };
    const result = await this.context.write("transactions.save", input, (p) =>
      t
        ? this.context.api.client.financeUpdateTransactions(t.id, p)
        : this.context.api.client.financeCreateTransactions(p),
    );
    if (result) this.dialogs.close();
  }
}
