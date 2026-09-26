import { Injectable, signal } from "@angular/core";
import {
  FinanceAccount,
  FinanceTransaction,
} from "../../../../../generated/backend-api/thereabout";
export type FinanceDialog =
  | { kind: "transaction"; transaction?: FinanceTransaction }
  | { kind: "account"; account?: FinanceAccount; counterparty?: boolean }
  | { kind: "categories" }
  | { kind: "valuation"; account: FinanceAccount }
  | { kind: "rates" }
  | { kind: "deletion"; transaction: FinanceTransaction }
  | { kind: "history"; transaction: FinanceTransaction };
@Injectable()
export class FinanceDialogs {
  readonly selected = signal<FinanceDialog | null>(null);
  open(dialog: FinanceDialog) {
    this.selected.set(dialog);
  }
  close() {
    this.selected.set(null);
  }
}
