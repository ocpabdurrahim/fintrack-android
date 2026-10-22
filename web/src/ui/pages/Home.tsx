import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { accountBalances, byId } from '../../data/actions'
import { useData } from '../../data/store'
import { OVERALL } from '../../data/types'
import { currentMonth, greeting, monthEnd, monthLabel, monthStart, startOfDay } from '../../domain/dates'
import { totalsOf } from '../../domain/stats'
import { budgetColor, EmojiBadge, Empty, ProgressBar, TxnRow, useEditor, useMoney } from '../common'
import { Icon } from '../icons'

const HIDE_KEY = 'fintrack.hideBalance'

export function HomePage() {
  const data = useData()
  const money = useMoney()
  const openEditor = useEditor()
  const navigate = useNavigate()
  const [hidden, setHidden] = useState(() => localStorage.getItem(HIDE_KEY) === '1')
  const toggleHidden = () => {
    localStorage.setItem(HIDE_KEY, hidden ? '0' : '1')
    setHidden(!hidden)
  }

  const month = currentMonth()
  const balances = useMemo(() => accountBalances(data.accounts, data.transactions), [data.accounts, data.transactions])
  const total = [...balances.values()].reduce((s, v) => s + v, 0)
  const monthTotals = useMemo(() => {
    const start = monthStart(month)
    const end = monthEnd(month)
    return totalsOf(data.transactions.filter(t => t.date >= start && t.date < end))
  }, [data.transactions, month])
  const today = useMemo(() => {
    const start = startOfDay(Date.now())
    return totalsOf(data.transactions.filter(t => t.date >= start && t.date < start + 86_400_000))
  }, [data.transactions])
  const budget = data.budgets.find(b => b.categoryId === OVERALL)?.amount
  const accounts = byId(data.accounts)
  const categories = byId(data.categories)
  const show = (v: number) => (hidden ? '••••' : money.format(v))

  return (
    <div className="page">
      <div className="page-head">
        <div className="grow">
          <div className="muted small">{greeting()}</div>
          <h1>FinTrack</h1>
        </div>
      </div>

      <div className="grid-2">
        <div className="stack">
          <div className="hero">
            <div className="row">
              <span className="label grow">Total balance</span>
              <button className="icon-btn" style={{ color: '#fff' }} onClick={toggleHidden} aria-label={hidden ? 'Show amounts' : 'Hide amounts'}>
                <Icon name={hidden ? 'eyeOff' : 'eye'} />
              </button>
            </div>
            <div className="big num" data-testid="balance-total">{hidden ? '••••••' : money.format(total)}</div>
            <div className="row">
              <HeroStat icon="down" label="Income" value={show(monthTotals.income)} />
              <HeroStat icon="up" label="Expense" value={show(monthTotals.expense)} />
            </div>
            <div className="foot">This month · {monthLabel(month)}</div>
          </div>

          <div className="quick">
            <button onClick={() => openEditor({ type: 'INCOME' })}><Icon name="plus" className="income" size={18} />Income</button>
            <button onClick={() => openEditor({ type: 'EXPENSE' })}><Icon name="minus" className="expense" size={18} />Expense</button>
            <button onClick={() => openEditor({ type: 'TRANSFER' })}><Icon name="swap" className="transfer" size={18} />Transfer</button>
          </div>

          <div className="card pad">
            <div className="row">
              <div className="grow">
                <div className="bold">Today</div>
                <div className="small muted">
                  {today.count === 0 ? 'Nothing recorded yet' : today.count === 1 ? '1 entry' : `${today.count} entries`}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div className="expense bold num" style={{ fontSize: 18 }}>{today.expense > 0 ? '-' : ''}{money.format(today.expense)}</div>
                {today.income > 0 && <div className="income num">+{money.format(today.income)}</div>}
              </div>
            </div>
            {budget !== undefined && budget > 0 && (
              <button className="item" style={{ padding: '14px 0 0', marginTop: 12, borderTop: '1px solid var(--border)', display: 'block' }}
                onClick={() => navigate('/budgets')}>
                <div className="row" style={{ marginBottom: 8 }}>
                  <span className="grow bold small">Monthly budget</span>
                  <span className="small bold" style={{ color: budgetColor(monthTotals.expense / budget) }}>
                    {budget - monthTotals.expense >= 0
                      ? `${money.format(budget - monthTotals.expense)} left`
                      : `${money.format(monthTotals.expense - budget)} over`}
                  </span>
                </div>
                <ProgressBar fraction={monthTotals.expense / budget} color={budgetColor(monthTotals.expense / budget)} />
              </button>
            )}
          </div>
        </div>

        <div className="stack">
          <div>
            <div className="section-title">
              <span>Accounts</span>
              <button className="btn text" onClick={() => navigate('/accounts')}>Manage</button>
            </div>
            <div className="accounts-row">
              {data.accounts.filter(a => !a.archived).map(a => (
                <button key={a.id} className="account-card" onClick={() => navigate('/accounts')}>
                  <EmojiBadge emoji={a.emoji} color="color-mix(in srgb, var(--primary) 20%, transparent)" size={34} />
                  <div className="small muted" style={{ marginTop: 10 }}>{a.name}</div>
                  <div className="bold num">{show(balances.get(a.id) ?? 0)}</div>
                </button>
              ))}
            </div>
          </div>

          <div>
            <div className="section-title">
              <span>Recent</span>
              <button className="btn text" onClick={() => navigate('/history')}>See all</button>
            </div>
            <div className="card" style={{ padding: 6 }}>
              {data.transactions.length === 0 ? (
                <Empty emoji="🌱" title="No transactions yet" body="Add your first income or expense. It shows up on your phone too." />
              ) : (
                <div className="list">
                  {data.transactions.slice(0, 8).map(t => (
                    <TxnRow key={t.id} txn={t} categories={categories} accounts={accounts} showDate onOpen={id => openEditor({ id })} />
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function HeroStat({ icon, label, value }: { icon: 'down' | 'up'; label: string; value: string }) {
  return (
    <div className="stat">
      <span className="icon-circle"><Icon name={icon} size={18} /></span>
      <div>
        <div className="small" style={{ opacity: 0.8 }}>{label}</div>
        <div className="bold num" style={{ fontSize: 17 }}>{value}</div>
      </div>
    </div>
  )
}
