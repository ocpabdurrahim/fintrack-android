// Port of the Android app's domain/Stats.kt.
import type { Txn, TxnType } from '../data/types'
import { currentMonth, daysInMonth, monthOf, parseYm, type YearMonth } from './dates'

export interface Totals {
  income: number
  expense: number
  count: number
}

/** Transfers move money between accounts but are neither income nor expense. */
export function totalsOf(list: Txn[]): Totals {
  let income = 0
  let expense = 0
  for (const t of list) {
    if (t.type === 'INCOME') income += t.amount
    else if (t.type === 'EXPENSE') expense += t.amount
  }
  return { income, expense, count: list.length }
}

export interface CategoryShare {
  categoryId: string | null
  amount: number
  fraction: number
}

/** Per-category totals for one type, largest first, with each one's share of the whole. */
export function byCategory(list: Txn[], type: TxnType): CategoryShare[] {
  const sums = new Map<string | null, number>()
  let total = 0
  for (const t of list) {
    if (t.type !== type) continue
    sums.set(t.categoryId, (sums.get(t.categoryId) ?? 0) + t.amount)
    total += t.amount
  }
  if (total <= 0) return []
  return [...sums.entries()]
    .map(([categoryId, amount]) => ({ categoryId, amount, fraction: amount / total }))
    .sort((a, b) => b.amount - a.amount)
}

/** One total per day of the month (index 0 is the 1st). */
export function dailyTotals(list: Txn[], month: YearMonth, type: TxnType): number[] {
  const days = new Array<number>(daysInMonth(month)).fill(0)
  for (const t of list) {
    if (t.type !== type || monthOf(t.date) !== month) continue
    days[new Date(t.date).getDate() - 1] += t.amount
  }
  return days
}

export function monthlyTotals(list: Txn[], months: YearMonth[]): Totals[] {
  const grouped = new Map<YearMonth, Txn[]>()
  for (const t of list) {
    const m = monthOf(t.date)
    const bucket = grouped.get(m)
    if (bucket) bucket.push(t)
    else grouped.set(m, [t])
  }
  return months.map(m => totalsOf(grouped.get(m) ?? []))
}

/** Days that count for an average: elapsed days this month, all days for a past month. */
export function daysCounted(month: YearMonth, now = new Date()): number {
  const current = currentMonth(now)
  if (month === current) return now.getDate()
  const a = parseYm(month)
  const b = parseYm(current)
  return a.year * 12 + a.month < b.year * 12 + b.month ? daysInMonth(month) : 0
}

/** Rounded to whole units (taka, dollars): an average does not need paisa. */
export function dailyAverage(total: number, month: YearMonth, now = new Date()): number {
  const days = daysCounted(month, now)
  return days === 0 ? 0 : Math.floor((Math.floor(total / days) + 50) / 100) * 100
}
