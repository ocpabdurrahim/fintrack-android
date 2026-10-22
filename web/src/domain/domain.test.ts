// Mirrors the Android unit tests (MoneyTest, StatsTest) so both apps agree.
import { describe, expect, it } from 'vitest'
import { amountToInput, compactMoney, formatMoney, parseAmount, sanitizeAmount } from './money'
import { occurrence } from './recurrence'
import { byCategory, dailyAverage, dailyTotals, totalsOf } from './stats'
import { newId, occurrenceId } from '../data/ids'
import { cssColor, DEFAULT_CATEGORIES } from '../data/defaults'
import type { Txn, TxnType } from '../data/types'

describe('money', () => {
  it('parses whole and decimal amounts', () => {
    expect(parseAmount('1250')).toBe(125000)
    expect(parseAmount('1,250.5')).toBe(125050)
    expect(parseAmount('1250.05')).toBe(125005)
    expect(parseAmount('.5')).toBe(50)
    expect(parseAmount(' 1. ')).toBe(100)
    expect(parseAmount('-25', true)).toBe(-2500)
  })

  it('rejects invalid amounts', () => {
    for (const bad of ['', '.', '12.345', 'abc', '-5', '1.2.3']) expect(parseAmount(bad)).toBeNull()
  })

  it('sanitizes keystrokes', () => {
    expect(sanitizeAmount('12.3')).toBe('12.3')
    expect(sanitizeAmount('1,2')).toBe('12')
    expect(sanitizeAmount('12.345')).toBeNull()
    expect(sanitizeAmount('-4', true)).toBe('-4')
  })

  it('round-trips edit text', () => {
    expect(amountToInput(125050)).toBe('1250.50')
    expect(amountToInput(125000)).toBe('1250')
    expect(amountToInput(-305)).toBe('-3.05')
  })

  it('formats with South Asian and international grouping', () => {
    expect(formatMoney(125000, '৳', 'SOUTH_ASIAN')).toBe('৳1,250')
    expect(formatMoney(123456750, '৳', 'SOUTH_ASIAN')).toBe('৳12,34,567.50')
    expect(formatMoney(1_000_000_000, '৳', 'SOUTH_ASIAN')).toBe('৳1,00,00,000')
    expect(formatMoney(123456750, '$', 'INTERNATIONAL')).toBe('$1,234,567.50')
    expect(formatMoney(0, '৳', 'SOUTH_ASIAN', true)).toBe('৳0')
    expect(formatMoney(-25000, '৳', 'SOUTH_ASIAN')).toBe('-৳250')
    expect(formatMoney(25000, '৳', 'SOUTH_ASIAN', true)).toBe('+৳250')
    expect(formatMoney(50000, 'Rs', 'SOUTH_ASIAN')).toBe('Rs 500')
    expect(formatMoney(-150000, 'SAR', 'INTERNATIONAL')).toBe('-SAR 1,500')
  })

  it('makes compact chart labels', () => {
    expect(compactMoney(95000, '৳', 'SOUTH_ASIAN')).toBe('৳950')
    expect(compactMoney(1_250_000, '৳', 'SOUTH_ASIAN')).toBe('৳12.5K')
    expect(compactMoney(15_000_000, '৳', 'SOUTH_ASIAN')).toBe('৳1.5L')
    expect(compactMoney(2_000_000_000, '৳', 'SOUTH_ASIAN')).toBe('৳2Cr')
    expect(compactMoney(200_000_000, '$', 'INTERNATIONAL')).toBe('$2M')
  })
})

const at = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime()
const txn = (type: TxnType, amount: number, category: string | null, date: number): Txn => ({
  id: newId(), type, amount, categoryId: category, accountId: 'a1', toAccountId: type === 'TRANSFER' ? 'a2' : null,
  date, note: '', recurringId: null, createdAt: 0,
})

describe('stats', () => {
  it('ignores transfers in totals', () => {
    const t = totalsOf([txn('INCOME', 1000, 'c', at(2026, 10, 1)), txn('EXPENSE', 300, 'c', at(2026, 10, 2)), txn('TRANSFER', 5000, null, at(2026, 10, 3))])
    expect(t).toEqual({ income: 1000, expense: 300, count: 3 })
  })

  it('shares categories largest first', () => {
    const s = byCategory([
      txn('EXPENSE', 100, 'c1', at(2026, 10, 1)), txn('EXPENSE', 300, 'c2', at(2026, 10, 1)),
      txn('EXPENSE', 100, 'c1', at(2026, 10, 2)), txn('INCOME', 9999, 'c3', at(2026, 10, 2)),
    ], 'EXPENSE')
    expect(s.map(x => x.categoryId)).toEqual(['c2', 'c1'])
    expect(s[0].fraction).toBeCloseTo(0.6)
  })

  it('sums each day of the month', () => {
    const days = dailyTotals([
      txn('EXPENSE', 100, 'c', at(2026, 2, 1)), txn('EXPENSE', 50, 'c', at(2026, 2, 1, 20)),
      txn('EXPENSE', 70, 'c', at(2026, 2, 28)), txn('EXPENSE', 999, 'c', at(2026, 3, 1)),
    ], '2026-02', 'EXPENSE')
    expect(days.length).toBe(28)
    expect([days[0], days[27]]).toEqual([150, 70])
  })

  it('averages over elapsed days, rounded to whole units', () => {
    const now = new Date(2026, 9, 10, 15)
    expect(dailyAverage(1000, '2026-10', now)).toBe(100)
    expect(dailyAverage(3000, '2026-09', now)).toBe(100)
    expect(dailyAverage(3000, '2026-11', now)).toBe(0)
    expect(dailyAverage(243595 * 10, '2026-10', now)).toBe(243600)
  })
})

describe('recurrence', () => {
  const day = (ms: number) => {
    const d = new Date(ms)
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
  }

  it('counts months from the start without drifting', () => {
    const start = at(2026, 1, 31)
    expect([0, 1, 2, 3].map(n => day(occurrence(start, 'MONTHLY', n)))).toEqual(['2026-1-31', '2026-2-28', '2026-3-31', '2026-4-30'])
  })

  it('handles the other frequencies and leap days', () => {
    const start = at(2024, 2, 29)
    expect(day(occurrence(start, 'DAILY', 1))).toBe('2024-3-1')
    expect(day(occurrence(start, 'WEEKLY', 2))).toBe('2024-3-14')
    expect(day(occurrence(start, 'YEARLY', 1))).toBe('2025-2-28')
    expect(day(occurrence(start, 'YEARLY', 4))).toBe('2028-2-29')
    expect(new Date(occurrence(start, 'MONTHLY', 1)).getHours()).toBe(12)
  })
})

describe('ids and defaults', () => {
  it('makes 20-character ids and fixed occurrence ids', () => {
    expect(newId()).toMatch(/^[A-Za-z0-9]{20}$/)
    expect(occurrenceId('abc', 3)).toBe('abc_3')
  })

  it('matches the Android starter data', () => {
    expect(DEFAULT_CATEGORIES.length).toBe(22)
    const food = DEFAULT_CATEGORIES.find(c => c.id === 'cat_food')!
    expect(food.color).toBe(0xffffb74d | 0)
    expect(cssColor(food.color)).toBe('rgb(255, 183, 77)')
  })
})
