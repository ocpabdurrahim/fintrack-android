package io.github.ocpabdurrahim.fintrack.data

import androidx.room.withTransaction
import io.github.ocpabdurrahim.fintrack.domain.Recurrence
import io.github.ocpabdurrahim.fintrack.domain.Totals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccountBalance(val account: AccountEntity, val balance: Long)

/** A transaction with its category and accounts resolved, ready to display. */
data class TxnItem(
    val txn: TransactionEntity,
    val category: CategoryEntity?,
    val account: AccountEntity?,
    val toAccount: AccountEntity?,
)

enum class RemoveResult { DELETED, ARCHIVED, REFUSED }

private class Lookup(
    private val accounts: Map<String, AccountEntity>,
    private val categories: Map<String, CategoryEntity>,
) {
    fun item(t: TransactionEntity) = TxnItem(
        txn = t,
        category = t.categoryId?.let { categories[it] },
        account = accounts[t.accountId],
        toAccount = t.toAccountId?.let { accounts[it] },
    )
}

/**
 * The single gateway to the database. Screens read Flows and call the suspend
 * writers. Every write stamps the row (updatedAt = now, dirty = true) so the
 * sync engine knows to upload it; deletes leave a tombstone for the same reason.
 */
class FinRepository(private val db: FinDatabase) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val txnDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val recurringDao = db.recurringDao()

    /** Serialises seeding, resets, restores and recurring catch-up so they never interleave. */
    private val writeLock = Mutex()

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val budgets: Flow<List<BudgetEntity>> = budgetDao.observeAll()
    val recurring: Flow<List<RecurringEntity>> = recurringDao.observeAll()

    private val lookup: Flow<Lookup> = combine(accounts, categories) { a, c ->
        Lookup(a.associateBy { it.id }, c.associateBy { it.id })
    }

    val accountBalances: Flow<List<AccountBalance>> =
        combine(accounts, txnDao.observeAccountDeltas()) { list, deltas ->
            val byAccount = deltas.associate { it.accountId to it.delta }
            list.map { AccountBalance(it, it.openingBalance + (byAccount[it.id] ?: 0L)) }
        }

    fun rawBetween(start: Long, end: Long): Flow<List<TransactionEntity>> = txnDao.observeBetween(start, end)

    fun transactionsBetween(start: Long, end: Long): Flow<List<TxnItem>> =
        combine(txnDao.observeBetween(start, end), lookup) { list, lk -> list.map { lk.item(it) } }

    fun recent(limit: Int): Flow<List<TxnItem>> =
        combine(txnDao.observeRecent(limit), lookup) { list, lk -> list.map { lk.item(it) } }

    fun search(query: String): Flow<List<TxnItem>> =
        combine(txnDao.search(query), lookup) { list, lk -> list.map { lk.item(it) } }

    suspend fun totalsBetween(start: Long, end: Long): Totals = Totals.of(txnDao.getBetween(start, end))

    private fun now() = System.currentTimeMillis()

    // ---- Transactions -------------------------------------------------------

    suspend fun getTransaction(id: String): TransactionEntity? = txnDao.get(id)?.takeUnless { it.deleted }

    /** Inserts or updates. A new transaction with [repeat] also starts a recurring schedule. */
    suspend fun saveTransaction(txn: TransactionEntity, repeat: Frequency? = null): String =
        db.withTransaction {
            val now = now()
            if (repeat != null && txnDao.get(txn.id) == null) {
                val schedule = RecurringEntity(
                    type = txn.type,
                    amount = txn.amount,
                    categoryId = txn.categoryId,
                    accountId = txn.accountId,
                    toAccountId = txn.toAccountId,
                    note = txn.note,
                    frequency = repeat,
                    startDate = txn.date,
                    generated = 1,
                    updatedAt = now,
                    dirty = true,
                )
                recurringDao.upsert(schedule)
                // This transaction is occurrence 0 of the new schedule.
                val first = txn.copy(id = Ids.occurrence(schedule.id, 0), recurringId = schedule.id, updatedAt = now, dirty = true)
                txnDao.upsert(first)
                first.id
            } else {
                txnDao.upsert(txn.copy(updatedAt = now, dirty = true, deleted = false))
                txn.id
            }
        }

    suspend fun deleteTransaction(id: String) = txnDao.softDelete(id, now())

    // ---- Accounts -----------------------------------------------------------

    suspend fun saveAccount(account: AccountEntity) {
        val isNew = accountDao.get(account.id)?.takeUnless { it.deleted } == null
        val sortOrder = if (isNew) accountDao.maxSort() + 1 else account.sortOrder
        accountDao.upsert(account.copy(sortOrder = sortOrder, updatedAt = now(), dirty = true, deleted = false))
    }

    /** Unused accounts are deleted; ones with history are archived so old records stay intact. */
    suspend fun removeAccount(account: AccountEntity): RemoveResult = db.withTransaction {
        val active = accountDao.getAll().count { !it.archived }
        if (!account.archived && active <= 1) return@withTransaction RemoveResult.REFUSED
        val now = now()
        if (txnDao.countForAccount(account.id) == 0 && recurringDao.countForAccount(account.id) == 0) {
            accountDao.upsert(account.copy(deleted = true, updatedAt = now, dirty = true))
            RemoveResult.DELETED
        } else {
            accountDao.upsert(account.copy(archived = true, updatedAt = now, dirty = true))
            RemoveResult.ARCHIVED
        }
    }

    // ---- Categories ---------------------------------------------------------

    /** Returns the category's id. */
    suspend fun saveCategory(category: CategoryEntity): String {
        val isNew = categoryDao.get(category.id)?.takeUnless { it.deleted } == null
        val sortOrder = if (isNew) categoryDao.maxSort(category.type.name) + 1 else category.sortOrder
        categoryDao.upsert(category.copy(sortOrder = sortOrder, updatedAt = now(), dirty = true, deleted = false))
        return category.id
    }

    suspend fun removeCategory(category: CategoryEntity): RemoveResult = db.withTransaction {
        val activeOfType = categoryDao.getAll().count { !it.archived && it.type == category.type }
        if (!category.archived && activeOfType <= 1) return@withTransaction RemoveResult.REFUSED
        val now = now()
        budgetDao.softDeleteForCategory(category.id, now)
        if (txnDao.countForCategory(category.id) == 0 && recurringDao.countForCategory(category.id) == 0) {
            categoryDao.upsert(category.copy(deleted = true, updatedAt = now, dirty = true))
            RemoveResult.DELETED
        } else {
            categoryDao.upsert(category.copy(archived = true, updatedAt = now, dirty = true))
            RemoveResult.ARCHIVED
        }
    }

    // ---- Budgets ------------------------------------------------------------

    /** [categoryId] is a category id or [BudgetEntity.OVERALL]. */
    suspend fun setBudget(categoryId: String, amount: Long) = budgetDao.upsert(
        BudgetEntity(id = Ids.budget(categoryId), categoryId = categoryId, amount = amount, updatedAt = now(), dirty = true)
    )

    suspend fun removeBudget(categoryId: String) = budgetDao.softDeleteForCategory(categoryId, now())

    // ---- Recurring ----------------------------------------------------------

    /**
     * Writes every occurrence that has come due. Runs at app start, from the
     * daily worker and after a sync. Occurrence ids are fixed, so the phone and
     * the web adding the same rent payment produce one transaction, not two.
     */
    suspend fun processRecurring(now: Long): Int = writeLock.withLock {
        db.withTransaction {
            var created = 0
            for (r in recurringDao.getActive()) {
                var generated = r.generated
                var next = Recurrence.occurrence(r.startDate, r.frequency, generated)
                while (next <= now && created < MAX_CATCH_UP) {
                    val inserted = txnDao.insertIfAbsent(
                        TransactionEntity(
                            id = Ids.occurrence(r.id, generated),
                            type = r.type,
                            amount = r.amount,
                            categoryId = r.categoryId,
                            accountId = r.accountId,
                            toAccountId = r.toAccountId,
                            date = next,
                            note = r.note,
                            recurringId = r.id,
                            updatedAt = now,
                            dirty = true,
                        )
                    )
                    if (inserted != -1L) created++
                    generated++
                    next = Recurrence.occurrence(r.startDate, r.frequency, generated)
                }
                if (generated != r.generated) {
                    recurringDao.upsert(r.copy(generated = generated, updatedAt = now, dirty = true))
                }
            }
            created
        }
    }

    /** Pausing stops new entries; resuming skips what was missed instead of back-filling it. */
    suspend fun setRecurringActive(r: RecurringEntity, active: Boolean, now: Long = now()) {
        var generated = r.generated
        if (active) {
            while (Recurrence.occurrence(r.startDate, r.frequency, generated) <= now && generated - r.generated < MAX_CATCH_UP) {
                generated++
            }
        }
        recurringDao.upsert(r.copy(active = active, generated = generated, updatedAt = now, dirty = true))
    }

    /** Stops the schedule. Entries it already wrote stay. */
    suspend fun deleteRecurring(r: RecurringEntity) =
        recurringDao.upsert(r.copy(deleted = true, updatedAt = now(), dirty = true))

    // ---- Whole-database operations -----------------------------------------

    suspend fun ensureSeeded() {
        writeLock.withLock {
            db.withTransaction { seedIfEmpty() }
        }
    }

    private suspend fun seedIfEmpty() {
        if (accountDao.count() == 0 && categoryDao.count() == 0) {
            // Not dirty: the first sync uploads them only if the cloud is empty.
            accountDao.upsertAll(DefaultData.accounts())
            categoryDao.upsertAll(DefaultData.categories())
        }
    }

    /** Erases everything (on every synced device) and puts the starter accounts and categories back. */
    suspend fun resetAll() {
        writeLock.withLock {
            db.withTransaction {
                val now = now()
                softDeleteEverything(now)
                accountDao.upsertAll(DefaultData.accounts().map { it.copy(updatedAt = now, dirty = true) })
                categoryDao.upsertAll(DefaultData.categories().map { it.copy(updatedAt = now, dirty = true) })
            }
        }
    }

    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            accounts = accountDao.getAll(),
            categories = categoryDao.getAll(),
            transactions = txnDao.getAll(),
            budgets = budgetDao.getAll(),
            recurring = recurringDao.getAll(),
        )
    }

    /** Restore: replaces all data in one transaction, so a bad file changes nothing. */
    suspend fun replaceAll(data: BackupData) {
        writeLock.withLock {
            db.withTransaction {
                val now = now()
                softDeleteEverything(now)
                accountDao.upsertAll(data.accounts.map { it.copy(updatedAt = now, dirty = true, deleted = false) })
                categoryDao.upsertAll(data.categories.map { it.copy(updatedAt = now, dirty = true, deleted = false) })
                txnDao.upsertAll(data.transactions.map { it.copy(updatedAt = now, dirty = true, deleted = false) })
                budgetDao.upsertAll(data.budgets.map { it.copy(updatedAt = now, dirty = true, deleted = false) })
                recurringDao.upsertAll(data.recurring.map { it.copy(updatedAt = now, dirty = true, deleted = false) })
            }
        }
    }

    private suspend fun softDeleteEverything(now: Long) {
        txnDao.softDeleteAll(now)
        recurringDao.softDeleteAll(now)
        budgetDao.softDeleteAll(now)
        categoryDao.softDeleteAll(now)
        accountDao.softDeleteAll(now)
    }

    private companion object {
        const val MAX_CATCH_UP = 5000
    }
}
