package io.github.ocpabdurrahim.fintrack.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnType { INCOME, EXPENSE, TRANSFER }

enum class Frequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
}

// All money is stored as Long minor units (paisa / cents): 1,250.50 is 125050.
// Integer maths keeps totals exact; floating point would drift.
//
// Every table carries three sync columns:
//  - updatedAt: when this phone last changed the row (millis). The uploader
//    clears `dirty` only if the row still has the updatedAt it uploaded.
//  - dirty: changed here and not uploaded yet.
//  - deleted: a tombstone. Hidden everywhere; uploaded so other devices delete
//    it too, then removed.

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val name: String,
    val emoji: String,
    val openingBalance: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val name: String,
    /** INCOME or EXPENSE. Transfers never have a category. */
    val type: TxnType,
    val emoji: String,
    /** ARGB colour used for the badge and the charts. */
    val color: Int,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("categoryId"), Index("accountId"), Index("toAccountId"), Index("dirty")],
)
data class TransactionEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val type: TxnType,
    val amount: Long,
    val categoryId: String?,
    /** For a transfer this is the account the money leaves. */
    val accountId: String,
    /** Only set for transfers: the account the money arrives in. */
    val toAccountId: String? = null,
    /** Epoch millis. */
    val date: Long,
    val note: String = "",
    val recurringId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

/** A monthly spending limit; one per category, plus the overall one. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    /** A category id, or [OVERALL]. */
    val categoryId: String,
    val amount: Long,
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
) {
    companion object {
        const val OVERALL = "overall"
    }
}

/**
 * A repeating transaction. Occurrence n falls on startDate + n periods, always
 * counted from the start, so a monthly entry on the 31st does not drift to the
 * 28th after February. Occurrence n is stored with id [Ids.occurrence].
 */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val type: TxnType,
    val amount: Long,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val note: String,
    val frequency: Frequency,
    val startDate: Long,
    /** How many occurrences already exist as transactions, the first included. */
    val generated: Int,
    val active: Boolean = true,
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

/** Net movement of one account, from the balance query. */
data class AccountDelta(val accountId: String, val delta: Long)
