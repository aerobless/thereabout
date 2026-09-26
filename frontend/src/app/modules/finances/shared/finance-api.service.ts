import { inject, Injectable } from "@angular/core";
import {
  FinanceAccountQuery,
  FinanceTransactionQuery,
  FinancesService,
} from "../../../../../generated/backend-api/thereabout";
@Injectable()
export class FinanceApi {
  readonly client = inject(FinancesService);
  accounts(q: FinanceAccountQuery) {
    return this.client.financeListAccounts(
      q.page,
      q.pageSize,
      q.scope,
      q.q,
      q.kind,
      q.id,
      q.asOf,
      q.includeDeleted,
      q.includeInactive,
    );
  }
  transactions(q: FinanceTransactionQuery) {
    return this.client.financeListTransactions(
      q.page,
      q.pageSize,
      q.q,
      q.accountId,
      q.categoryId,
      q.type,
      q.effect,
      q.from,
      q.to,
      q.includeDeleted,
      q.operatingOnly,
    );
  }
}
