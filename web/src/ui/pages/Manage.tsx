import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { accountBalances, byId, nextSortOrder, removeAccount, removeCategory, setRecurringActive, type RemoveResult } from '../../data/actions'
import { cssColor } from '../../data/defaults'
import { store, useData } from '../../data/store'
import type { Account, Category, CategoryType, Recurring } from '../../data/types'
import { fullDate } from '../../domain/dates'
import { occurrence } from '../../domain/recurrence'
import { categoryBadgeColor, ConfirmDialog, EmojiBadge, Empty, Segmented, typeClass, useMoney, useToast } from '../common'
import { AccountDialog, CategoryDialog } from '../dialogs'
import { Icon } from '../icons'

function BackHead({ title, action }: { title: string; action?: React.ReactNode }) {
  const navigate = useNavigate()
  return (
    <div className="page-head">
      <button className="icon-btn" aria-label="Back" onClick={() => navigate('/more')}><Icon name="back" /></button>
      <h1>{title}</h1>
      {action}
    </div>
  )
}

function removedMessage(name: string, result: RemoveResult, kind: 'account' | 'category') {
  if (result === 'deleted') return `${name} deleted`
  if (result === 'archived') return `${name} has history, so it was archived instead`
  return kind === 'account' ? 'Keep at least one account' : 'Keep at least one category of each kind'
}

export function AccountsPage() {
  const data = useData()
  const money = useMoney()
  const toast = useToast()
  const [editing, setEditing] = useState<Account | 'new' | null>(null)
  const balances = useMemo(() => accountBalances(data.accounts, data.transactions), [data.accounts, data.transactions])
  const total = [...balances.values()].reduce((s, v) => s + v, 0)
  const active = data.accounts.filter(a => !a.archived)
  const archived = data.accounts.filter(a => a.archived)

  const save = (a: Account) => {
    const isNew = !data.accounts.some(x => x.id === a.id)
    store.put('accounts', isNew ? { ...a, sortOrder: nextSortOrder(data.accounts) } : a)
    setEditing(null)
  }

  return (
    <div className="page">
      <BackHead title="Accounts" action={<button className="btn" onClick={() => setEditing('new')}><Icon name="plus" size={18} />Add</button>} />
      <div className="stack">
        <div className="card pad" style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)', borderColor: 'transparent' }}>
          <div className="small">Total across accounts</div>
          <div className="bold num" style={{ fontSize: 28 }}>{money.format(total)}</div>
        </div>
        <div className="card" style={{ overflow: 'hidden' }}>
          {active.map(a => (
            <button key={a.id} className="item" onClick={() => setEditing(a)}>
              <EmojiBadge emoji={a.emoji} color="color-mix(in srgb, var(--primary) 20%, transparent)" />
              <div className="grow">
                <div className="title">{a.name}</div>
                {a.openingBalance !== 0 && <div className="small muted">Started with {money.format(a.openingBalance)}</div>}
              </div>
              <span className="bold num">{money.format(balances.get(a.id) ?? 0)}</span>
            </button>
          ))}
        </div>
        {archived.length > 0 && (
          <>
            <div className="section-title"><span>Archived</span></div>
            <div className="card" style={{ overflow: 'hidden' }}>
              {archived.map(a => (
                <div key={a.id} className="item">
                  <EmojiBadge emoji={a.emoji} color="var(--chip)" />
                  <div className="grow title">{a.name}</div>
                  <button className="btn text" onClick={() => store.put('accounts', { ...a, archived: false })}>Restore</button>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
      {editing && (
        <AccountDialog
          initial={editing === 'new' ? null : editing}
          onSave={save}
          onDelete={editing === 'new' ? undefined : () => {
            toast(removedMessage(editing.name, removeAccount(data, editing), 'account'))
            setEditing(null)
          }}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  )
}

export function CategoriesPage() {
  const data = useData()
  const toast = useToast()
  const [tab, setTab] = useState<CategoryType>('EXPENSE')
  const [editing, setEditing] = useState<Category | 'new' | null>(null)
  const ofType = data.categories.filter(c => c.type === tab)

  const save = (c: Category) => {
    const isNew = !data.categories.some(x => x.id === c.id)
    store.put('categories', isNew ? { ...c, sortOrder: nextSortOrder(ofType) } : c)
    setEditing(null)
  }

  return (
    <div className="page">
      <BackHead title="Categories" action={<button className="btn" onClick={() => setEditing('new')}><Icon name="plus" size={18} />Add</button>} />
      <div className="stack">
        <Segmented options={[['EXPENSE', 'Expense'], ['INCOME', 'Income']]} value={tab} onChange={setTab} />
        <div className="card" style={{ overflow: 'hidden' }}>
          {ofType.filter(c => !c.archived).map(c => (
            <button key={c.id} className="item" onClick={() => setEditing(c)}>
              <EmojiBadge emoji={c.emoji} color={cssColor(c.color, 0.25)} />
              <div className="grow title">{c.name}</div>
              <span className="badge" style={{ width: 12, height: 12, background: cssColor(c.color) }} />
            </button>
          ))}
        </div>
        {ofType.some(c => c.archived) && (
          <>
            <div className="section-title"><span>Archived</span></div>
            <div className="card" style={{ overflow: 'hidden' }}>
              {ofType.filter(c => c.archived).map(c => (
                <div key={c.id} className="item">
                  <EmojiBadge emoji={c.emoji} color="var(--chip)" />
                  <div className="grow title">{c.name}</div>
                  <button className="btn text" onClick={() => store.put('categories', { ...c, archived: false })}>Restore</button>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
      {editing && (
        <CategoryDialog
          initial={editing === 'new' ? null : editing}
          type={editing === 'new' ? tab : editing.type}
          onSave={save}
          onDelete={editing === 'new' ? undefined : () => {
            toast(removedMessage(editing.name, removeCategory(data, editing), 'category'))
            setEditing(null)
          }}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  )
}

const FREQ_LABEL = { DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' } as const

export function RecurringPage() {
  const data = useData()
  const money = useMoney()
  const [deleting, setDeleting] = useState<Recurring | null>(null)
  const categories = useMemo(() => byId(data.categories), [data.categories])
  const accounts = useMemo(() => byId(data.accounts), [data.accounts])

  return (
    <div className="page">
      <BackHead title="Recurring" />
      {data.recurring.length === 0 ? (
        <div className="card">
          <Empty emoji="🔁" title="Nothing repeating yet" body="When you add rent, salary or a subscription, pick Repeat → Monthly and FinTrack will enter it for you each time." />
        </div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          {data.recurring.map(r => {
            const transfer = r.type === 'TRANSFER'
            const c = r.categoryId ? categories.get(r.categoryId) : undefined
            const title = transfer
              ? `${accounts.get(r.accountId)?.name ?? '?'} → ${(r.toAccountId && accounts.get(r.toAccountId)?.name) || '?'}`
              : c?.name ?? 'Uncategorised'
            return (
              <div key={r.id} className="item">
                <EmojiBadge emoji={transfer ? '🔁' : c?.emoji ?? '❔'} color={transfer ? 'var(--chip)' : categoryBadgeColor(c)} />
                <div className="grow">
                  <div className="title">{title}</div>
                  <div className={`small num ${typeClass(r.type)}`}>{money.format(r.amount)} · {FREQ_LABEL[r.frequency]}</div>
                  <div className="small muted">{r.active ? `Next: ${fullDate(occurrence(r.startDate, r.frequency, r.generated))}` : 'Paused'}{r.note ? ` · ${r.note}` : ''}</div>
                </div>
                <label className="row small" style={{ gap: 6 }}>
                  <input type="checkbox" checked={r.active} onChange={e => setRecurringActive(r, e.target.checked)} />
                  {r.active ? 'On' : 'Paused'}
                </label>
                <button className="icon-btn" aria-label="Stop repeating" onClick={() => setDeleting(r)}><Icon name="trash" size={20} /></button>
              </div>
            )
          })}
        </div>
      )}
      {deleting && (
        <ConfirmDialog
          title="Stop repeating?" confirm="Stop" danger
          text="FinTrack will stop adding this entry. The ones it already added stay in your history."
          onConfirm={() => store.remove('recurring', deleting.id)}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  )
}
