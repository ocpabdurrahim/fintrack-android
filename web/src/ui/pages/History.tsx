import { useMemo, useState } from 'react'
import { byId } from '../../data/actions'
import { useData } from '../../data/store'
import type { Txn, TxnType } from '../../data/types'
import { currentMonth, dayHeader, monthEnd, monthLabel, monthStart, startOfDay } from '../../domain/dates'
import { totalsOf } from '../../domain/stats'
import { Empty, MonthSelector, Tile, TxnRow, useEditor, useMoney } from '../common'
import { Icon } from '../icons'

const FILTERS: [TxnType | null, string][] = [[null, 'All'], ['EXPENSE', 'Expenses'], ['INCOME', 'Income'], ['TRANSFER', 'Transfers']]

export function HistoryPage() {
  const data = useData()
  const money = useMoney()
  const openEditor = useEditor()
  const [month, setMonth] = useState(currentMonth())
  const [filter, setFilter] = useState<TxnType | null>(null)
  const [query, setQuery] = useState('')
  const categories = useMemo(() => byId(data.categories), [data.categories])
  const accounts = useMemo(() => byId(data.accounts), [data.accounts])
  const searching = query.trim().length > 0

  // A search looks through every month; otherwise one month is listed.
  const visible = useMemo(() => {
    let list: Txn[]
    if (searching) {
      const q = query.trim().toLowerCase()
      list = data.transactions.filter(t =>
        t.note.toLowerCase().includes(q) || (t.categoryId && categories.get(t.categoryId)?.name.toLowerCase().includes(q)),
      ).slice(0, 500)
    } else {
      const start = monthStart(month)
      const end = monthEnd(month)
      list = data.transactions.filter(t => t.date >= start && t.date < end)
    }
    return filter ? list.filter(t => t.type === filter) : list
  }, [data.transactions, month, filter, query, searching, categories])

  const days = useMemo(() => {
    const groups = new Map<number, Txn[]>()
    for (const t of visible) {
      const d = startOfDay(t.date)
      const g = groups.get(d)
      if (g) g.push(t)
      else groups.set(d, [t])
    }
    return [...groups.entries()].sort((a, b) => b[0] - a[0])
  }, [visible])
  const totals = totalsOf(visible)
  const net = totals.income - totals.expense

  return (
    <div className="page">
      <div className="page-head"><h1>History</h1></div>
      <div className="stack">
        <div className="search">
          <Icon name="search" size={18} />
          <input placeholder="Search notes and categories in every month" value={query} onChange={e => setQuery(e.target.value)} aria-label="Search" />
          {searching && <button className="icon-btn" onClick={() => setQuery('')} aria-label="Clear search"><Icon name="close" size={18} /></button>}
        </div>
        {!searching && <MonthSelector month={month} onChange={setMonth} />}
        <div className="chips">
          {FILTERS.map(([value, label]) => (
            <button key={label} className={`chip ${filter === value ? 'on' : ''}`} onClick={() => setFilter(value)}>{label}</button>
          ))}
        </div>
        <div className="tiles">
          <Tile label="Income" value={money.format(totals.income)} className="income" />
          <Tile label="Expense" value={money.format(totals.expense)} className="expense" />
          <Tile label="Net" value={money.format(net, true)} className={net >= 0 ? 'income' : 'expense'} />
        </div>
        <div className="card" style={{ padding: '0 6px 6px', overflow: 'hidden' }}>
          {days.length === 0 ? (
            searching
              ? <Empty emoji="🔍" title="No matches" body="Nothing in your notes or category names matches that search." />
              : <Empty emoji="🗓️" title="Nothing this month" body={`Transactions you add for ${monthLabel(month)} will show here.`} />
          ) : days.map(([day, items]) => {
            const dayNet = items.reduce((s, t) => s + (t.type === 'INCOME' ? t.amount : t.type === 'EXPENSE' ? -t.amount : 0), 0)
            return (
              <div key={day}>
                <div className="day-head">
                  <span>{dayHeader(day)}</span>
                  {dayNet !== 0 && <span className="net num">{money.format(dayNet, true)}</span>}
                </div>
                {items.map(t => (
                  <TxnRow key={t.id} txn={t} categories={categories} accounts={accounts} showDate={searching} onOpen={id => openEditor({ id })} />
                ))}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
