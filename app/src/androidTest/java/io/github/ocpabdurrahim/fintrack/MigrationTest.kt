package io.github.ocpabdurrahim.fintrack

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ocpabdurrahim.fintrack.data.BudgetEntity
import io.github.ocpabdurrahim.fintrack.data.FinDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a database exactly as FinTrack 1.0.0 left it, then opens it with the
 * current Room schema. Room validates every table after the migration, so a
 * mismatch in the hand-written v2 SQL fails here, not on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @Before
    @After
    fun clean() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1DataSurvivesWithStringIds() {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1_SCHEMA.forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO accounts VALUES (1, 'Cash', '💵', 500000, 0, 1)")
            db.execSQL("INSERT INTO accounts VALUES (2, 'Bank', '🏦', 0, 0, 2)")
            db.execSQL("INSERT INTO accounts VALUES (4, 'bKash', '📱', 0, 0, 4)")
            db.execSQL("INSERT INTO categories VALUES (1, 'Food', 'EXPENSE', '🍔', -18611, 0, 1)")
            db.execSQL("INSERT INTO categories VALUES (16, 'Salary', 'INCOME', '💼', -10044566, 0, 1)")
            db.execSQL("INSERT INTO categories VALUES (30, 'Pets', 'EXPENSE', '🐾', -11, 0, 16)")
            db.execSQL("INSERT INTO recurring VALUES (1, 'INCOME', 6500000, 16, 2, NULL, 'Salary', 'MONTHLY', 1760000000000, 2, 1)")
            db.execSQL("INSERT INTO transactions VALUES (1, 'EXPENSE', 25000, 1, 1, NULL, 1760000000000, 'Lunch', NULL, 1)")
            db.execSQL("INSERT INTO transactions VALUES (2, 'EXPENSE', 9000, 30, 4, NULL, 1760000000001, '', NULL, 2)")
            db.execSQL("INSERT INTO transactions VALUES (3, 'TRANSFER', 100000, NULL, 2, 1, 1760000000002, 'ATM', NULL, 3)")
            db.execSQL("INSERT INTO transactions VALUES (4, 'INCOME', 6500000, 16, 2, NULL, 1760000000003, 'Salary', 1, 4)")
            db.execSQL("INSERT INTO budgets VALUES (1, 0, 4500000)")
            db.execSQL("INSERT INTO budgets VALUES (2, 1, 900000)")
            db.version = 1
        }

        val room = FinDatabase.build(context, name)
        try {
            runBlocking {
                val accounts = room.accountDao().getAll().associateBy { it.name }
                assertEquals("acc_cash", accounts.getValue("Cash").id)
                assertEquals("acc_bank", accounts.getValue("Bank").id)
                val bkash = accounts.getValue("bKash").id
                assertEquals(20, bkash.length)

                val categories = room.categoryDao().getAll().associateBy { it.name }
                assertEquals("cat_food", categories.getValue("Food").id)
                assertEquals("cat_salary", categories.getValue("Salary").id)
                val pets = categories.getValue("Pets").id

                val txns = room.transactionDao().getAll().associateBy { it.note }
                assertEquals(4, txns.size)
                assertEquals("cat_food", txns.getValue("Lunch").categoryId)
                assertEquals(pets, txns.getValue("").categoryId)
                assertEquals(bkash, txns.getValue("").accountId)
                assertEquals("acc_bank", txns.getValue("ATM").accountId)
                assertEquals("acc_cash", txns.getValue("ATM").toAccountId)

                val schedule = room.recurringDao().getAll().single()
                assertEquals(schedule.id, txns.getValue("Salary").recurringId)
                assertEquals("cat_salary", schedule.categoryId)
                assertEquals(2, schedule.generated)

                val budgets = room.budgetDao().getAll().associateBy { it.categoryId }
                assertEquals(4500000L, budgets.getValue(BudgetEntity.OVERALL).amount)
                assertEquals("budget-cat_food", budgets.getValue("cat_food").id)

                // Balances still add up: Cash 5000 - 250 + 1000 = 5750.
                val balances = room.transactionDao().observeAccountDeltas().first().associate { it.accountId to it.delta }
                assertEquals(-25000L + 100000L, balances["acc_cash"])
                // Everything is waiting to be uploaded on the first sync.
                assertTrue(room.transactionDao().dirty().size == 4)
            }
        } finally {
            room.close()
        }
    }

    private companion object {
        // The tables exactly as Room created them in FinTrack 1.0.0.
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `openingBalance` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, " +
                "`date` INTEGER NOT NULL, `note` TEXT NOT NULL, `recurringId` INTEGER, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_toAccountId` ON `transactions` (`toAccountId`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)",
            "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, " +
                "`note` TEXT NOT NULL, `frequency` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `generated` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL)",
        )
    }
}
