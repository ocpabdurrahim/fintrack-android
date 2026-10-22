// File exports in the same formats as the Android app: a CSV for spreadsheets
// and a JSON backup (format version 2) that the phone's Restore accepts.
import type { DataSnapshot } from './store'
import { byId } from './actions'

function csvEscape(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

function plainAmount(minor: number): string {
  const sign = minor < 0 ? '-' : ''
  const a = Math.abs(minor)
  return `${sign}${Math.floor(a / 100)}.${String(a % 100).padStart(2, '0')}`
}

const pad = (n: number) => String(n).padStart(2, '0')

export function buildCsv(data: DataSnapshot): string {
  const accounts = byId(data.accounts)
  const categories = byId(data.categories)
  const rows = [...data.transactions].sort((a, b) => a.date - b.date || a.createdAt - b.createdAt).map(t => {
    const d = new Date(t.date)
    return [
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`,
      t.type.charAt(0) + t.type.slice(1).toLowerCase(),
      (t.categoryId && categories.get(t.categoryId)?.name) || '',
      accounts.get(t.accountId)?.name ?? '',
      (t.toAccountId && accounts.get(t.toAccountId)?.name) || '',
      plainAmount(t.amount),
      t.note,
    ].map(csvEscape).join(',')
  })
  // The byte-order mark makes Excel read the file as UTF-8, so Bangla notes survive.
  return '﻿' + ['Date,Type,Category,Account,To account,Amount,Note', ...rows].join('\r\n') + '\r\n'
}

export function buildBackup(data: DataSnapshot): string {
  return JSON.stringify({
    format: 'fintrack-backup',
    version: 2,
    exportedAt: Date.now(),
    ...data.settings,
    accounts: data.accounts,
    categories: data.categories,
    transactions: data.transactions,
    budgets: data.budgets.map(({ id, categoryId, amount }) => ({ id, categoryId, amount })),
    recurring: data.recurring,
  }, null, 2)
}

export function download(fileName: string, text: string, type: string) {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
