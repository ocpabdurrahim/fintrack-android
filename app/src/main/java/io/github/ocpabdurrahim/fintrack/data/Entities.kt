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

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val openingBalance: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** INCOME or EXPENSE. Transfers never have a category. */
    val type: TxnType,
    val emoji: String,
    /** ARGB colour used for the badge and the charts. */
    val color: Int,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("categoryId"), Index("accountId"), Index("toAccountId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long?,
    /** For a transfer this is the account the money leaves. */
    val accountId: Long,
    /** Only set for transfers: the account the money arrives in. */
    val toAccountId: Long? = null,
    /** Epoch millis. */
    val date: Long,
    val note: String = "",
    val recurringId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A monthly spending limit. categoryId 0 is the overall budget. */
@Entity(tableName = "budgets", indices = [Index(value = ["categoryId"], unique = true)])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amount: Long,
)

/**
 * A repeating transaction. Occurrence n falls on startDate + n periods, always
 * counted from the start, so a monthly entry on the 31st does not drift to the
 * 28th after February.
 */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long?,
    val note: String,
    val frequency: Frequency,
    val startDate: Long,
    /** How many occurrences already exist as transactions, the first included. */
    val generated: Int,
    val active: Boolean = true,
)

/** Net movement of one account, from the balance query. */
data class AccountDelta(val accountId: Long, val delta: Long)
