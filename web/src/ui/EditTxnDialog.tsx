import { useMemo, useState } from 'react'
import { accountBalances, nextSortOrder, saveTransaction } from '../data/actions'
import { cssColor } from '../data/defaults'
import { newId } from '../data/ids'
import { store, useData } from '../data/store'
import type { Category, Txn, TxnType } from '../data/types'
import { fromDateInput, toDateInput, startOfDay } from '../domain/dates'
import { amountToInput, parseAmount, sanitizeAmount } from '../domain/money'
import type { Frequency } from '../domain/recurrence'
import { ConfirmDialog, EmojiBadge, Modal, Segmented, typeClass, useMoney, type EditorRequest } from './common'
import { CategoryDialog } from './dialogs'
import { Icon } from './icons'

const FREQUENCIES: [Frequency, string][] = [['DAILY', 'Daily'], ['WEEKLY', 'Weekly'], ['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']]
const EVERY: Record<Frequency, string> = { DAILY: 'day', WEEKLY: 'week', MONTHLY: 'month', YEARLY: 'year' }

export function EditTxnDialog({ request, onClose }: { request: EditorRequest; onClose: () => void }) {
  const data = useData()
  const money = useMoney()
  const original = request.id ? data.transactions.find(t => t.id === request.id) : undefined
  const activeAccounts = data.accounts.filter(a => !a.archived)

  const [type, setType] = useState<TxnType>(original?.type ?? request.type ?? 'EXPENSE')
  const [amount, setAmount] = useState(original ? amountToInput(original.amount) : '')
  const [categoryId, setCategoryId] = useState<string | null>(original?.categoryId ?? null)
  const [accountId, setAccountId] = useState<string | null>(original?.accountId ?? activeAccounts[0]?.id ?? null)
  const [toAccountId, setToAccountId] = useState<string | null>(original?.toAccountId ?? activeAccounts[1]?.id ?? null)
  const [date, setDate] = useState(original?.date ?? Date.now())
  const [note, setNote] = useState(original?.note ?? '')
  const [repeat, setRepeat] = useState<Frequency | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [newCategory, setNewCategory] = useState(false)

  const accounts = data.accounts.filter(a => !a.archived || a.id === accountId || a.id === toAccountId)
  const categories = useMemo(
    () => data.categories.filter(c => c.type === type && (!c.archived || c.id === categoryId)),
    [data.categories, type, categoryId],
  )
  const today = startOfDay(Date.now())

  const changeType = (t: TxnType) => {
    if (t === type) return
    setType(t)
    setCategoryId(null) // categories belong to one type
    setError(null)
  }

  const save = () => {
    const value = parseAmount(amount)
    const transfer = type === 'TRANSFER'
    const problem =
      value === null || value <= 0 ? 'Enter an amount above zero'
        : !accountId ? 'Choose an account'
          : !transfer && !categoryId ? 'Choose a category'
            : transfer && (!toAccountId || toAccountId === accountId) ? 'Pick two different accounts'
              : null
    setError(problem)
    if (problem || value === null || !accountId) return
    const txn: Txn = {
      id: original?.id ?? newId(),
      type,
      amount: value,
      categoryId: transfer ? null : categoryId,
      accountId,
      toAccountId: transfer ? toAccountId : null,
      date,
      note: note.trim(),
      recurringId: original?.recurringId ?? null,
      createdAt: original?.createdAt ?? Date.now(),
    }
    saveTransaction(txn, original ? null : repeat)
    onClose()
  }

  const addCategory = (c: Category) => {
    store.put('categories', { ...c, sortOrder: nextSortOrder(data.categories.filter(x => x.type === c.type)) })
    setCategoryId(c.id)
    setNewCategory(false)
  }

  const balances = useMemo(() => accountBalances(data.accounts, data.transactions), [data.accounts, data.transactions])

  // Accounts are picked from drop-down lists that show each balance.
  const accountSelect = (label: string, value: string | null, onPick: (id: string) => void) => (
    <label className="field grow">
      <span>{label}</span>
      <select className="input" value={value ?? ''} onChange={e => { onPick(e.target.value); setError(null) }}>
        <option value="" disabled>Choose an account</option>
        {accounts.map(a => (
          <option key={a.id} value={a.id}>{a.emoji}  {a.name} · {money.format(balances.get(a.id) ?? 0)}</option>
        ))}
      </select>
    </label>
  )


  return (
    <Modal onClose={onClose} sheet label={original ? 'Edit transaction' : 'New transaction'}>
      <div className="row" style={{ marginBottom: 14 }}>
        <h2 className="grow" style={{ margin: 0 }}>{original ? 'Edit transaction' : 'New transaction'}</h2>
        {original && (
          <button className="icon-btn" aria-label="Delete" onClick={() => setConfirmDelete(true)}><Icon name="trash" /></button>
        )}
        <button className="icon-btn" aria-label="Close" onClick={onClose}><Icon name="close" /></button>
      </div>

      <div className="stack">
        <Segmented options={[['EXPENSE', 'Expense'], ['INCOME', 'Income'], ['TRANSFER', 'Transfer']]} value={type} onChange={changeType} />

        <div className={`amount-input ${typeClass(type)}`}>
          <span className="sym">{money.symbol}</span>
          <input
            aria-label="Amount" inputMode="decimal" placeholder="0" autoFocus={!original} value={amount}
            className={typeClass(type)}
            onChange={e => { const v = sanitizeAmount(e.target.value); if (v !== null) { setAmount(v); setError(null) } }}
            onKeyDown={e => e.key === 'Enter' && save()}
          />
        </div>

        {type === 'TRANSFER' ? (
          <div className="transfer-row">
            {accountSelect('From', accountId, setAccountId)}
            <button type="button" className="icon-btn swap" aria-label="Swap From and To" title="Swap"
              onClick={() => { setAccountId(toAccountId); setToAccountId(accountId) }}>
              <Icon name="swap" />
            </button>
            {accountSelect('To', toAccountId, setToAccountId)}
          </div>
        ) : (
          <>
            <div className="field">
              <span>Category</span>
              <div className="cat-grid">
                {categories.map(c => {
                  const on = c.id === categoryId
                  return (
                    <button key={c.id} className={`cat-tile ${on ? 'on' : ''}`}
                      style={on ? { borderColor: cssColor(c.color), background: cssColor(c.color, 0.18) } : undefined}
                      onClick={() => { setCategoryId(c.id); setError(null) }}>
                      <EmojiBadge emoji={c.emoji} color={cssColor(c.color, 0.25)} size={36} />
                      {c.name}
                    </button>
                  )
                })}
                <button className="cat-tile" onClick={() => setNewCategory(true)}>
                  <EmojiBadge emoji="＋" color="var(--chip)" size={36} />New
                </button>
              </div>
            </div>
            {accountSelect(type === 'INCOME' ? 'Received in' : 'Paid from', accountId, setAccountId)}
          </>
        )}

        <div className="field">
          <span>Date</span>
          <div className="row wrap">
            <button className={`chip ${startOfDay(date) === today ? 'on' : ''}`} onClick={() => setDate(fromDateInput(toDateInput(Date.now()), date))}>Today</button>
            <button className={`chip ${startOfDay(date) === startOfDay(today - 1) ? 'on' : ''}`} onClick={() => setDate(fromDateInput(toDateInput(today - 1), date))}>Yesterday</button>
            <input type="date" className="input" style={{ width: 'auto' }} value={toDateInput(date)}
              onChange={e => e.target.value && setDate(fromDateInput(e.target.value, date))} />
          </div>
        </div>

        <label className="field">
          <span>Note</span>
          <input className="input" value={note} maxLength={200} placeholder="What was it for? (optional)" onChange={e => setNote(e.target.value)} />
        </label>

        {!original && (
          <div className="field">
            <span>Repeat</span>
            <div className="chips">
              <button className={`chip ${repeat === null ? 'on' : ''}`} onClick={() => setRepeat(null)}>Never</button>
              {FREQUENCIES.map(([f, label]) => (
                <button key={f} className={`chip ${repeat === f ? 'on' : ''}`} onClick={() => setRepeat(f)}>{label}</button>
              ))}
            </div>
            {repeat && <div className="small muted">FinTrack will add this again every {EVERY[repeat]} by itself, on your phone and here.</div>}
          </div>
        )}
      </div>

      <div style={{ position: 'sticky', bottom: -22, background: 'var(--card)', paddingTop: 14, marginTop: 8 }}>
        {error && <div className="error-text small" style={{ marginBottom: 8 }} role="alert">{error}</div>}
        <button className="btn big" onClick={save}>{original ? 'Save changes' : 'Save'}</button>
      </div>

      {confirmDelete && original && (
        <ConfirmDialog
          title="Delete this transaction?"
          text="It will be removed from your history and balances, here and on your phone. This can't be undone."
          confirm="Delete" danger
          onConfirm={() => { store.remove('transactions', original.id); onClose() }}
          onClose={() => setConfirmDelete(false)}
        />
      )}
      {newCategory && (
        <CategoryDialog initial={null} type={type === 'INCOME' ? 'INCOME' : 'EXPENSE'} onSave={addCategory} onClose={() => setNewCategory(false)} />
      )}
    </Modal>
  )
}
