// The web app's copy of the user's data, kept in step with Firestore.
//
// Start-up reads whatever the browser already cached (instant), then listens
// only for documents changed since the newest cached one. Deletes are
// tombstones ({deleted: true}) so they arrive through the same listener.
// Writes update memory at once and reach Firestore in the background
// (queued offline). See docs/sync-protocol.md for the shared format.
import {
  collection, doc, getDocsFromCache, onSnapshot, query, serverTimestamp, setDoc, Timestamp, where, writeBatch,
  type DocumentData, type DocumentSnapshot, type Firestore, type Unsubscribe,
} from 'firebase/firestore'
import { useSyncExternalStore } from 'react'
import { occurrence } from '../domain/recurrence'
import { DEFAULT_ACCOUNTS, DEFAULT_CATEGORIES } from './defaults'
import { occurrenceId } from './ids'
import {
  COLLECTIONS, DEFAULT_CURRENCY,
  type Account, type Budget, type Category, type CollectionName, type Collections, type CurrencySettings,
  type Recurring, type Txn,
} from './types'

export interface DataSnapshot {
  /** False until the first data (cached or from the server) is in. */
  ready: boolean
  /** True once the server has answered for every collection. */
  synced: boolean
  /** Writes made here that the server has not confirmed yet. */
  pending: number
  error: string | null
  accounts: Account[]
  categories: Category[]
  transactions: Txn[]
  budgets: Budget[]
  recurring: Recurring[]
  settings: CurrencySettings
}

const num = (v: unknown, fallback = 0) => (typeof v === 'number' && Number.isFinite(v) ? v : fallback)
const str = (v: unknown, fallback = '') => (typeof v === 'string' ? v : fallback)
const strOrNull = (v: unknown) => (typeof v === 'string' ? v : null)
const bool = (v: unknown, fallback = false) => (typeof v === 'boolean' ? v : fallback)

const parsers: { [K in CollectionName]: (id: string, d: DocumentData) => Collections[K] } = {
  accounts: (id, d) => ({
    id, name: str(d.name), emoji: str(d.emoji, '💵'), openingBalance: num(d.openingBalance),
    archived: bool(d.archived), sortOrder: num(d.sortOrder),
  }),
  categories: (id, d) => ({
    id, name: str(d.name), type: d.type === 'INCOME' ? 'INCOME' : 'EXPENSE', emoji: str(d.emoji, '📦'),
    color: num(d.color, 0xff90a4ae | 0), archived: bool(d.archived), sortOrder: num(d.sortOrder),
  }),
  transactions: (id, d) => ({
    id, type: d.type === 'INCOME' || d.type === 'TRANSFER' ? d.type : 'EXPENSE', amount: num(d.amount),
    categoryId: strOrNull(d.categoryId), accountId: str(d.accountId), toAccountId: strOrNull(d.toAccountId),
    date: num(d.date), note: str(d.note), recurringId: strOrNull(d.recurringId), createdAt: num(d.createdAt, num(d.date)),
  }),
  budgets: (id, d) => ({ id, categoryId: str(d.categoryId), amount: num(d.amount) }),
  recurring: (id, d) => ({
    id, type: d.type === 'INCOME' || d.type === 'TRANSFER' ? d.type : 'EXPENSE', amount: num(d.amount),
    categoryId: strOrNull(d.categoryId), accountId: str(d.accountId), toAccountId: strOrNull(d.toAccountId),
    note: str(d.note), frequency: ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'].includes(d.frequency) ? d.frequency : 'MONTHLY',
    startDate: num(d.startDate), generated: num(d.generated, 1), active: bool(d.active, true),
  }),
}

function parseSettings(d: DocumentData | undefined): CurrencySettings {
  if (!d) return DEFAULT_CURRENCY
  return {
    currencyCode: str(d.currencyCode, DEFAULT_CURRENCY.currencyCode),
    currencySymbol: str(d.currencySymbol, DEFAULT_CURRENCY.currencySymbol),
    grouping: d.grouping === 'INTERNATIONAL' ? 'INTERNATIONAL' : 'SOUTH_ASIAN',
  }
}

type Maps = { [K in CollectionName]: Map<string, Collections[K]> }
type IdSets = Record<CollectionName, Set<string>>

const emptyMaps = (): Maps => ({
  accounts: new Map(), categories: new Map(), transactions: new Map(), budgets: new Map(), recurring: new Map(),
})
const emptySets = (): IdSets => ({
  accounts: new Set(), categories: new Set(), transactions: new Set(), budgets: new Set(), recurring: new Set(),
})

const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name)

class DataStore {
  private db: Firestore | null = null
  private uid: string | null = null
  private unsubs: Unsubscribe[] = []
  private maps = emptyMaps()
  /** Ids deleted somewhere; a recurring occurrence that was deleted must not come back. */
  private tombstones = emptySets()
  /** Every id seen, deleted or not: an account with history is never re-seeded. */
  private seen = emptySets()
  private serverLoaded = new Set<CollectionName>()
  private settings: CurrencySettings = DEFAULT_CURRENCY
  private ready = false
  private pending = 0
  private error: string | null = null
  private listeners = new Set<() => void>()
  private cached: DataSnapshot | null = null
  private recurringTimer: ReturnType<typeof setInterval> | null = null

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getSnapshot = (): DataSnapshot => {
    if (!this.cached) {
      const m = this.maps
      this.cached = {
        ready: this.ready,
        synced: this.serverLoaded.size === COLLECTIONS.length,
        pending: this.pending,
        error: this.error,
        accounts: [...m.accounts.values()].sort((a, b) => Number(a.archived) - Number(b.archived) || a.sortOrder - b.sortOrder || byName(a, b)),
        categories: [...m.categories.values()].sort((a, b) => a.type.localeCompare(b.type) || a.sortOrder - b.sortOrder || byName(a, b)),
        transactions: [...m.transactions.values()].sort((a, b) => b.date - a.date || b.createdAt - a.createdAt),
        budgets: [...m.budgets.values()],
        recurring: [...m.recurring.values()].sort((a, b) => Number(b.active) - Number(a.active) || a.startDate - b.startDate),
        settings: this.settings,
      }
    }
    return this.cached
  }

  private changed() {
    this.cached = null
    this.listeners.forEach(l => l())
  }

  async start(db: Firestore, uid: string) {
    this.stop()
    this.db = db
    this.uid = uid
    await Promise.all(COLLECTIONS.map(name => this.listen(name)))
    this.unsubs.push(
      onSnapshot(doc(db, 'users', uid), snap => {
        this.settings = parseSettings(snap.data())
        this.changed()
      }, err => this.fail(err)),
    )
    this.ready = true
    this.changed()
    // Recurring entries are due by the clock, not by a sync; check hourly.
    this.recurringTimer = setInterval(() => this.processRecurring(), 60 * 60 * 1000)
  }

  stop() {
    this.unsubs.forEach(u => u())
    this.unsubs = []
    if (this.recurringTimer) clearInterval(this.recurringTimer)
    this.recurringTimer = null
    this.maps = emptyMaps()
    this.tombstones = emptySets()
    this.seen = emptySets()
    this.serverLoaded.clear()
    this.settings = DEFAULT_CURRENCY
    this.ready = false
    this.error = null
    this.pending = 0
    this.db = null
    this.uid = null
    this.changed()
  }

  private async listen(name: CollectionName) {
    const col = collection(this.db!, 'users', this.uid!, name)
    let newest = 0
    try {
      const cached = await getDocsFromCache(col)
      cached.forEach(d => {
        this.apply(name, d)
        newest = Math.max(newest, updatedMillis(d))
      })
    } catch {
      // Nothing cached yet: the listener below downloads everything.
    }
    const changes = query(col, where('updatedAt', '>=', Timestamp.fromMillis(newest)))
    this.unsubs.push(
      onSnapshot(changes, { includeMetadataChanges: true }, snap => {
        snap.docChanges().forEach(change => this.apply(name, change.doc))
        if (!snap.metadata.fromCache && !this.serverLoaded.has(name)) {
          this.serverLoaded.add(name)
          this.afterServerLoad()
        }
        this.changed()
      }, err => this.fail(err)),
    )
  }

  private apply(name: CollectionName, d: DocumentSnapshot) {
    const data = d.data({ serverTimestamps: 'estimate' })
    if (!data) return
    this.seen[name].add(d.id)
    if (data.deleted === true) {
      this.maps[name].delete(d.id)
      this.tombstones[name].add(d.id)
    } else {
      ;(this.maps[name] as Map<string, unknown>).set(d.id, parsers[name](d.id, data))
      this.tombstones[name].delete(d.id)
    }
  }

  private afterServerLoad() {
    const s = this.serverLoaded
    // A brand-new user (nothing on any device yet) gets the starter records.
    if (s.has('accounts') && s.has('categories') && this.seen.accounts.size === 0 && this.seen.categories.size === 0) {
      this.putMany([
        ...DEFAULT_ACCOUNTS.map(a => ['accounts', a] as const),
        ...DEFAULT_CATEGORIES.map(c => ['categories', c] as const),
      ])
    }
    // Only once everything is in, or this tab could re-create an occurrence the phone already edited.
    if (s.size === COLLECTIONS.length) this.processRecurring()
  }

  private fail(err: unknown) {
    this.error = err instanceof Error ? err.message : String(err)
    this.changed()
  }

  private ref(name: CollectionName | 'settings', id?: string) {
    if (!this.db || !this.uid) throw new Error('Not signed in')
    return name === 'settings' ? doc(this.db, 'users', this.uid) : doc(this.db, 'users', this.uid, name, id!)
  }

  private track(write: Promise<unknown>) {
    this.pending++
    this.changed()
    write
      .catch(err => this.fail(err))
      .finally(() => {
        this.pending--
        this.changed()
      })
  }

  // ---- Writes -------------------------------------------------------------

  put<K extends CollectionName>(name: K, item: Collections[K]) {
    this.putMany([[name, item]])
  }

  putMany(items: ReadonlyArray<readonly [CollectionName, Collections[CollectionName]]>) {
    if (items.length === 0) return
    for (const [name, item] of items) {
      ;(this.maps[name] as Map<string, unknown>).set(item.id, item)
      this.tombstones[name].delete(item.id)
      this.seen[name].add(item.id)
    }
    this.changed()
    // Firestore batches hold at most 500 writes.
    for (let i = 0; i < items.length; i += 450) {
      const batch = writeBatch(this.db!)
      for (const [name, item] of items.slice(i, i + 450)) {
        const { id, ...fields } = item
        batch.set(this.ref(name, id), { ...fields, deleted: false, updatedAt: serverTimestamp() })
      }
      this.track(batch.commit())
    }
  }

  remove(name: CollectionName, id: string) {
    this.maps[name].delete(id)
    this.tombstones[name].add(id)
    this.changed()
    this.track(setDoc(this.ref(name, id), { deleted: true, updatedAt: serverTimestamp() }, { merge: true }))
  }

  setSettings(settings: CurrencySettings) {
    this.settings = settings
    this.changed()
    this.track(setDoc(this.ref('settings'), { ...settings, updatedAt: serverTimestamp() }, { merge: true }))
  }

  /** Same rules as FinRepository.processRecurring on the phone; the ids make it safe to run on both. */
  processRecurring(now = Date.now()) {
    if (!this.db || this.serverLoaded.size !== COLLECTIONS.length) return
    const writes: (readonly [CollectionName, Txn | Recurring])[] = []
    for (const r of this.maps.recurring.values()) {
      if (!r.active) continue
      let generated = r.generated
      let next = occurrence(r.startDate, r.frequency, generated)
      while (next <= now && generated - r.generated < 5000) {
        const id = occurrenceId(r.id, generated)
        if (!this.maps.transactions.has(id) && !this.tombstones.transactions.has(id)) {
          writes.push(['transactions', {
            id, type: r.type, amount: r.amount, categoryId: r.categoryId, accountId: r.accountId,
            toAccountId: r.toAccountId, date: next, note: r.note, recurringId: r.id, createdAt: now,
          }])
        }
        generated++
        next = occurrence(r.startDate, r.frequency, generated)
      }
      if (generated !== r.generated) writes.push(['recurring', { ...r, generated }])
    }
    this.putMany(writes)
  }
}

function updatedMillis(d: DocumentSnapshot): number {
  const value = d.get('updatedAt')
  return value instanceof Timestamp ? value.toMillis() : 0
}

export const store = new DataStore()

export function useData(): DataSnapshot {
  return useSyncExternalStore(store.subscribe, store.getSnapshot)
}
