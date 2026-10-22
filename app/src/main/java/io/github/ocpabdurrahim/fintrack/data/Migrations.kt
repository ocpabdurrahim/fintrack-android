package io.github.ocpabdurrahim.fintrack.data

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Turns 1.0.0's numeric ids into string ids, consistently within one database
 * or backup file. Starter accounts/categories get their fixed ids back (found
 * by their original name), so they line up with the same records elsewhere.
 */
class LegacyIdMapper {
    private val accounts = HashMap<Long, String>()
    private val categories = HashMap<Long, String>()
    private val recurring = HashMap<Long, String>()
    private val taken = HashSet<String>()

    fun account(old: Long, name: String): String = accounts.getOrPut(old) { claim(DefaultData.accountIdFor(name)) }

    fun category(old: Long, type: TxnType, name: String): String =
        categories.getOrPut(old) { claim(DefaultData.categoryIdFor(type, name)) }

    fun recurring(old: Long): String = recurring.getOrPut(old) { Ids.newId() }

    // References to rows that no longer exist keep a stable, recognisable id.
    fun accountRef(old: Long): String = accounts[old] ?: "legacy-account-$old"
    fun categoryRef(old: Long?): String? = old?.let { categories[it] ?: "legacy-category-$it" }
    fun recurringRef(old: Long?): String? = old?.let { recurring[it] ?: "legacy-recurring-$it" }

    /** 1.0.0 stored the overall budget as category 0. */
    fun budgetCategoryRef(old: Long): String = if (old == 0L) BudgetEntity.OVERALL else categoryRef(old)!!

    private fun claim(preferred: String?): String =
        if (preferred != null && taken.add(preferred)) preferred else Ids.newId()
}

object Migrations {

    /** The version-2 tables, exactly as Room creates them. */
    private val V2_SCHEMA = listOf(
        "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, " +
            "`openingBalance` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
            "`emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`categoryId` TEXT, `accountId` TEXT NOT NULL, `toAccountId` TEXT, `date` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
            "`recurringId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, " +
            "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_toAccountId` ON `transactions` (`toAccountId`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_dirty` ON `transactions` (`dirty`)",
        "CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `recurring` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`categoryId` TEXT, `accountId` TEXT NOT NULL, `toAccountId` TEXT, `note` TEXT NOT NULL, `frequency` TEXT NOT NULL, " +
            "`startDate` INTEGER NOT NULL, `generated` INTEGER NOT NULL, `active` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )

    /** 1.0.0 -> 1.1.0: numeric ids become string ids, and the sync columns appear. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToStringIds(db, System.currentTimeMillis())
    }

    fun migrateToStringIds(db: SupportSQLiteDatabase, now: Long) {
        // Old indexes keep their names when a table is renamed; drop them first.
        listOf(
            "index_transactions_date", "index_transactions_categoryId", "index_transactions_accountId",
            "index_transactions_toAccountId", "index_budgets_categoryId",
        ).forEach { db.execSQL("DROP INDEX IF EXISTS `$it`") }
        val tables = listOf("accounts", "categories", "transactions", "budgets", "recurring")
        tables.forEach { db.execSQL("ALTER TABLE `$it` RENAME TO `${it}_v1`") }
        V2_SCHEMA.forEach { db.execSQL(it) }

        val ids = LegacyIdMapper()
        // Every migrated row is "dirty": the first sync uploads whatever the cloud doesn't already have.
        db.query("SELECT id, name, emoji, openingBalance, archived, sortOrder FROM accounts_v1 ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val id = ids.account(c.getLong(0), c.getString(1))
                db.execSQL(
                    "INSERT INTO accounts (id, name, emoji, openingBalance, archived, sortOrder, updatedAt, dirty, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0)",
                    arrayOf<Any?>(id, c.getString(1), c.getString(2), c.getLong(3), c.getInt(4), c.getInt(5), now),
                )
            }
        }
        db.query("SELECT id, name, type, emoji, color, archived, sortOrder FROM categories_v1 ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val type = txnType(c.getString(2))
                val id = ids.category(c.getLong(0), type, c.getString(1))
                db.execSQL(
                    "INSERT INTO categories (id, name, type, emoji, color, archived, sortOrder, updatedAt, dirty, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)",
                    arrayOf<Any?>(id, c.getString(1), type.name, c.getString(3), c.getInt(4), c.getInt(5), c.getInt(6), now),
                )
            }
        }
        db.query(
            "SELECT id, type, amount, categoryId, accountId, toAccountId, note, frequency, startDate, generated, active " +
                "FROM recurring_v1 ORDER BY id"
        ).use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO recurring (id, type, amount, categoryId, accountId, toAccountId, note, frequency, startDate, " +
                        "generated, active, updatedAt, dirty, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)",
                    arrayOf<Any?>(
                        ids.recurring(c.getLong(0)), c.getString(1), c.getLong(2), ids.categoryRef(c.longOrNull(3)),
                        ids.accountRef(c.getLong(4)), c.longOrNull(5)?.let(ids::accountRef), c.getString(6), c.getString(7),
                        c.getLong(8), c.getInt(9), c.getInt(10), now,
                    ),
                )
            }
        }
        db.query(
            "SELECT type, amount, categoryId, accountId, toAccountId, date, note, recurringId, createdAt FROM transactions_v1"
        ).use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO transactions (id, type, amount, categoryId, accountId, toAccountId, date, note, recurringId, " +
                        "createdAt, updatedAt, dirty, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)",
                    arrayOf<Any?>(
                        Ids.newId(), c.getString(0), c.getLong(1), ids.categoryRef(c.longOrNull(2)), ids.accountRef(c.getLong(3)),
                        c.longOrNull(4)?.let(ids::accountRef), c.getLong(5), c.getString(6), ids.recurringRef(c.longOrNull(7)),
                        c.getLong(8), now,
                    ),
                )
            }
        }
        db.query("SELECT categoryId, amount FROM budgets_v1").use { c ->
            while (c.moveToNext()) {
                val categoryId = ids.budgetCategoryRef(c.getLong(0))
                db.execSQL(
                    "INSERT OR REPLACE INTO budgets (id, categoryId, amount, updatedAt, dirty, deleted) VALUES (?, ?, ?, ?, 1, 0)",
                    arrayOf<Any?>(Ids.budget(categoryId), categoryId, c.getLong(1), now),
                )
            }
        }
        tables.forEach { db.execSQL("DROP TABLE `${it}_v1`") }
    }

    private fun txnType(name: String): TxnType = TxnType.entries.firstOrNull { it.name == name } ?: TxnType.EXPENSE

    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
}
