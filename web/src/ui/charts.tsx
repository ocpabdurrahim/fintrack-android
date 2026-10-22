import type { ReactNode } from 'react'

/** A ring chart; [slices] are value/colour pairs, [children] go in the hole. */
export function Donut({ slices, children, size = 220, thickness = 26 }: {
  slices: { value: number; color: string }[]
  children?: ReactNode
  size?: number
  thickness?: number
}) {
  const r = (size - thickness) / 2
  const c = 2 * Math.PI * r
  const total = slices.reduce((s, x) => s + x.value, 0)
  const gap = slices.length > 1 ? 2 : 0
  let offset = 0
  return (
    <div className="donut-wrap" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--chip)" strokeWidth={thickness} />
        {total > 0 && slices.map((s, i) => {
          const length = (s.value / total) * c
          const dash = Math.max(0, length - gap)
          const el = (
            <circle key={i} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color} strokeWidth={thickness}
              strokeDasharray={`${dash} ${c - dash}`} strokeDashoffset={-offset} />
          )
          offset += length
          return el
        })}
      </svg>
      <div className="donut-center">{children}</div>
    </div>
  )
}

/** Side-by-side bars per group, e.g. income and expense per month. */
export function GroupedBars({ groups, colors, highlight = -1 }: {
  groups: { label: string; values: number[] }[]
  colors: string[]
  highlight?: number
}) {
  const max = Math.max(1, ...groups.flatMap(g => g.values))
  return (
    <div className="bars">
      {groups.map((g, i) => (
        <div className="group" key={i}>
          <div className="cols">
            {g.values.map((v, s) => (
              <div key={s} className="col" style={{ height: v > 0 ? `${Math.max(2, (v / max) * 100)}%` : 0, background: colors[s % colors.length] }} />
            ))}
          </div>
          <div className="tiny" style={{ fontWeight: i === highlight ? 700 : 500, color: i === highlight ? 'var(--text)' : 'var(--muted)' }}>{g.label}</div>
        </div>
      ))}
    </div>
  )
}

/** One thin bar per day of a month; the biggest day is drawn solid. */
export function DailyBars({ values, color }: { values: number[]; color: string }) {
  const max = Math.max(1, ...values)
  const peak = values.indexOf(Math.max(...values))
  return (
    <div>
      <div className="daily">
        {values.map((v, i) => (
          <div key={i} title={`Day ${i + 1}`}>
            {v > 0 && <i style={{ height: `${Math.max(3, (v / max) * 100)}%`, background: color, opacity: i === peak ? 1 : 0.5 }} />}
          </div>
        ))}
      </div>
      <div className="row tiny muted" style={{ justifyContent: 'space-between', marginTop: 4 }}>
        <span>1</span><span>15</span><span>{values.length}</span>
      </div>
    </div>
  )
}
