import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { cssColor } from '../data/defaults'
import { useData } from '../data/store'
import type { Account, Category, Txn, TxnType } from '../data/types'
import { currentMonth, monthLabel, shortDate, timeLabel, type YearMonth, addMonths } from '../domain/dates'
import { compactMoney, formatMoney } from '../domain/money'
import { Icon } from './icons'

// ---- Money formatting from the synced currency settings ---------------------

export function useMoney() {
  const { settings } = useData()
  return useMemo(() => ({
    symbol: settings.currencySymbol,
    format: (minor: number, showPlus = false) => formatMoney(minor, settings.currencySymbol, settings.grouping, showPlus),
    compact: (minor: number) => compactMoney(minor, settings.currencySymbol, settings.grouping),
  }), [settings])
}

export const typeClass = (type: TxnType) => (type === 'INCOME' ? 'income' : type === 'EXPENSE' ? 'expense' : 'transfer')

/** Green while comfortable, amber past 80%, red once over. */
export const budgetColor = (fraction: number) =>
  fraction >= 1 ? 'var(--expense)' : fraction >= 0.8 ? 'var(--warning)' : 'var(--income)'

// ---- The add/edit dialog is opened from anywhere ----------------------------

export interface EditorRequest {
  id?: string
  type?: TxnType
}

export const EditorContext = createContext<(request: EditorRequest) => void>(() => {})
export const useEditor = () => useContext(EditorContext)

// ---- Toasts -------------------------------------------------------------------

const ToastContext = createContext<(message: string) => void>(() => {})
export const useToast = () => useContext(ToastContext)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<{ id: number; message: string }[]>([])
  const show = useCallback((message: string) => {
    const id = Date.now() + Math.random()
    setToasts(t => [...t, { id, message }])
    setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 3500)
  }, [])
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toasts" role="status">
        {toasts.map(t => <div key={t.id} className="toast">{t.message}</div>)}
      </div>
    </ToastContext.Provider>
  )
}

// ---- Small building blocks ------------------------------------------------------

export function EmojiBadge({ emoji, color, size = 40 }: { emoji: string; color: string; size?: number }) {
  return (
    <span className="badge" style={{ width: size, height: size, fontSize: size * 0.45, background: color }}>
      {emoji}
    </span>
  )
}

export function categoryBadgeColor(category: Category | undefined, alpha = 0.22) {
  return category ? cssColor(category.color, alpha) : 'var(--chip)'
}

export function ProgressBar({ fraction, color, height = 8 }: { fraction: number; color: string; height?: number }) {
  const width = Math.max(0, Math.min(1, fraction)) * 100
  return (
    <div className="progress" style={{ height }}>
      <div style={{ width: `${width}%`, background: color }} />
    </div>
  )
}

export function Empty({ emoji, title, body }: { emoji: string; title: string; body: string }) {
  return (
    <div className="empty">
      <div className="e">{emoji}</div>
      <h3>{title}</h3>
      <p>{body}</p>
    </div>
  )
}

export function Tile({ label, value, className }: { label: string; value: string; className?: string }) {
  return (
    <div className="tile">
      <div className="k">{label}</div>
      <div className={`v num ${className ?? ''}`}>{value}</div>
    </div>
  )
}

export function MonthSelector({ month, onChange }: { month: YearMonth; onChange: (m: YearMonth) => void }) {
  const now = currentMonth()
  return (
    <div className="month-selector">
      <button className="icon-btn" onClick={() => onChange(addMonths(month, -1))} aria-label="Previous month"><Icon name="left" /></button>
      <div className="label">
        <b>{monthLabel(month)}</b>
        {month !== now && <button onClick={() => onChange(now)}>Back to this month</button>}
      </div>
      <button className="icon-btn" onClick={() => onChange(addMonths(month, 1))} aria-label="Next month"><Icon name="right" /></button>
    </div>
  )
}

export function Segmented<T extends string>({ options, value, onChange }: {
  options: [T, string][]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map(([v, label]) => (
        <button key={v} role="tab" aria-selected={v === value} className={v === value ? 'on' : ''} onClick={() => onChange(v)}>
          {v === value && '✓ '}{label}
        </button>
      ))}
    </div>
  )
}

export function TxnRow({ txn, categories, accounts, showDate = false, onOpen }: {
  txn: Txn
  categories: Map<string, Category>
  accounts: Map<string, Account>
  showDate?: boolean
  onOpen: (id: string) => void
}) {
  const money = useMoney()
  const transfer = txn.type === 'TRANSFER'
  const category = txn.categoryId ? categories.get(txn.categoryId) : undefined
  const account = accounts.get(txn.accountId)
  const title = transfer ? 'Transfer' : category?.name ?? 'Uncategorised'
  const subtitle = transfer
    ? `${account?.name ?? '?'} → ${(txn.toAccountId && accounts.get(txn.toAccountId)?.name) || '?'}${txn.note ? ` · ${txn.note}` : ''}`
    : [txn.note, account?.name].filter(Boolean).join(' · ')
  const sign = txn.type === 'INCOME' ? '+' : txn.type === 'EXPENSE' ? '-' : ''
  return (
    <button className="txn" onClick={() => onOpen(txn.id)}>
      <EmojiBadge
        emoji={transfer ? '🔁' : category?.emoji ?? '❔'}
        color={transfer ? 'color-mix(in srgb, var(--transfer) 22%, transparent)' : categoryBadgeColor(category)}
      />
      <div className="grow">
        <div className="title">{title}</div>
        {subtitle && <div className="subtitle">{subtitle}</div>}
      </div>
      <div className="amt">
        <div className={`v num ${typeClass(txn.type)}`}>{sign}{money.format(txn.amount)}</div>
        <div className="tiny muted">{showDate ? shortDate(txn.date) : timeLabel(txn.date)}</div>
      </div>
    </button>
  )
}

// ---- Dialogs ----------------------------------------------------------------------

export function Modal({ children, onClose, narrow = false, sheet = false, label }: {
  children: ReactNode
  onClose: () => void
  narrow?: boolean
  /** Full-screen on phones. */
  sheet?: boolean
  label: string
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className={`backdrop ${sheet ? 'sheet' : ''}`} onMouseDown={e => e.target === e.currentTarget && onClose()}>
      <div className={`dialog ${narrow ? 'narrow' : ''}`} role="dialog" aria-modal="true" aria-label={label}>
        {children}
      </div>
    </div>
  )
}

export function ConfirmDialog({ title, text, confirm, danger = false, onConfirm, onClose }: {
  title: string
  text: string
  confirm: string
  danger?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Modal onClose={onClose} narrow label={title}>
      <h2>{title}</h2>
      <p className="muted">{text}</p>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className={`btn ${danger ? 'danger' : ''}`} onClick={() => { onConfirm(); onClose() }}>{confirm}</button>
      </div>
    </Modal>
  )
}

export function Spinner() {
  return <div className="center-screen"><div className="spinner" aria-label="Loading" /></div>
}
