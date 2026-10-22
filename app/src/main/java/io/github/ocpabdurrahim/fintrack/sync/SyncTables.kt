package io.github.ocpabdurrahim.fintrack.sync

import com.google.firebase.firestore.FieldValue
import io.github.ocpabdurrahim.fintrack.data.AccountEntity
import io.github.ocpabdurrahim.fintrack.data.BudgetEntity
import io.github.ocpabdurrahim.fintrack.data.CategoryEntity
import io.github.ocpabdurrahim.fintrack.data.FinDatabase
import io.github.ocpabdurrahim.fintrack.data.Frequency
import io.github.ocpabdurrahim.fintrack.data.Ids
import io.github.ocpabdurrahim.fintrack.data.RecurringEntity
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType

/**
 * One synced collection: how its rows become Firestore documents and back.
 * The field names and types are the shared format in docs/sync-protocol.md;
 * the web app reads and writes the same ones.
 */
abstract class SyncTable<T : Any>(val name: String) {
    abstract suspend fun dirty(): List<T>
    abstract suspend fun get(id: String): T?
    abstract fun id(row: T): String
    abstract fun updatedAt(row: T): Long
    abstract fun isDirty(row: T): Boolean
    abstract fun fields(row: T): Map<String, Any?>
    /** A row built from a cloud document; clean, since it matches the cloud. */
    abstract fun fromDoc(id: String, d: Map<String, Any?>, now: Long): T
    abstract suspend fun upsert(row: T)
    abstract suspend fun hardDelete(id: String)
    abstract suspend fun markClean(id: String, updatedAt: Long)
    abstract suspend fun markAllDirty()
    abstract suspend fun purgeTombstones()
    abstract fun isDeleted(row: T): Boolean

    /** The document to upload: the row's fields plus the shared sync fields. */
    fun document(row: T): Map<String, Any?> =
        fields(row) + mapOf("deleted" to isDeleted(row), "updatedAt" to FieldValue.serverTimestamp())

    class Pending(val id: String, val updatedAt: Long, val document: Map<String, Any?>)

    /** Rows changed here and not uploaded yet, ready to send. */
    suspend fun pending(): List<Pending> = dirty().map { Pending(id(it), updatedAt(it), document(it)) }

    /**
     * Applies one cloud document. Unless [force], a row with unsent local changes
     * is left alone (its upload follows and wins). Returns whether it was applied.
     */
    suspend fun applyDocument(id: String, d: Map<String, Any?>, now: Long, force: Boolean): Boolean {
        if (!force) {
            val local = get(id)
            if (local != null && isDirty(local)) return false
        }
        if (d["deleted"] == true) hardDelete(id) else upsert(fromDoc(id, d, now))
        return true
    }
}

private fun Map<String, Any?>.str(key: String, fallback: String = "") = this[key] as? String ?: fallback
private fun Map<String, Any?>.strOrNull(key: String) = this[key] as? String
private fun Map<String, Any?>.long(key: String, fallback: Long = 0L) = (this[key] as? Number)?.toLong() ?: fallback
private fun Map<String, Any?>.int(key: String, fallback: Int = 0) = (this[key] as? Number)?.toInt() ?: fallback
private fun Map<String, Any?>.bool(key: String, fallback: Boolean = false) = this[key] as? Boolean ?: fallback

private fun txnType(value: Any?): TxnType = TxnType.entries.firstOrNull { it.name == value } ?: TxnType.EXPENSE

fun syncTables(db: FinDatabase): List<SyncTable<*>> {
    val accounts = db.accountDao()
    val categories = db.categoryDao()
    val txns = db.transactionDao()
    val budgets = db.budgetDao()
    val recurring = db.recurringDao()

    return listOf(
        object : SyncTable<AccountEntity>("accounts") {
            override suspend fun dirty() = accounts.dirty()
            override suspend fun get(id: String) = accounts.get(id)
            override fun id(row: AccountEntity) = row.id
            override fun updatedAt(row: AccountEntity) = row.updatedAt
            override fun isDirty(row: AccountEntity) = row.dirty
            override fun isDeleted(row: AccountEntity) = row.deleted
            override fun fields(row: AccountEntity) = mapOf(
                "name" to row.name, "emoji" to row.emoji, "openingBalance" to row.openingBalance,
                "archived" to row.archived, "sortOrder" to row.sortOrder,
            )
            override fun fromDoc(id: String, d: Map<String, Any?>, now: Long) = AccountEntity(
                id = id, name = d.str("name"), emoji = d.str("emoji", "💵"), openingBalance = d.long("openingBalance"),
                archived = d.bool("archived"), sortOrder = d.int("sortOrder"), updatedAt = now,
            )
            override suspend fun upsert(row: AccountEntity) = accounts.upsert(row)
            override suspend fun hardDelete(id: String) = accounts.hardDelete(id)
            override suspend fun markClean(id: String, updatedAt: Long) = accounts.markClean(id, updatedAt)
            override suspend fun markAllDirty() = accounts.markAllDirty()
            override suspend fun purgeTombstones() = accounts.purgeTombstones()
        },
        object : SyncTable<CategoryEntity>("categories") {
            override suspend fun dirty() = categories.dirty()
            override suspend fun get(id: String) = categories.get(id)
            override fun id(row: CategoryEntity) = row.id
            override fun updatedAt(row: CategoryEntity) = row.updatedAt
            override fun isDirty(row: CategoryEntity) = row.dirty
            override fun isDeleted(row: CategoryEntity) = row.deleted
            override fun fields(row: CategoryEntity) = mapOf(
                "name" to row.name, "type" to row.type.name, "emoji" to row.emoji, "color" to row.color,
                "archived" to row.archived, "sortOrder" to row.sortOrder,
            )
            override fun fromDoc(id: String, d: Map<String, Any?>, now: Long) = CategoryEntity(
                id = id, name = d.str("name"), type = if (d["type"] == "INCOME") TxnType.INCOME else TxnType.EXPENSE,
                emoji = d.str("emoji", "📦"), color = d.int("color", 0xFF90A4AE.toInt()), archived = d.bool("archived"),
                sortOrder = d.int("sortOrder"), updatedAt = now,
            )
            override suspend fun upsert(row: CategoryEntity) = categories.upsert(row)
            override suspend fun hardDelete(id: String) = categories.hardDelete(id)
            override suspend fun markClean(id: String, updatedAt: Long) = categories.markClean(id, updatedAt)
            override suspend fun markAllDirty() = categories.markAllDirty()
            override suspend fun purgeTombstones() = categories.purgeTombstones()
        },
        object : SyncTable<RecurringEntity>("recurring") {
            override suspend fun dirty() = recurring.dirty()
            override suspend fun get(id: String) = recurring.get(id)
            override fun id(row: RecurringEntity) = row.id
            override fun updatedAt(row: RecurringEntity) = row.updatedAt
            override fun isDirty(row: RecurringEntity) = row.dirty
            override fun isDeleted(row: RecurringEntity) = row.deleted
            override fun fields(row: RecurringEntity) = mapOf(
                "type" to row.type.name, "amount" to row.amount, "categoryId" to row.categoryId,
                "accountId" to row.accountId, "toAccountId" to row.toAccountId, "note" to row.note,
                "frequency" to row.frequency.name, "startDate" to row.startDate, "generated" to row.generated,
                "active" to row.active,
            )
            override fun fromDoc(id: String, d: Map<String, Any?>, now: Long) = RecurringEntity(
                id = id, type = txnType(d["type"]), amount = d.long("amount"), categoryId = d.strOrNull("categoryId"),
                accountId = d.str("accountId"), toAccountId = d.strOrNull("toAccountId"), note = d.str("note"),
                frequency = Frequency.entries.firstOrNull { it.name == d["frequency"] } ?: Frequency.MONTHLY,
                startDate = d.long("startDate"), generated = d.int("generated", 1), active = d.bool("active", true),
                updatedAt = now,
            )
            override suspend fun upsert(row: RecurringEntity) = recurring.upsert(row)
            override suspend fun hardDelete(id: String) = recurring.hardDelete(id)
            override suspend fun markClean(id: String, updatedAt: Long) = recurring.markClean(id, updatedAt)
            override suspend fun markAllDirty() = recurring.markAllDirty()
            override suspend fun purgeTombstones() = recurring.purgeTombstones()
        },
        object : SyncTable<TransactionEntity>("transactions") {
            override suspend fun dirty() = txns.dirty()
            override suspend fun get(id: String) = txns.get(id)
            override fun id(row: TransactionEntity) = row.id
            override fun updatedAt(row: TransactionEntity) = row.updatedAt
            override fun isDirty(row: TransactionEntity) = row.dirty
            override fun isDeleted(row: TransactionEntity) = row.deleted
            override fun fields(row: TransactionEntity) = mapOf(
                "type" to row.type.name, "amount" to row.amount, "categoryId" to row.categoryId,
                "accountId" to row.accountId, "toAccountId" to row.toAccountId, "date" to row.date,
                "note" to row.note, "recurringId" to row.recurringId, "createdAt" to row.createdAt,
            )
            override fun fromDoc(id: String, d: Map<String, Any?>, now: Long) = TransactionEntity(
                id = id, type = txnType(d["type"]), amount = d.long("amount"), categoryId = d.strOrNull("categoryId"),
                accountId = d.str("accountId"), toAccountId = d.strOrNull("toAccountId"), date = d.long("date"),
                note = d.str("note"), recurringId = d.strOrNull("recurringId"),
                createdAt = d.long("createdAt", d.long("date")), updatedAt = now,
            )
            override suspend fun upsert(row: TransactionEntity) = txns.upsert(row)
            override suspend fun hardDelete(id: String) = txns.hardDelete(id)
            override suspend fun markClean(id: String, updatedAt: Long) = txns.markClean(id, updatedAt)
            override suspend fun markAllDirty() = txns.markAllDirty()
            override suspend fun purgeTombstones() = txns.purgeTombstones()
        },
        object : SyncTable<BudgetEntity>("budgets") {
            override suspend fun dirty() = budgets.dirty()
            override suspend fun get(id: String) = budgets.get(id)
            override fun id(row: BudgetEntity) = row.id
            override fun updatedAt(row: BudgetEntity) = row.updatedAt
            override fun isDirty(row: BudgetEntity) = row.dirty
            override fun isDeleted(row: BudgetEntity) = row.deleted
            override fun fields(row: BudgetEntity) = mapOf("categoryId" to row.categoryId, "amount" to row.amount)
            override fun fromDoc(id: String, d: Map<String, Any?>, now: Long): BudgetEntity {
                val categoryId = d.str("categoryId", BudgetEntity.OVERALL)
                return BudgetEntity(id = Ids.budget(categoryId), categoryId = categoryId, amount = d.long("amount"), updatedAt = now)
            }
            override suspend fun upsert(row: BudgetEntity) = budgets.upsert(row)
            override suspend fun hardDelete(id: String) = budgets.hardDelete(id)
            override suspend fun markClean(id: String, updatedAt: Long) = budgets.markClean(id, updatedAt)
            override suspend fun markAllDirty() = budgets.markAllDirty()
            override suspend fun purgeTombstones() = budgets.purgeTombstones()
        },
    )
}
