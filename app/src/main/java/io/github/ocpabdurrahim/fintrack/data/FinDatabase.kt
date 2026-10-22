package io.github.ocpabdurrahim.fintrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
    ],
    version = 2,
    // No schema export: debug and release builds race on the same file. MigrationTest
    // checks the upgrade from version 1 against the live schema instead.
    exportSchema = false,
)
abstract class FinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "fintrack.db"

        fun build(context: Context, name: String = NAME): FinDatabase =
            Room.databaseBuilder(context, FinDatabase::class.java, name)
                .addMigrations(Migrations.MIGRATION_1_2)
                .build()
    }
}
