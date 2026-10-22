// Starter accounts and categories — the same fixed ids as the Android app's
// data/DefaultData.kt, so a phone and a browser seeding on their own end up
// with one set of records. Keep the two lists in step.
import type { Account, Category } from './types'

const argb = (hex: number) => hex | 0

export const PALETTE: number[] = [
  0xffe57373, 0xfff06292, 0xffba68c8, 0xff9575cd, 0xff7986cb, 0xff64b5f6, 0xff4fc3f7, 0xff4dd0e1,
  0xff4db6ac, 0xff81c784, 0xffaed581, 0xffffd54f, 0xffffb74d, 0xffff8a65, 0xffa1887f, 0xff90a4ae,
].map(argb)

export const CATEGORY_EMOJIS = [
  '🍔', '🍕', '☕', '🛒', '🍎', '🚌', '🚗', '⛽', '🚕', '🏠',
  '💡', '💧', '🔥', '📱', '📶', '🛍️', '👕', '👟', '💊', '🏥',
  '📚', '🎓', '🎬', '🎮', '🎵', '✈️', '🏖️', '🎁', '💝', '👪',
  '👶', '🐾', '💇', '💄', '⚽', '🔧', '💳', '🏦', '📦', '🕌',
  '🙏', '💼', '💻', '🏪', '📈', '💰', '🏘️', '🎉', '🏷️', '❔',
]

export const ACCOUNT_EMOJIS = ['💵', '🏦', '📱', '💳', '👛', '💰', '🏧', '📈', '🐷', '🏠', '💼', '🌐']

export const DEFAULT_ACCOUNTS: Account[] = [
  { id: 'acc_cash', name: 'Cash', emoji: '💵', openingBalance: 0, archived: false, sortOrder: 1 },
  { id: 'acc_bank', name: 'Bank', emoji: '🏦', openingBalance: 0, archived: false, sortOrder: 2 },
  { id: 'acc_wallet', name: 'Mobile Wallet', emoji: '📱', openingBalance: 0, archived: false, sortOrder: 3 },
]

const expense: [string, string, string, number][] = [
  ['cat_food', 'Food', '🍔', 0xffffb74d],
  ['cat_groceries', 'Groceries', '🛒', 0xff81c784],
  ['cat_transport', 'Transport', '🚌', 0xff64b5f6],
  ['cat_rent', 'Rent', '🏠', 0xffa1887f],
  ['cat_bills', 'Bills & Utilities', '💡', 0xffffd54f],
  ['cat_mobile', 'Mobile & Internet', '📶', 0xff4dd0e1],
  ['cat_shopping', 'Shopping', '🛍️', 0xfff06292],
  ['cat_health', 'Health', '💊', 0xffe57373],
  ['cat_education', 'Education', '📚', 0xff7986cb],
  ['cat_entertainment', 'Entertainment', '🎬', 0xffba68c8],
  ['cat_family', 'Family', '👪', 0xff4db6ac],
  ['cat_gifts', 'Gifts & Charity', '🎁', 0xff9575cd],
  ['cat_travel', 'Travel', '✈️', 0xff4fc3f7],
  ['cat_personal', 'Personal Care', '💇', 0xffff8a65],
  ['cat_other', 'Other', '📦', 0xff90a4ae],
]

const income: [string, string, string, number][] = [
  ['cat_salary', 'Salary', '💼', 0xff66bb6a],
  ['cat_business', 'Business', '🏪', 0xff26a69a],
  ['cat_freelance', 'Freelance', '💻', 0xff5c6bc0],
  ['cat_profit', 'Profit & Interest', '📈', 0xff42a5f5],
  ['cat_rent_income', 'Rent Received', '🏘️', 0xff8d6e63],
  ['cat_gifts_income', 'Gifts Received', '🎁', 0xffab47bc],
  ['cat_other_income', 'Other Income', '💰', 0xffffca28],
]

export const DEFAULT_CATEGORIES: Category[] = [
  ...expense.map(([id, name, emoji, color], i): Category => ({
    id, name, emoji, type: 'EXPENSE', color: argb(color), archived: false, sortOrder: i + 1,
  })),
  ...income.map(([id, name, emoji, color], i): Category => ({
    id, name, emoji, type: 'INCOME', color: argb(color), archived: false, sortOrder: i + 1,
  })),
]

/** Android ARGB int -> CSS colour. */
export function cssColor(color: number, alpha = 1): string {
  const rgb = color & 0xffffff
  const r = (rgb >> 16) & 0xff
  const g = (rgb >> 8) & 0xff
  const b = rgb & 0xff
  return alpha === 1 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${alpha})`
}
