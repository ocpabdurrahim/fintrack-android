// Port of the Android app's domain/Money.kt. Amounts are integers in minor
// units (paisa / cents): 1,250.50 is 125050. Keep the two files in step.

export type Grouping = 'SOUTH_ASIAN' | 'INTERNATIONAL'

const positiveInput = /^\d{0,12}(\.\d{0,2})?$/
const signedInput = /^-?\d{0,12}(\.\d{0,2})?$/

/** "1,250.5" -> 125050. Null when the text is not a usable amount. */
export function parseAmount(input: string, allowNegative = false): number | null {
  const s = input.trim().replace(/,/g, '')
  if (!(allowNegative ? signedInput : positiveInput).test(s)) return null
  const negative = s.startsWith('-')
  const body = negative ? s.slice(1) : s
  if (body === '' || body === '.') return null
  const dot = body.indexOf('.')
  const whole = Number((dot >= 0 ? body.slice(0, dot) : body) || '0')
  const fraction = Number((dot >= 0 ? body.slice(dot + 1) : '').padEnd(2, '0'))
  const value = whole * 100 + fraction
  return negative ? -value : value
}

/** Keyboard filter for amount fields: the cleaned text, or null to reject the keystroke. */
export function sanitizeAmount(raw: string, allowNegative = false): string | null {
  const s = raw.replace(/[, ]/g, '')
  return (allowNegative ? signedInput : positiveInput).test(s) ? s : null
}

/** 125050 -> "1250.50", 125000 -> "1250": what an edit field starts with. */
export function amountToInput(minor: number): string {
  const sign = minor < 0 ? '-' : ''
  const a = Math.abs(minor)
  const fraction = a % 100
  const whole = Math.floor(a / 100)
  return fraction === 0 ? `${sign}${whole}` : `${sign}${whole}.${String(fraction).padStart(2, '0')}`
}

export function groupDigits(digits: string, grouping: Grouping): string {
  if (digits.length <= 3) return digits
  const head = digits.slice(0, -3)
  const size = grouping === 'SOUTH_ASIAN' ? 2 : 3
  const groups: string[] = []
  for (let end = head.length; end > 0; end -= size) groups.unshift(head.slice(Math.max(0, end - size), end))
  return `${groups.join(',')},${digits.slice(-3)}`
}

/** Word-like symbols get a space ("Rs 500"); sign-like ones do not ("৳500"). */
function prefix(symbol: string): string {
  return symbol && /\p{L}$/u.test(symbol) ? `${symbol} ` : symbol
}

/** 125050 -> "৳1,250.50"; whole amounts drop the decimals: "৳1,250". */
export function formatMoney(minor: number, symbol: string, grouping: Grouping, showPlus = false): string {
  const a = Math.abs(minor)
  const fraction = a % 100
  const digits = groupDigits(String(Math.floor(a / 100)), grouping)
  const number = fraction === 0 ? digits : `${digits}.${String(fraction).padStart(2, '0')}`
  const sign = minor < 0 ? '-' : showPlus && minor > 0 ? '+' : ''
  return `${sign}${prefix(symbol)}${number}`
}

/** Short labels for charts: ৳950, ৳12.5K, ৳3.2L, ৳1.1Cr / $4.2M. */
export function compactMoney(minor: number, symbol: string, grouping: Grouping): string {
  const value = Math.abs(minor) / 100
  const sign = minor < 0 ? '-' : ''
  let divisor = 1
  let suffix = ''
  if (grouping === 'SOUTH_ASIAN' && value >= 1e7) [divisor, suffix] = [1e7, 'Cr']
  else if (grouping === 'SOUTH_ASIAN' && value >= 1e5) [divisor, suffix] = [1e5, 'L']
  else if (grouping === 'INTERNATIONAL' && value >= 1e9) [divisor, suffix] = [1e9, 'B']
  else if (grouping === 'INTERNATIONAL' && value >= 1e6) [divisor, suffix] = [1e6, 'M']
  else if (value >= 1e3) [divisor, suffix] = [1e3, 'K']
  const number = suffix === '' ? String(Math.round(value)) : oneDecimal(value / divisor)
  return `${sign}${prefix(symbol)}${number}${suffix}`
}

function oneDecimal(v: number): string {
  const tenths = Math.round(v * 10)
  return tenths % 10 === 0 ? String(tenths / 10) : `${Math.floor(tenths / 10)}.${tenths % 10}`
}
