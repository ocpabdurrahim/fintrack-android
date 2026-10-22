// Calendar helpers in the browser's time zone, and the labels the UI shows.
// A month is represented as "YYYY-MM".

export type YearMonth = string

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

export function ym(year: number, monthIndex: number): YearMonth {
  const d = new Date(year, monthIndex, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

export function currentMonth(now = new Date()): YearMonth {
  return ym(now.getFullYear(), now.getMonth())
}

export function parseYm(value: YearMonth): { year: number; month: number } {
  const [y, m] = value.split('-').map(Number)
  return { year: y, month: m - 1 }
}

export function addMonths(value: YearMonth, n: number): YearMonth {
  const { year, month } = parseYm(value)
  return ym(year, month + n)
}

export function monthOf(millis: number): YearMonth {
  const d = new Date(millis)
  return ym(d.getFullYear(), d.getMonth())
}

export function monthStart(value: YearMonth): number {
  const { year, month } = parseYm(value)
  return new Date(year, month, 1).getTime()
}

/** Exclusive end: the first moment of the next month. */
export function monthEnd(value: YearMonth): number {
  return monthStart(addMonths(value, 1))
}

export function daysInMonth(value: YearMonth): number {
  const { year, month } = parseYm(value)
  return new Date(year, month + 1, 0).getDate()
}

export function startOfDay(millis: number): number {
  const d = new Date(millis)
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

/** "YYYY-MM-DD" for a date input, from millis. */
export function toDateInput(millis: number): string {
  const d = new Date(millis)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Combines a "YYYY-MM-DD" date with the time of day of [timeFrom]. */
export function fromDateInput(value: string, timeFrom: number): number {
  const [y, m, d] = value.split('-').map(Number)
  const t = new Date(timeFrom)
  return new Date(y, m - 1, d, t.getHours(), t.getMinutes(), t.getSeconds(), t.getMilliseconds()).getTime()
}

export function monthLabel(value: YearMonth): string {
  const { year, month } = parseYm(value)
  return `${MONTHS[month]} ${year}`
}

export function shortMonthLabel(value: YearMonth): string {
  return MONTHS[parseYm(value).month].slice(0, 3)
}

export function fullDate(millis: number): string {
  const d = new Date(millis)
  return `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)} ${d.getFullYear()}`
}

export function shortDate(millis: number): string {
  const d = new Date(millis)
  return `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}`
}

export function timeLabel(millis: number): string {
  const d = new Date(millis)
  const h = d.getHours() % 12 || 12
  return `${h}:${String(d.getMinutes()).padStart(2, '0')} ${d.getHours() < 12 ? 'AM' : 'PM'}`
}

/** "Today", "Yesterday", "Mon, 20 Oct", or with the year once it is not this year. */
export function dayHeader(dayStart: number, now = Date.now()): string {
  const today = startOfDay(now)
  const yesterday = startOfDay(today - 12 * 3600 * 1000)
  if (dayStart === today) return 'Today'
  if (dayStart === yesterday) return 'Yesterday'
  const d = new Date(dayStart)
  const base = `${DAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}`
  return d.getFullYear() === new Date(now).getFullYear() ? base : `${base} ${d.getFullYear()}`
}

export function greeting(now = new Date()): string {
  const h = now.getHours()
  if (h >= 5 && h <= 11) return 'Good morning'
  if (h >= 12 && h <= 16) return 'Good afternoon'
  if (h >= 17 && h <= 21) return 'Good evening'
  return 'Good night'
}
