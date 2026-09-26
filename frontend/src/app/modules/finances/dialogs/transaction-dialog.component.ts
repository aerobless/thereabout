import { FinanceDateInputComponent } from "../shared/finance-date-input.component";
import { SelectModule } from "primeng/select";
import { AutoCompleteModule } from "primeng/autocomplete";
import { dateTimeInput, moneyInput } from "../shared/finance-format";
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
  imports: [
    FinanceDateInputComponent,
    SelectModule,
    AutoCompleteModule,
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
  ],
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
  counterpartySelection:
    | Pick<FinanceAccount, "id" | "name" | "currency">
    | string
    | null = null;
  readonly baseTypeOptions = [
    { value: "WITHDRAWAL", label: "Expense" },
    { value: "DEPOSIT", label: "Income" },
    { value: "TRANSFER", label: "Transfer" },
  ];
  get typeOptions() {
    const value = this.form.controls.type.value;
    return value === "OPENING" || value === "RECONCILIATION"
      ? [...this.baseTypeOptions, { value, label: value }]
      : this.baseTypeOptions;
  }
  get effectOptions() {
    const value = this.form.controls.effect.value;
    const options = [
      { value: "OPERATING", label: "Normal transaction" },
      { value: "VALUATION", label: "Valuation gain / loss" },
    ];
    return value === "OPENING" || value === "RECONCILIATION"
      ? [...options, { value, label: value }]
      : options;
  }
  get categoryOptions() {
    return [{ id: 0, name: "Uncategorized" }, ...this.categories];
  }
  get currencyOptions() {
    return [
      { code: "", name: "None" },
      ...this.currencies
        .filter((c) => c.enabled)
        .map((c) => ({ ...c, name: c.code })),
    ];
  }
  get suggestions() {
    const rows = this.counterparties().data ?? [];
    const name = this.counterQuery.trim();
    const matches = rows.map((a) => ({ ...a, label: a.name, create: false }));
    if (
      name &&
      !this.counterparties().loading &&
      !this.counterparties().error &&
      !rows.some((a) => a.name.toLocaleLowerCase() === name.toLocaleLowerCase())
    )
      return [
        ...matches,
        {
          id: 0,
          name,
          label: `Create “${name}”`,
          currency: this.form.controls.sourceCurrency.value,
          create: true,
        },
      ];
    return matches;
  }
  get usesCounterparty() {
    return ["WITHDRAWAL", "DEPOSIT"].includes(this.form.controls.type.value);
  }
  searchCounterparties(query: string) {
    this.counterQuery = query;
    this.transactionOptions();
  }
  counterpartyChanged(value: unknown) {
    if (typeof value === "string" || value == null) {
      this.form.controls[
        this.form.controls.type.value === "DEPOSIT"
          ? "sourceId"
          : "destinationId"
      ].setValue(0);
    }
  }
  async selectCounterparty(
    value: Pick<FinanceAccount, "id" | "name" | "currency"> & {
      create?: boolean;
    },
  ) {
    let account = value;
    if (value.create) {
      this.counterpartyChanged(null);
      const result = await this.context.write(
        "accounts.save",
        {
          name: value.name,
          kind:
            this.form.controls.type.value === "DEPOSIT"
              ? ("REVENUE" as const)
              : ("EXPENSE" as const),
          currency: value.currency,
          active: true,
          includeNetWorth: false,
        },
        (p) => this.context.api.client.financeCreateAccounts(p),
      );
      if (!result) {
        this.counterpartySelection = null;
        return;
      }
      account = result.account;
    }
    this.counterpartySelection = account;
    this.originalCounter.set([
      {
        ...account,
        kind:
          this.form.controls.type.value === "DEPOSIT" ? "REVENUE" : "EXPENSE",
        active: true,
        deleted: false,
        includeNetWorth: false,
        version: 0,
        balance: "0",
      },
    ]);
    this.form.controls[
      this.form.controls.type.value === "DEPOSIT" ? "sourceId" : "destinationId"
    ].setValue(account.id);
    this.alignCurrency(false);
  }
  formatAmount(field: "sourceAmount" | "destinationAmount" | "foreignAmount") {
    const currency =
      field === "foreignAmount"
        ? this.form.controls.foreignCurrency.value
        : field === "sourceAmount"
          ? this.form.controls.sourceCurrency.value
          : this.form.controls.destinationCurrency.value;
    const places =
      this.currencies.find((c) => c.code === currency)?.decimalPlaces ?? 2;
    this.form.controls[field].setValue(
      moneyInput(this.form.controls[field].value, places),
    );
    if (field === "sourceAmount" && this.form.controls.sourceAmount.dirty)
      this.alignCurrency();
  }

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
        date: dateTimeInput(t.occurredAt),
        sourceId: t.sourceAccountId,
        destinationId: t.destinationAccountId,
        sourceAmount: moneyInput(
          t.sourceAmount,
          this.currencyPlaces(t.sourceCurrency),
        ),
        destinationAmount: moneyInput(
          t.destinationAmount,
          this.currencyPlaces(t.destinationCurrency),
        ),
        sourceCurrency: t.sourceCurrency,
        destinationCurrency: t.destinationCurrency,
        categoryId: t.categoryId ?? 0,
        notes: t.notes ?? "",
        externalReference: t.externalReference ?? "",
        foreignCurrency: t.foreignCurrency ?? "",
        foreignAmount: moneyInput(
          t.foreignAmount?.replace(/^-/, ""),
          this.currencyPlaces(t.foreignCurrency),
        ),
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
    this.counterpartySelection = this.usesCounterparty
      ? (this.originalCounter()[0] ?? null)
      : null;
    this.transactionOptions();
  }
  private currencyPlaces(currency?: string) {
    return this.currencies.find((c) => c.code === currency)?.decimalPlaces ?? 2;
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
  get sourceOptions(): Array<Pick<FinanceAccount, "id" | "name" | "currency">> {
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
  get destinationOptions(): Array<
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
    this.counterpartySelection = null;
    this.counterQuery = "";
    this.form.patchValue({ sourceId: 0, destinationId: 0 });
    this.originalCounter.set([]);
    this.transactionOptions();
  }
  get hasImportedPrecision() {
    const t = this.transaction();
    if (!t) return false;
    return [
      [t.sourceAmount, t.sourceCurrency],
      [t.destinationAmount, t.destinationCurrency],
      [t.foreignAmount, t.foreignCurrency],
    ].some(
      ([value, currency]) =>
        (value?.split(".")[1]?.replace(/0+$/, "").length ?? 0) >
        this.currencyPlaces(currency),
    );
  }
  alignCurrency(syncAmount = true) {
    const v = this.form.getRawValue();
    const s = this.sourceOptions.find((a) => a.id === v.sourceId),
      d = this.destinationOptions.find((a) => a.id === v.destinationId);
    const sourceCurrency =
      v.type === "DEPOSIT" ? (d?.currency ?? "CHF") : (s?.currency ?? "CHF");
    const destinationCurrency =
      v.type === "TRANSFER" ? (d?.currency ?? "CHF") : sourceCurrency;
    const synchronize =
      syncAmount &&
      sourceCurrency === destinationCurrency &&
      (!this.transaction() ||
        this.form.controls.sourceAmount.dirty ||
        sourceCurrency !== v.sourceCurrency ||
        destinationCurrency !== v.destinationCurrency);
    if (synchronize) this.form.controls.destinationAmount.markAsDirty();
    this.form.patchValue({
      sourceCurrency,
      destinationCurrency,
      ...(synchronize ? { destinationAmount: v.sourceAmount } : {}),
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
      date:
        t && !this.form.controls.date.dirty
          ? t.occurredAt.replace(" ", "T")
          : v.date,
      id: t?.id,
      version: t?.version,
      foreignCurrency: v.foreignCurrency || undefined,
      sourceAmount:
        t &&
        !this.form.controls.sourceAmount.dirty &&
        v.sourceCurrency === t.sourceCurrency
          ? t.sourceAmount
          : v.sourceAmount,
      destinationAmount:
        t &&
        !this.form.controls.destinationAmount.dirty &&
        v.destinationCurrency === t.destinationCurrency
          ? t.destinationAmount
          : v.destinationAmount,
      foreignAmount: v.foreignCurrency
        ? t &&
          !this.form.controls.foreignAmount.dirty &&
          v.foreignCurrency === t.foreignCurrency
          ? t.foreignAmount?.replace(/^-/, "")
          : v.foreignAmount
        : undefined,
    };
    const result = await this.context.write("transactions.save", input, (p) =>
      t
        ? this.context.api.client.financeUpdateTransactions(t.id, p)
        : this.context.api.client.financeCreateTransactions(p),
    );
    if (result) this.dialogs.close();
  }
}
