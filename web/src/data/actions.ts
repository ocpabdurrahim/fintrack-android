// Derived numbers and the multi-step edits, with the same rules as the Android
// app's FinRepository so both apps behave alike.
import { budgetId, newId, occurrenceId } from './ids'
import { store, type DataSnapshot } from './store'
import type { Account, Budget, Category, Recurring, Txn } from './types'
import { occurrence, type Frequency } from '../domain/recurrence'

export function byId<T extends { id: string }>(list: T[]): Map<string, T> {
  return new Map(list.map(x => [x.id, x]))
}

/** Opening balance plus every transaction's effect, per account id. */
export function accountBalances(accounts: Account[], txns: Txn[]): Map<string, number> {
  const balance = new Map(accounts.map(a => [a.id, a.openingBalance]))
  const add = (id: string | null, v: number) => {
    if (id !== null && balance.has(id)) balance.set(id, balance.get(id)! + v)
  }
  for (const t of txns) {
    if (t.type === 'INCOME') add(t.accountId, t.amount)
    else add(t.accountId, -t.amount)
    if (t.type === 'TRANSFER') add(t.toAccountId, t.amount)
  }
  return balance
}

export function saveTransaction(txn: Txn, repeat: Frequency | null) {
  if (!repeat) {
    store.put('transactions', txn)
    return
  }
  const schedule: Recurring = {
    id: newId(), type: txn.type, amount: txn.amount, categoryId: txn.categoryId, accountId: txn.accountId,
    toAccountId: txn.toAccountId, note: txn.note, frequency: repeat, startDate: txn.date, generated: 1, active: true,
  }
  // This transaction is occurrence 0 of the new schedule.
  store.putMany([['recurring', schedule], ['transactions', { ...txn, id: occurrenceId(schedule.id, 0), recurringId: schedule.id }]])
  store.processRecurring()
}

export type RemoveResult = 'deleted' | 'archived' | 'refused'

/** Unused accounts are deleted; ones with history are archived so old records stay intact. */
export function removeAccount(data: DataSnapshot, account: Account): RemoveResult {
  const active = data.accounts.filter(a => !a.archived).length
  if (!account.archived && active <= 1) return 'refused'
  const used = data.transactions.some(t => t.accountId === account.id || t.toAccountId === account.id) ||
    data.recurring.some(r => r.accountId === account.id || r.toAccountId === account.id)
  if (used) {
    store.put('accounts', { ...account, archived: true })
    return 'archived'
  }
  store.remove('accounts', account.id)
  return 'deleted'
}

export function removeCategory(data: DataSnapshot, category: Category): RemoveResult {
  const activeOfType = data.categories.filter(c => !c.archived && c.type === category.type).length
  if (!category.archived && activeOfType <= 1) return 'refused'
  if (data.budgets.some(b => b.categoryId === category.id)) store.remove('budgets', budgetId(category.id))
  const used = data.transactions.some(t => t.categoryId === category.id) || data.recurring.some(r => r.categoryId === category.id)
  if (used) {
    store.put('categories', { ...category, archived: true })
    return 'archived'
  }
  store.remove('categories', category.id)
  return 'deleted'
}

export function nextSortOrder(list: { sortOrder: number }[]): number {
  return list.reduce((max, x) => Math.max(max, x.sortOrder), 0) + 1
}

export function setBudget(categoryId: string, amount: number) {
  const budget: Budget = { id: budgetId(categoryId), categoryId, amount }
  store.put('budgets', budget)
}

export function removeBudget(categoryId: string) {
  store.remove('budgets', budgetId(categoryId))
}

/** Pausing stops new entries; resuming skips what was missed instead of back-filling it. */
export function setRecurringActive(r: Recurring, active: boolean, now = Date.now()) {
  let generated = r.generated
  if (active) {
    while (occurrence(r.startDate, r.frequency, generated) <= now && generated - r.generated < 5000) generated++
  }
  store.put('recurring', { ...r, active, generated })
}
