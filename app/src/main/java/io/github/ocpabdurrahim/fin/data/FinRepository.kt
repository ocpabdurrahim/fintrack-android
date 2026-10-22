package io.github.ocpabdurrahim.fin.data

import androidx.room.withTransaction
import io.github.ocpabdurrahim.fin.domain.Recurrence
import io.github.ocpabdurrahim.fin.domain.Totals
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
    private val accounts: Map<Long, AccountEntity>,
    private val categories: Map<Long, CategoryEntity>,
) {
    fun item(t: TransactionEntity) = TxnItem(
        txn = t,
        category = t.categoryId?.let { categories[it] },
        account = accounts[t.accountId],
        toAccount = t.toAccountId?.let { accounts[it] },
    )
}

/** The single gateway to the database. Screens read Flows and call the suspend writers. */
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

    // ---- Transactions -------------------------------------------------------

    suspend fun getTransaction(id: Long): TransactionEntity? = txnDao.get(id)

    /** Inserts or updates. A new transaction with [repeat] also starts a recurring schedule. */
    suspend fun saveTransaction(txn: TransactionEntity, repeat: Frequency? = null): Long =
        db.withTransaction {
            if (txn.id == 0L) {
                val id = txnDao.insert(txn)
                if (repeat != null) {
                    val recurringId = recurringDao.insert(
                        RecurringEntity(
                            type = txn.type,
                            amount = txn.amount,
                            categoryId = txn.categoryId,
                            accountId = txn.accountId,
                            toAccountId = txn.toAccountId,
                            note = txn.note,
                            frequency = repeat,
                            startDate = txn.date,
                            generated = 1,
                        )
                    )
                    txnDao.update(txn.copy(id = id, recurringId = recurringId))
                }
                id
            } else {
                txnDao.update(txn)
                txn.id
            }
        }

    suspend fun deleteTransaction(id: Long) = txnDao.delete(id)

    // ---- Accounts -----------------------------------------------------------

    suspend fun saveAccount(account: AccountEntity) {
        if (account.id == 0L) accountDao.insert(account.copy(sortOrder = accountDao.maxSort() + 1))
        else accountDao.update(account)
    }

    /** Unused accounts are deleted; ones with history are archived so old records stay intact. */
    suspend fun removeAccount(account: AccountEntity): RemoveResult = db.withTransaction {
        val active = accountDao.getAll().count { !it.archived }
        if (!account.archived && active <= 1) return@withTransaction RemoveResult.REFUSED
        if (txnDao.countForAccount(account.id) == 0 && recurringDao.countForAccount(account.id) == 0) {
            accountDao.delete(account.id)
            RemoveResult.DELETED
        } else {
            accountDao.update(account.copy(archived = true))
            RemoveResult.ARCHIVED
        }
    }

    // ---- Categories ---------------------------------------------------------

    suspend fun saveCategory(category: CategoryEntity): Long =
        if (category.id == 0L) {
            categoryDao.insert(category.copy(sortOrder = categoryDao.maxSort(category.type.name) + 1))
        } else {
            categoryDao.update(category)
            category.id
        }

    suspend fun removeCategory(category: CategoryEntity): RemoveResult = db.withTransaction {
        val activeOfType = categoryDao.getAll().count { !it.archived && it.type == category.type }
        if (!category.archived && activeOfType <= 1) return@withTransaction RemoveResult.REFUSED
        budgetDao.deleteForCategory(category.id)
        if (txnDao.countForCategory(category.id) == 0 && recurringDao.countForCategory(category.id) == 0) {
            categoryDao.delete(category.id)
            RemoveResult.DELETED
        } else {
            categoryDao.update(category.copy(archived = true))
            RemoveResult.ARCHIVED
        }
    }

    // ---- Budgets ------------------------------------------------------------

    suspend fun setBudget(categoryId: Long, amount: Long) =
        budgetDao.upsert(BudgetEntity(categoryId = categoryId, amount = amount))

    suspend fun removeBudget(categoryId: Long) = budgetDao.deleteForCategory(categoryId)

    // ---- Recurring ----------------------------------------------------------

    /**
     * Writes every occurrence that has come due. Runs at app start and from the
     * daily worker, so a monthly rent shows up even if the app was closed that day.
     */
    suspend fun processRecurring(now: Long): Int = writeLock.withLock {
        db.withTransaction {
            var created = 0
            for (r in recurringDao.getActive()) {
                var generated = r.generated
                var next = Recurrence.occurrence(r.startDate, r.frequency, generated)
                while (next <= now && created < MAX_CATCH_UP) {
                    txnDao.insert(
                        TransactionEntity(
                            type = r.type,
                            amount = r.amount,
                            categoryId = r.categoryId,
                            accountId = r.accountId,
                            toAccountId = r.toAccountId,
                            date = next,
                            note = r.note,
                            recurringId = r.id,
                        )
                    )
                    generated++
                    created++
                    next = Recurrence.occurrence(r.startDate, r.frequency, generated)
                }
                if (generated != r.generated) recurringDao.update(r.copy(generated = generated))
            }
            created
        }
    }

    /** Pausing stops new entries; resuming skips what was missed instead of back-filling it. */
    suspend fun setRecurringActive(r: RecurringEntity, active: Boolean, now: Long = System.currentTimeMillis()) {
        var generated = r.generated
        if (active) {
            while (Recurrence.occurrence(r.startDate, r.frequency, generated) <= now && generated - r.generated < MAX_CATCH_UP) {
                generated++
            }
        }
        recurringDao.update(r.copy(active = active, generated = generated))
    }

    /** Stops the schedule. Entries it already wrote stay. */
    suspend fun deleteRecurring(id: Long) = recurringDao.delete(id)

    // ---- Whole-database operations -----------------------------------------

    suspend fun ensureSeeded() {
        writeLock.withLock {
            db.withTransaction { seedIfEmpty() }
        }
    }

    private suspend fun seedIfEmpty() {
        if (accountDao.count() == 0 && categoryDao.count() == 0) seedDefaults()
    }

    /** Erases everything and puts the starter accounts and categories back. */
    suspend fun resetAll() {
        writeLock.withLock {
            db.withTransaction {
                clearAll()
                seedDefaults()
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
                clearAll()
                accountDao.insertAll(data.accounts)
                categoryDao.insertAll(data.categories)
                txnDao.insertAll(data.transactions)
                budgetDao.insertAll(data.budgets)
                recurringDao.insertAll(data.recurring)
            }
        }
    }

    private suspend fun seedDefaults() {
        accountDao.insertAll(DefaultData.accounts())
        categoryDao.insertAll(DefaultData.categories())
    }

    private suspend fun clearAll() {
        txnDao.clear()
        recurringDao.clear()
        budgetDao.clear()
        categoryDao.clear()
        accountDao.clear()
    }

    private companion object {
        const val MAX_CATCH_UP = 5000
    }
}
