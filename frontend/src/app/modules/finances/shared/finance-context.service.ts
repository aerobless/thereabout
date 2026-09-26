import { computed, inject, Injectable, signal } from "@angular/core";
import { Observable, firstValueFrom, forkJoin } from "rxjs";
import { FinanceApi } from "./finance-api.service";
import { loadResource, errorMessage } from "./finance-resource";
@Injectable()
export class FinanceContext {
  readonly api = inject(FinanceApi);
  readonly revision = signal(0);
  readonly saving = signal(false);
  readonly error = signal("");
  readonly notice = signal("");
  private pending?: { fingerprint: string; key: string };
  readonly metadata = loadResource(this.revision, () =>
    forkJoin({
      accounts: this.api.accounts({
        scope: "OWN",
        pageSize: 200,
        includeInactive: true,
      }),
      categories: this.api.client.financeListCategories(),
      currencies: this.api.client.financeCurrencies(),
    }),
  );
  readonly accounts = computed(
    () => this.metadata().data?.accounts.items ?? [],
  );
  readonly categories = computed(
    () => this.metadata().data?.categories.items ?? [],
  );
  readonly currencies = computed(
    () => this.metadata().data?.currencies.items ?? [],
  );
  money(value: string | number | null | undefined, currency = "CHF"): string {
    if (value == null || !Number.isFinite(Number(value))) return "—";
    const places =
      this.currencies().find((c) => c.code === currency)?.decimalPlaces ?? 2;
    return `${currency} ${new Intl.NumberFormat("en-CH", { minimumFractionDigits: places, maximumFractionDigits: places }).format(Number(value))}`;
  }
  async write<I extends object, T>(
    name: string,
    input: I,
    execute: (request: I & { requestKey: string }) => Observable<T>,
  ): Promise<T | undefined> {
    if (this.saving()) return;
    this.saving.set(true);
    this.error.set("");
    const fingerprint = JSON.stringify([name, input]);
    if (this.pending?.fingerprint !== fingerprint)
      this.pending = { fingerprint, key: crypto.randomUUID() };
    try {
      const result = await firstValueFrom(
        execute({ ...input, requestKey: this.pending.key }),
      );
      this.pending = undefined;
      this.notice.set("Saved");
      this.revision.update((n) => n + 1);
      return result;
    } catch (error) {
      this.error.set(errorMessage(error));
      return undefined;
    } finally {
      this.saving.set(false);
    }
  }
}
