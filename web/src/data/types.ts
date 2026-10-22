// The shapes stored in Firestore under users/{uid}/... — the same fields the
// Android app's sync engine reads and writes (see docs/sync-protocol.md).
import type { Grouping } from '../domain/money'
import type { Frequency } from '../domain/recurrence'

export type TxnType = 'INCOME' | 'EXPENSE' | 'TRANSFER'
export type CategoryType = 'INCOME' | 'EXPENSE'

export interface Account {
  id: string
  name: string
  emoji: string
  openingBalance: number
  archived: boolean
  sortOrder: number
}

export interface Category {
  id: string
  name: string
  type: CategoryType
  emoji: string
  /** Signed 32-bit ARGB, as Android stores colours. */
  color: number
  archived: boolean
  sortOrder: number
}

export interface Txn {
  id: string
  type: TxnType
  amount: number
  categoryId: string | null
  /** For a transfer, the account the money leaves. */
  accountId: string
  /** Only for transfers: the account the money arrives in. */
  toAccountId: string | null
  date: number
  note: string
  recurringId: string | null
  createdAt: number
}

export interface Budget {
  id: string
  /** A category id, or OVERALL. */
  categoryId: string
  amount: number
}

export interface Recurring {
  id: string
  type: TxnType
  amount: number
  categoryId: string | null
  accountId: string
  toAccountId: string | null
  note: string
  frequency: Frequency
  startDate: number
  generated: number
  active: boolean
}

export interface CurrencySettings {
  currencyCode: string
  currencySymbol: string
  grouping: Grouping
}

export const OVERALL = 'overall'

export const DEFAULT_CURRENCY: CurrencySettings = { currencyCode: 'BDT', currencySymbol: '৳', grouping: 'SOUTH_ASIAN' }

export interface Collections {
  accounts: Account
  categories: Category
  transactions: Txn
  budgets: Budget
  recurring: Recurring
}

export type CollectionName = keyof Collections

export const COLLECTIONS: CollectionName[] = ['accounts', 'categories', 'transactions', 'budgets', 'recurring']
