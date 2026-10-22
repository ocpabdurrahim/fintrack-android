// Port of Recurrence.occurrence in the Android app's domain/Stats.kt.
// Occurrence n is always counted from the start, in local time, with
// java.time's clamping: Jan 31 + 1 month = Feb 28, + 2 months = Mar 31.

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'

function lastDayOf(year: number, monthIndex: number): number {
  return new Date(year, monthIndex + 1, 0).getDate()
}

export function occurrence(start: number, frequency: Frequency, n: number): number {
  const d = new Date(start)
  const [h, mi, s, ms] = [d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds()]
  switch (frequency) {
    case 'DAILY':
      return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n, h, mi, s, ms).getTime()
    case 'WEEKLY':
      return new Date(d.getFullYear(), d.getMonth(), d.getDate() + 7 * n, h, mi, s, ms).getTime()
    case 'MONTHLY': {
      const target = new Date(d.getFullYear(), d.getMonth() + n, 1)
      const day = Math.min(d.getDate(), lastDayOf(target.getFullYear(), target.getMonth()))
      return new Date(target.getFullYear(), target.getMonth(), day, h, mi, s, ms).getTime()
    }
    case 'YEARLY': {
      const year = d.getFullYear() + n
      const day = Math.min(d.getDate(), lastDayOf(year, d.getMonth()))
      return new Date(year, d.getMonth(), day, h, mi, s, ms).getTime()
    }
  }
}
