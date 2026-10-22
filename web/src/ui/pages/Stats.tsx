import { useMemo, useState } from 'react'
import { byId } from '../../data/actions'
import { cssColor } from '../../data/defaults'
import { useData } from '../../data/store'
import { addMonths, currentMonth, monthEnd, monthLabel, monthStart, shortMonthLabel } from '../../domain/dates'
import { byCategory, dailyAverage, dailyTotals, monthlyTotals, totalsOf } from '../../domain/stats'
import { DailyBars, Donut, GroupedBars } from '../charts'
import { categoryBadgeColor, EmojiBadge, Empty, MonthSelector, ProgressBar, Segmented, Tile, useMoney } from '../common'

export function StatsPage() {
  const data = useData()
  const money = useMoney()
  const [month, setMonth] = useState(currentMonth())
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE')
  const categories = useMemo(() => byId(data.categories), [data.categories])
  const isExpense = type === 'EXPENSE'

  const view = useMemo(() => {
    const start = monthStart(month)
    const end = monthEnd(month)
    const inMonth = data.transactions.filter(t => t.date >= start && t.date < end)
    const months = [5, 4, 3, 2, 1, 0].map(n => addMonths(month, -n))
    const windowStart = monthStart(months[0])
    const window = data.transactions.filter(t => t.date >= windowStart && t.date < end)
    const totals = totalsOf(inMonth)
    const monthly = monthlyTotals(window, months)
    return {
      totals,
      shares: byCategory(inMonth, type),
      trend: months.map((m, i) => ({ month: m, totals: monthly[i] })),
      daily: dailyTotals(inMonth, month, type),
      average: dailyAverage(isExpense ? totals.expense : totals.income, month),
    }
  }, [data.transactions, month, type, isExpense])

  const typeTotal = isExpense ? view.totals.expense : view.totals.income
  const typeColor = isExpense ? 'var(--expense)' : 'var(--income)'
  const net = view.totals.income - view.totals.expense
  const best = view.trend.reduce<{ month: string; net: number } | null>((b, x) => {
    const n = x.totals.income - x.totals.expense
    return !b || n > b.net ? { month: x.month, net: n } : b
  }, null)
  const peak = view.daily.reduce((p, v, i) => (v > view.daily[p] ? i : p), 0)

  return (
    <div className="page">
      <div className="page-head"><h1>Statistics</h1></div>
      <div className="stack">
        <MonthSelector month={month} onChange={setMonth} />
        <div className="tiles">
          <Tile label="Income" value={money.format(view.totals.income)} className="income" />
          <Tile label="Expense" value={money.format(view.totals.expense)} className="expense" />
          <Tile label="Saved" value={money.format(net, true)} className={net >= 0 ? 'income' : 'expense'} />
          <Tile label={isExpense ? 'Spent per day' : 'Earned per day'} value={money.format(view.average)} />
        </div>
        <Segmented options={[['EXPENSE', 'Expenses'], ['INCOME', 'Income']]} value={type} onChange={setType} />

        <div className="grid-2">
          <div className="card pad">
            <h2>{isExpense ? 'Where the money went' : 'Where the money came from'}</h2>
            {view.shares.length === 0 ? (
              <Empty emoji="📊" title={`No ${isExpense ? 'expenses' : 'income'} in ${monthLabel(month)}`} body="Add a few transactions and the breakdown appears here." />
            ) : (
              <>
                <Donut slices={view.shares.map(s => ({
                  value: s.amount,
                  color: s.categoryId && categories.get(s.categoryId) ? cssColor(categories.get(s.categoryId)!.color) : 'var(--muted)',
                }))}>
                  <div>
                    <div className="small muted">{isExpense ? 'Spent' : 'Earned'}</div>
                    <div className="bold num" style={{ fontSize: 22, color: typeColor }}>{money.format(typeTotal)}</div>
                  </div>
                </Donut>
                {view.shares.map(s => {
                  const c = s.categoryId ? categories.get(s.categoryId) : undefined
                  const color = c ? cssColor(c.color) : 'var(--muted)'
                  return (
                    <div key={s.categoryId ?? 'none'} className="row" style={{ padding: '7px 0' }}>
                      <EmojiBadge emoji={c?.emoji ?? '❔'} color={categoryBadgeColor(c)} size={36} />
                      <div className="grow">
                        <div className="row"><span className="grow">{c?.name ?? 'Uncategorised'}</span><span className="num">{money.format(s.amount)}</span></div>
                        <div className="row" style={{ marginTop: 4 }}>
                          <div className="grow"><ProgressBar fraction={s.fraction} color={color} height={6} /></div>
                          <span className="tiny muted" style={{ width: 34, textAlign: 'right' }}>{Math.round(s.fraction * 100)}%</span>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </>
            )}
          </div>

          <div className="stack">
            <div className="card pad">
              <h2>Last 6 months</h2>
              <div className="row" style={{ gap: 16, marginBottom: 14 }}>
                <span className="legend"><i style={{ background: 'var(--income)' }} />Income</span>
                <span className="legend"><i style={{ background: 'var(--expense)' }} />Expense</span>
              </div>
              <GroupedBars
                groups={view.trend.map(x => ({ label: shortMonthLabel(x.month), values: [x.totals.income, x.totals.expense] }))}
                colors={['var(--income)', 'var(--expense)']}
                highlight={5}
              />
              {best && best.net > 0 && (
                <p className="small muted" style={{ marginBottom: 0 }}>
                  Best month for saving: {monthLabel(best.month)} ({money.format(best.net, true)})
                </p>
              )}
            </div>
            <div className="card pad">
              <h2>{isExpense ? 'Daily spending' : 'Daily income'}</h2>
              {view.daily[peak] > 0 && (
                <p className="small muted" style={{ marginTop: -4 }}>
                  Biggest day: {peak + 1} {shortMonthLabel(month)} · {money.format(view.daily[peak])}
                </p>
              )}
              <DailyBars values={view.daily} color={typeColor} />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
