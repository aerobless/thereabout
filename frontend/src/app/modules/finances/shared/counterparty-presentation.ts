import {FinanceAccount} from '../../../../../generated/backend-api/thereabout';

export function counterpartyAccountKinds(accounts: FinanceAccount[]): string[] {
  return [...new Set(accounts.map(account => `${account.kind === 'EXPENSE' ? 'Expense' : 'Revenue'} · ${account.currency}`))];
}
