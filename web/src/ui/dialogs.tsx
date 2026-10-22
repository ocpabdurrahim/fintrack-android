import { useState } from 'react'
import { ACCOUNT_EMOJIS, CATEGORY_EMOJIS, cssColor, PALETTE } from '../data/defaults'
import { newId } from '../data/ids'
import type { Account, Category, CategoryType } from '../data/types'
import { amountToInput, parseAmount, sanitizeAmount } from '../domain/money'
import { EmojiBadge, Modal, useMoney } from './common'

export function AmountField({ value, onChange, label, allowNegative = false, autoFocus = false }: {
  value: string
  onChange: (v: string) => void
  label: string
  allowNegative?: boolean
  autoFocus?: boolean
}) {
  const money = useMoney()
  return (
    <label className="field">
      <span>{label}</span>
      <div className="row input" style={{ padding: '0 13px' }}>
        <span className="bold">{money.symbol}</span>
        <input
          inputMode="decimal" value={value} autoFocus={autoFocus}
          onChange={e => { const v = sanitizeAmount(e.target.value, allowNegative); if (v !== null) onChange(v) }}
          style={{ border: 0, outline: 'none', background: 'transparent', padding: '11px 0', flex: 1, minWidth: 0 }}
        />
      </div>
    </label>
  )
}

function EmojiPicker({ options, value, onPick }: { options: string[]; value: string; onPick: (e: string) => void }) {
  return (
    <div className="emoji-grid">
      {options.map(e => (
        <button key={e} type="button" className={e === value ? 'on' : ''} onClick={() => onPick(e)}>{e}</button>
      ))}
    </div>
  )
}

export function CategoryDialog({ initial, type, onSave, onDelete, onClose }: {
  initial: Category | null
  type: CategoryType
  onSave: (c: Category) => void
  onDelete?: () => void
  onClose: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [emoji, setEmoji] = useState(initial?.emoji ?? (type === 'INCOME' ? '💰' : '📦'))
  const [color, setColor] = useState(initial?.color ?? PALETTE[Math.floor(Math.random() * PALETTE.length)])
  const save = () => onSave({
    ...(initial ?? { id: newId(), type, archived: false, sortOrder: 0 }),
    name: name.trim(), emoji: emoji.trim(), color,
  })
  return (
    <Modal onClose={onClose} label="Category">
      <h2>{initial ? 'Edit category' : `New ${type === 'INCOME' ? 'income' : 'expense'} category`}</h2>
      <div className="stack">
        <div className="row">
          <EmojiBadge emoji={emoji} color={cssColor(color, 0.25)} size={52} />
          <label className="field grow">
            <span>Name</span>
            <input className="input" value={name} maxLength={30} autoFocus onChange={e => setName(e.target.value)} />
          </label>
        </div>
        <div className="field"><span>Icon</span><EmojiPicker options={CATEGORY_EMOJIS} value={emoji} onPick={setEmoji} /></div>
        <label className="field">
          <span>Or type any emoji</span>
          <input className="input" value={emoji} maxLength={8} onChange={e => setEmoji(e.target.value)} />
        </label>
        <div className="field">
          <span>Colour</span>
          <div className="color-grid">
            {PALETTE.map(c => (
              <button key={c} type="button" aria-label="Colour" className={c === color ? 'on' : ''} style={{ background: cssColor(c) }} onClick={() => setColor(c)} />
            ))}
          </div>
        </div>
      </div>
      <div className="actions">
        {onDelete && <button className="btn danger" onClick={onDelete}>Delete</button>}
        <span className="grow" />
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!name.trim() || !emoji.trim()} onClick={save}>Save</button>
      </div>
    </Modal>
  )
}

export function AccountDialog({ initial, onSave, onDelete, onClose }: {
  initial: Account | null
  onSave: (a: Account) => void
  onDelete?: () => void
  onClose: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [emoji, setEmoji] = useState(initial?.emoji ?? '💵')
  const [opening, setOpening] = useState(initial && initial.openingBalance !== 0 ? amountToInput(initial.openingBalance) : '')
  const save = () => onSave({
    ...(initial ?? { id: newId(), archived: false, sortOrder: 0 }),
    name: name.trim(), emoji, openingBalance: parseAmount(opening, true) ?? 0,
  })
  return (
    <Modal onClose={onClose} label="Account">
      <h2>{initial ? 'Edit account' : 'New account'}</h2>
      <div className="stack">
        <div className="row">
          <EmojiBadge emoji={emoji} color="color-mix(in srgb, var(--primary) 22%, transparent)" size={52} />
          <label className="field grow">
            <span>Name</span>
            <input className="input" value={name} maxLength={30} autoFocus placeholder="e.g. bKash, Dutch-Bangla" onChange={e => setName(e.target.value)} />
          </label>
        </div>
        <div className="field"><span>Icon</span><EmojiPicker options={ACCOUNT_EMOJIS} value={emoji} onPick={setEmoji} /></div>
        <AmountField label="Starting balance" value={opening} onChange={setOpening} allowNegative />
        <p className="small muted" style={{ margin: 0 }}>
          What the account held before you started using FinTrack. Use a minus sign for a credit card or loan.
        </p>
      </div>
      <div className="actions">
        {onDelete && <button className="btn danger" onClick={onDelete}>Delete</button>}
        <span className="grow" />
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!name.trim()} onClick={save}>Save</button>
      </div>
    </Modal>
  )
}

/** Set a monthly limit; with [choices] the category is picked first. */
export function BudgetDialog({ title, initialAmount, choices, onSave, onRemove, onClose }: {
  title: string
  initialAmount: number | null
  choices?: Category[]
  onSave: (categoryId: string | null, amount: number) => void
  onRemove?: () => void
  onClose: () => void
}) {
  const [amount, setAmount] = useState(initialAmount ? amountToInput(initialAmount) : '')
  const [categoryId, setCategoryId] = useState<string | null>(choices?.[0]?.id ?? null)
  const parsed = parseAmount(amount)
  return (
    <Modal onClose={onClose} narrow label={title}>
      <h2>{title}</h2>
      <div className="stack">
        {choices && (
          <div className="field">
            <span>Category</span>
            <div className="row wrap">
              {choices.map(c => (
                <button key={c.id} className={`chip ${c.id === categoryId ? 'on' : ''}`} onClick={() => setCategoryId(c.id)}>
                  {c.emoji} {c.name}
                </button>
              ))}
            </div>
          </div>
        )}
        <AmountField label="Monthly limit" value={amount} onChange={setAmount} autoFocus />
      </div>
      <div className="actions">
        {onRemove && <button className="btn danger" onClick={onRemove}>Remove</button>}
        <span className="grow" />
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!parsed || parsed <= 0 || (choices !== undefined && !categoryId)} onClick={() => parsed && onSave(categoryId, parsed)}>
          Save
        </button>
      </div>
    </Modal>
  )
}

export function ChoiceDialog<T extends string>({ title, options, value, onPick, onClose }: {
  title: string
  options: { value: T; label: string; hint?: string }[]
  value: T
  onPick: (v: T) => void
  onClose: () => void
}) {
  return (
    <Modal onClose={onClose} narrow label={title}>
      <h2>{title}</h2>
      <div className="list" style={{ maxHeight: 420, overflow: 'auto' }}>
        {options.map(o => (
          <button key={o.value} className="item" onClick={() => { onPick(o.value); onClose() }}>
            <input type="radio" readOnly checked={o.value === value} />
            <span className="grow">{o.label}</span>
            {o.hint && <span className="small muted">{o.hint}</span>}
          </button>
        ))}
      </div>
      <div className="actions"><button className="btn text" onClick={onClose}>Close</button></div>
    </Modal>
  )
}
