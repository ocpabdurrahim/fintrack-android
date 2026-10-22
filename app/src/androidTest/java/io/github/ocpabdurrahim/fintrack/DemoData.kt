package io.github.ocpabdurrahim.fintrack

import io.github.ocpabdurrahim.fintrack.data.Settings
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Dates
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

/** Six months of believable, repeatable household data for the screenshot tour. */
object DemoData {
    suspend fun load(c: AppContainer) {
        val repo = c.repository
        repo.resetAll()
        c.settings.update { Settings() }

        val categories = repo.categories.first().associateBy { it.name }
        val accounts = repo.accounts.first().associateBy { it.name }
        repo.saveAccount(accounts.getValue("Cash").copy(openingBalance = 5_000_00))
        repo.saveAccount(accounts.getValue("Bank").copy(openingBalance = 40_000_00))
        repo.saveAccount(accounts.getValue("Mobile Wallet").copy(openingBalance = 2_500_00))
        val cash = accounts.getValue("Cash").id
        val bank = accounts.getValue("Bank").id
        val wallet = accounts.getValue("Mobile Wallet").id

        val random = Random(7)
        val today = LocalDate.now()

        suspend fun add(
            type: TxnType,
            category: String?,
            account: Long,
            taka: Int,
            date: LocalDate,
            hour: Int,
            note: String = "",
            to: Long? = null,
        ) {
            repo.saveTransaction(
                TransactionEntity(
                    type = type,
                    amount = taka * 100L,
                    categoryId = category?.let { categories.getValue(it).id },
                    accountId = account,
                    toAccountId = to,
                    date = Dates.toMillis(date, LocalTime.of(hour, random.nextInt(60))),
                    note = note,
                )
            )
        }

        for (monthsAgo in 5 downTo 0) {
            val month = YearMonth.from(today).minusMonths(monthsAgo.toLong())
            val lastDay = if (monthsAgo == 0) today.dayOfMonth else month.lengthOfMonth()
            fun day(d: Int): LocalDate? = if (d <= lastDay) month.atDay(d) else null

            day(1)?.let { add(TxnType.INCOME, "Salary", bank, 65_000, it, 10, "Salary") }
            day(2)?.let { add(TxnType.EXPENSE, "Rent", bank, 18_000, it, 11, "Flat rent") }
            day(5)?.let { add(TxnType.EXPENSE, "Bills & Utilities", wallet, 2_000 + random.nextInt(900), it, 20, "Electricity and gas") }
            day(7)?.let { add(TxnType.EXPENSE, "Mobile & Internet", wallet, 1_200, it, 9, "Broadband") }
            day(12)?.let { add(TxnType.INCOME, "Freelance", wallet, 8_000 + random.nextInt(9_000), it, 16, "Logo design") }
            for (d in listOf(1, 8, 15, 22)) {
                day(d)?.let { add(TxnType.TRANSFER, null, bank, 6_000, it, 13, "ATM", to = cash) }
            }
            day(20)?.let { add(TxnType.EXPENSE, "Family", bank, 6_000, it, 18, "Sent home") }

            for (d in 1..lastDay) {
                val date = month.atDay(d)
                add(TxnType.EXPENSE, "Food", cash, 120 + random.nextInt(380), date, 13, listOf("Lunch", "Tea and snacks", "Dinner", "").random(random))
                if (random.nextInt(3) == 0) {
                    add(TxnType.EXPENSE, "Transport", cash, 60 + random.nextInt(240), date, 9, listOf("Rickshaw", "Bus", "CNG", "Uber").random(random))
                }
                if (d % 6 == 0) add(TxnType.EXPENSE, "Groceries", cash, 900 + random.nextInt(1_800), date, 19, "Weekly bazar")
                if (d % 9 == 4) add(TxnType.EXPENSE, "Shopping", bank, 1_500 + random.nextInt(3_500), date, 18, "Clothes")
                if (d % 11 == 3) add(TxnType.EXPENSE, "Health", cash, 300 + random.nextInt(900), date, 17, "Pharmacy")
                if (d % 13 == 5) add(TxnType.EXPENSE, "Entertainment", wallet, 400 + random.nextInt(600), date, 21, "Cinema")
            }
        }

        repo.setBudget(0L, 70_000_00)
        repo.setBudget(categories.getValue("Food").id, 9_000_00)
        repo.setBudget(categories.getValue("Transport").id, 3_000_00)
        repo.setBudget(categories.getValue("Shopping").id, 5_000_00)
        repo.setBudget(categories.getValue("Groceries").id, 7_000_00)
    }
}
