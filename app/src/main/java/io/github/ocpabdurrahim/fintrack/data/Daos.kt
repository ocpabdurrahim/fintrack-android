package io.github.ocpabdurrahim.fintrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Screens only ever see rows with deleted = 0. The dirty / markClean / purge
// queries exist for the sync engine.

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY archived, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY sortOrder, name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    /** Includes tombstones: a database that has ever held data is not "fresh". */
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM accounts WHERE deleted = 0")
    suspend fun maxSort(): Int

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET deleted = 1, dirty = 1, updatedAt = :now")
    suspend fun softDeleteAll(now: Long)

    @Query("SELECT * FROM accounts WHERE dirty = 1")
    suspend fun dirty(): List<AccountEntity>

    @Query("UPDATE accounts SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE accounts SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM accounts WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeTombstones()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY archived, type, sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY type, sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories WHERE type = :type AND deleted = 0")
    suspend fun maxSort(type: String): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET deleted = 1, dirty = 1, updatedAt = :now")
    suspend fun softDeleteAll(now: Long)

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun dirty(): List<CategoryEntity>

    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE categories SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM categories WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeTombstones()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 AND date >= :start AND date < :end ORDER BY date DESC, createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND date >= :start AND date < :end")
    suspend fun getBetween(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT t.* FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.deleted = 0 AND (t.note LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%')
        ORDER BY t.date DESC, t.createdAt DESC
        LIMIT 500
        """
    )
    fun search(query: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date, createdAt")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    // Income adds to its account; expenses and outgoing transfers subtract;
    // the second half credits the receiving side of each transfer.
    @Query(
        """
        SELECT accountId, SUM(delta) AS delta FROM (
            SELECT accountId AS accountId,
                   CASE WHEN type = 'INCOME' THEN amount ELSE -amount END AS delta
            FROM transactions
            WHERE deleted = 0
            UNION ALL
            SELECT toAccountId AS accountId, amount AS delta
            FROM transactions
            WHERE deleted = 0 AND type = 'TRANSFER' AND toAccountId IS NOT NULL
        ) GROUP BY accountId
        """
    )
    fun observeAccountDeltas(): Flow<List<AccountDelta>>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND (accountId = :accountId OR toAccountId = :accountId)")
    suspend fun countForAccount(accountId: String): Int

    @Upsert
    suspend fun upsert(txn: TransactionEntity)

    @Upsert
    suspend fun upsertAll(txns: List<TransactionEntity>)

    /** For recurring occurrences: never overwrite one that already exists (maybe edited). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(txn: TransactionEntity): Long

    @Query("UPDATE transactions SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE transactions SET deleted = 1, dirty = 1, updatedAt = :now")
    suspend fun softDeleteAll(now: Long)

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun dirty(): List<TransactionEntity>

    @Query("UPDATE transactions SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE transactions SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM transactions WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeTombstones()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: String): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Upsert
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Query("UPDATE budgets SET deleted = 1, dirty = 1, updatedAt = :now WHERE categoryId = :categoryId AND deleted = 0")
    suspend fun softDeleteForCategory(categoryId: String, now: Long)

    @Query("UPDATE budgets SET deleted = 1, dirty = 1, updatedAt = :now")
    suspend fun softDeleteAll(now: Long)

    @Query("SELECT * FROM budgets WHERE dirty = 1")
    suspend fun dirty(): List<BudgetEntity>

    @Query("UPDATE budgets SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE budgets SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM budgets WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeTombstones()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring WHERE deleted = 0 ORDER BY active DESC, startDate")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE deleted = 0 ORDER BY startDate")
    suspend fun getAll(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE deleted = 0 AND active = 1")
    suspend fun getActive(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: String): RecurringEntity?

    @Query("SELECT COUNT(*) FROM recurring WHERE deleted = 0 AND categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM recurring WHERE deleted = 0 AND (accountId = :accountId OR toAccountId = :accountId)")
    suspend fun countForAccount(accountId: String): Int

    @Upsert
    suspend fun upsert(recurring: RecurringEntity)

    @Upsert
    suspend fun upsertAll(recurring: List<RecurringEntity>)

    @Query("UPDATE recurring SET deleted = 1, dirty = 1, updatedAt = :now")
    suspend fun softDeleteAll(now: Long)

    @Query("SELECT * FROM recurring WHERE dirty = 1")
    suspend fun dirty(): List<RecurringEntity>

    @Query("UPDATE recurring SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE recurring SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM recurring WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeTombstones()
}

@Dao
interface SyncDao {
    /** Number of rows waiting to be uploaded; the uploader wakes up when it changes. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM accounts WHERE dirty = 1)
             + (SELECT COUNT(*) FROM categories WHERE dirty = 1)
             + (SELECT COUNT(*) FROM transactions WHERE dirty = 1)
             + (SELECT COUNT(*) FROM budgets WHERE dirty = 1)
             + (SELECT COUNT(*) FROM recurring WHERE dirty = 1)
        """
    )
    fun observePending(): Flow<Int>
}
