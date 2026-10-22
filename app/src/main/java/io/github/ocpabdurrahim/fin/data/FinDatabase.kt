package io.github.ocpabdurrahim.fin.data

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
    version = 1,
    exportSchema = false,
)
abstract class FinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        fun build(context: Context): FinDatabase =
            Room.databaseBuilder(context, FinDatabase::class.java, "fin.db").build()
    }
}
