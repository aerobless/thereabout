import { Injectable, signal } from "@angular/core";
import {
  FinanceAccount,
  FinanceTransaction,
} from "../../../../../generated/backend-api/thereabout";
export type FinanceDialog =
  | { kind: "transaction"; transaction?: FinanceTransaction }
  | { kind: "account"; account?: FinanceAccount; counterparty?: boolean; userId?: number }
  | { kind: "import"; accountId?: number }
  | { kind: "hints"; accountId: number }
  | { kind: "categories" }
  | { kind: "valuation"; account: FinanceAccount }
  | { kind: "rates" }
  | { kind: "deletion"; transaction: FinanceTransaction }
  | { kind: "history"; transaction: FinanceTransaction };
@Injectable()
export class FinanceDialogs {
  readonly blocked = signal(false);
  readonly hintAccountId = signal<number | null>(null);
  readonly hintsBlocked = signal(false);
  // Keep the import component mounted while its account hints are open.
  openImportHints(accountId: number) { this.hintAccountId.set(accountId); }
  closeImportHints() {
    if (!this.hintsBlocked()) this.hintAccountId.set(null);
  }
  readonly selected = signal<FinanceDialog | null>(null);
  open(dialog: FinanceDialog) {
    this.selected.set(dialog);
  }
  close() {
    this.hintAccountId.set(null);
    this.hintsBlocked.set(false);
    this.selected.set(null);
  }
}
