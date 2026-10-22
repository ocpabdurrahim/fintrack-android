import { useMemo, useState } from 'react'
import { byId, removeBudget, setBudget } from '../../data/actions'
import { cssColor } from '../../data/defaults'
import { useData } from '../../data/store'
import { OVERALL, type Category } from '../../data/types'
import { currentMonth, daysInMonth, monthEnd, monthLabel, monthStart } from '../../domain/dates'
import { budgetColor, categoryBadgeColor, EmojiBadge, Empty, MonthSelector, ProgressBar, useMoney } from '../common'
import { BudgetDialog } from '../dialogs'
import { Icon } from '../icons'

interface Line {
  category: Category | undefined
  categoryId: string
  limit: number
  spent: number
}

type Editor = { kind: 'overall' } | { kind: 'new' } | { kind: 'existing'; line: Line }

export function BudgetsPage() {
  const data = useData()
  const money = useMoney()
  const [month, setMonth] = useState(currentMonth())
  const [editor, setEditor] = useState<Editor | null>(null)

  const view = useMemo(() => {
    const start = monthStart(month)
    const end = monthEnd(month)
    const expenses = data.transactions.filter(t => t.type === 'EXPENSE' && t.date >= start && t.date < end)
    const spentBy = new Map<string | null, number>()
    for (const t of expenses) spentBy.set(t.categoryId, (spentBy.get(t.categoryId) ?? 0) + t.amount)
    const total = expenses.reduce((s, t) => s + t.amount, 0)
    const categories = byId(data.categories)
    const overall = data.budgets.find(b => b.categoryId === OVERALL)
    const lines: Line[] = data.budgets
      .filter(b => b.categoryId !== OVERALL && categories.has(b.categoryId))
      .map(b => ({ category: categories.get(b.categoryId), categoryId: b.categoryId, limit: b.amount, spent: spentBy.get(b.categoryId) ?? 0 }))
      .sort((a, b) => b.spent / b.limit - a.spent / a.limit)
    const budgeted = new Set(data.budgets.map(b => b.categoryId))
    const available = data.categories.filter(c => c.type === 'EXPENSE' && !c.archived && !budgeted.has(c.id))
    return { total, overall, lines, available }
  }, [data.transactions, data.budgets, data.categories, month])

  const overall = view.overall
  const left = overall ? overall.amount - view.total : 0
  const now = new Date()
  const isCurrent = month === currentMonth()
  const daysLeft = daysInMonth(month) - now.getDate() + 1

  return (
    <div className="page">
      <div className="page-head"><h1>Budgets</h1></div>
      <div className="stack">
        <MonthSelector month={month} onChange={setMonth} />
        <div className="card pad" style={{ background: 'var(--secondary-container)', borderColor: 'transparent' }}>
          <h2>Monthly budget</h2>
          {!overall ? (
            <>
              <p style={{ marginTop: 0 }}>Spent {money.format(view.total)} in {monthLabel(month)}. Set a limit to see how much is left each day.</p>
              <button className="btn" onClick={() => setEditor({ kind: 'overall' })}>Set monthly budget</button>
            </>
          ) : (
            <>
              <div className="row" style={{ alignItems: 'baseline', margin: '6px 0 10px' }}>
                <span className="bold num" style={{ fontSize: 30, color: budgetColor(view.total / overall.amount) }}>{money.format(view.total)}</span>
                <span className="num">of {money.format(overall.amount)}</span>
              </div>
              <ProgressBar fraction={view.total / overall.amount} color={budgetColor(view.total / overall.amount)} height={10} />
              <div className="bold" style={{ marginTop: 10, color: left >= 0 ? 'inherit' : 'var(--expense)' }}>
                {left >= 0 ? `${money.format(left)} left` : `Over budget by ${money.format(-left)}`}
              </div>
              {isCurrent && left > 0 && (
                <div className="small">About {money.format(Math.floor(left / daysLeft))} a day for the remaining {daysLeft} {daysLeft === 1 ? 'day' : 'days'}</div>
              )}
              <button className="btn text" style={{ paddingLeft: 0, marginTop: 6 }} onClick={() => setEditor({ kind: 'overall' })}>Change</button>
            </>
          )}
        </div>

        <div className="section-title"><span>By category</span></div>
        {view.lines.length === 0 && (
          <div className="card"><Empty emoji="🎯" title="No category budgets" body="Cap the categories that tend to run away — food, shopping, transport." /></div>
        )}
        <div className="stack" style={{ gap: 10 }}>
          {view.lines.map(line => {
            const fraction = line.spent / line.limit
            const lineLeft = line.limit - line.spent
            return (
              <button key={line.categoryId} className="card pad" style={{ textAlign: 'left' }} onClick={() => setEditor({ kind: 'existing', line })}>
                <div className="row">
                  <EmojiBadge emoji={line.category?.emoji ?? '❔'} color={categoryBadgeColor(line.category)} size={36} />
                  <div className="grow">
                    <div>{line.category?.name}</div>
                    <div className="small muted num">{money.format(line.spent)} of {money.format(line.limit)}</div>
                  </div>
                  <span className="bold small num" style={{ color: budgetColor(fraction) }}>
                    {lineLeft >= 0 ? `${money.format(lineLeft)} left` : `${money.format(-lineLeft)} over`}
                  </span>
                </div>
                <div style={{ marginTop: 10 }}>
                  <ProgressBar fraction={fraction} color={line.category ? cssColor(line.category.color) : budgetColor(fraction)} />
                </div>
              </button>
            )
          })}
        </div>
        {view.available.length > 0 && (
          <button className="btn outline" onClick={() => setEditor({ kind: 'new' })}><Icon name="plus" size={18} />Add a category budget</button>
        )}
      </div>

      {editor?.kind === 'overall' && (
        <BudgetDialog
          title="Monthly budget" initialAmount={overall?.amount ?? null}
          onSave={(_, amount) => { setBudget(OVERALL, amount); setEditor(null) }}
          onRemove={overall ? () => { removeBudget(OVERALL); setEditor(null) } : undefined}
          onClose={() => setEditor(null)}
        />
      )}
      {editor?.kind === 'new' && (
        <BudgetDialog
          title="Category budget" initialAmount={null} choices={view.available}
          onSave={(categoryId, amount) => { if (categoryId) setBudget(categoryId, amount); setEditor(null) }}
          onClose={() => setEditor(null)}
        />
      )}
      {editor?.kind === 'existing' && (
        <BudgetDialog
          title={`${editor.line.category?.emoji ?? ''} ${editor.line.category?.name ?? 'Budget'}`.trim()}
          initialAmount={editor.line.limit}
          onSave={(_, amount) => { setBudget(editor.line.categoryId, amount); setEditor(null) }}
          onRemove={() => { removeBudget(editor.line.categoryId); setEditor(null) }}
          onClose={() => setEditor(null)}
        />
      )}
    </div>
  )
}
