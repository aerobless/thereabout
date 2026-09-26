import { EMPTY, expand, reduce } from "rxjs";
import { inject, Injectable } from "@angular/core";
import {
  FinanceAccountQuery,
  FinanceAccountPage,
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
      q.active,
    );
  }
  allOwnAccounts(q: FinanceAccountQuery = {}) {
    const request = { ...q, scope: "OWN" as const, pageSize: 200 };
    return this.accounts({ ...request, page: 0 }).pipe(
      expand((page) =>
        (page.page + 1) * page.pageSize < page.total
          ? this.accounts({ ...request, page: page.page + 1 })
          : EMPTY,
      ),
      reduce<FinanceAccountPage, FinanceAccountPage>(
        (all, page) => ({
          ...page,
          items: [...all.items, ...page.items],
          page: 0,
        }),
        {
          items: [],
          total: 0,
          page: 0,
          pageSize: 200,
        },
      ),
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
      q.categoryIds,
      q.sort,
    );
  }
}
