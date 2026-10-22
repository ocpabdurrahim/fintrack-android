# FinTrack sync protocol

The Android app and the web app share one Firestore database. Both follow these
rules; change them in both places at once.

## Where the data lives

```
users/{uid}                      currency settings
users/{uid}/accounts/{id}
users/{uid}/categories/{id}
users/{uid}/transactions/{id}
users/{uid}/budgets/{id}
users/{uid}/recurring/{id}
```

`uid` is the Firebase Auth user (Google sign-in). `firestore.rules` lets a user
read and write only their own `users/{uid}` tree.

## Document fields

Every document in the five collections has:

| Field | Type | Meaning |
|---|---|---|
| `updatedAt` | timestamp | Set to the **server time** (`serverTimestamp()`) on every write |
| `deleted` | boolean | `true` = tombstone: the record was deleted on some device |

Money is an integer number of minor units (paisa/cents). Dates are integer epoch
milliseconds. Missing optional references are `null`.

| Collection | Fields |
|---|---|
| accounts | `name`, `emoji`, `openingBalance`, `archived`, `sortOrder` |
| categories | `name`, `type` (`INCOME`/`EXPENSE`), `emoji`, `color` (signed 32-bit ARGB), `archived`, `sortOrder` |
| transactions | `type` (`INCOME`/`EXPENSE`/`TRANSFER`), `amount`, `categoryId`, `accountId`, `toAccountId`, `date`, `note`, `recurringId`, `createdAt` |
| budgets | `categoryId` (a category id or `overall`), `amount` |
| recurring | `type`, `amount`, `categoryId`, `accountId`, `toAccountId`, `note`, `frequency` (`DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY`), `startDate`, `generated`, `active` |

`users/{uid}` holds `currencyCode`, `currencySymbol` and `grouping`
(`SOUTH_ASIAN`/`INTERNATIONAL`). Theme, PIN lock and reminders are per device and
are not synced.

## Ids

Ids are strings. New records get a random 20-character id. Some ids are fixed, so
two devices that create the same record on their own end up with one copy:

- starter accounts and categories: `acc_cash`, `cat_food`, … (see
  `DefaultData.kt` / `defaults.ts`);
- budgets: `budget-{categoryId}`;
- recurring occurrences: `{recurringId}_{n}`, where occurrence `n` falls on
  `startDate + n × frequency`, counted from the start in local time.

## Reading

A client remembers the newest `updatedAt` it has applied. It then listens to
`where('updatedAt', '>=', newest)`, so it only downloads what changed. Applying a
document is idempotent:

- `deleted: true`: remove the record;
- otherwise, insert or replace it.

On the phone, a local row with unsent changes (`dirty`) is not overwritten. Its
own upload will follow and win.

## Writing

Writes are whole documents with `deleted: false` and `updatedAt: serverTimestamp()`.
A delete writes `{deleted: true, updatedAt: serverTimestamp()}`. Documents are
never removed from Firestore.

The last write to reach the server wins.

## First sync on a phone

The first time a phone signs in, it downloads everything. For records both sides
have, the cloud copy wins. Records only the phone has are uploaded. A brand-new
account (no accounts or categories anywhere) is seeded with the starter records
by whichever device gets there first. The fixed ids make a double seed harmless.

## Recurring entries

Both apps write due occurrences on their own (the phone at start-up and daily,
the web hourly while open). The fixed occurrence ids make this safe. The web
waits until it has the full server state, so it never re-creates an occurrence
the phone has already edited or deleted.
