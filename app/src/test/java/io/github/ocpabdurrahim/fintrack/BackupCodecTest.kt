package io.github.ocpabdurrahim.fintrack

import io.github.ocpabdurrahim.fintrack.data.AccountEntity
import io.github.ocpabdurrahim.fintrack.data.BackupCodec
import io.github.ocpabdurrahim.fintrack.data.BackupData
import io.github.ocpabdurrahim.fintrack.data.BudgetEntity
import io.github.ocpabdurrahim.fintrack.data.CategoryEntity
import io.github.ocpabdurrahim.fintrack.data.CsvExporter
import io.github.ocpabdurrahim.fintrack.data.Frequency
import io.github.ocpabdurrahim.fintrack.data.Ids
import io.github.ocpabdurrahim.fintrack.data.RecurringEntity
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Grouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneOffset

class BackupCodecTest {
    private val sample = BackupData(
        accounts = listOf(
            AccountEntity(id = "acc_cash", name = "Cash", emoji = "💵", openingBalance = 50000, sortOrder = 1),
            AccountEntity(id = "k9Xa", name = "bKash", emoji = "📱", openingBalance = -1500, archived = true, sortOrder = 2),
        ),
        categories = listOf(
            CategoryEntity(id = "cat_food", name = "Food", type = TxnType.EXPENSE, emoji = "🍔", color = 0xFFFFB74D.toInt(), sortOrder = 1),
            CategoryEntity(id = "cat_salary", name = "Salary", type = TxnType.INCOME, emoji = "💼", color = 0xFF66BB6A.toInt()),
        ),
        transactions = listOf(
            TransactionEntity(id = "t1", type = TxnType.EXPENSE, amount = 25050, categoryId = "cat_food", accountId = "acc_cash", date = 1_760_000_000_000, note = "বাজার, \"fish\"", createdAt = 1),
            TransactionEntity(id = "t2", type = TxnType.TRANSFER, amount = 100000, categoryId = null, accountId = "acc_cash", toAccountId = "k9Xa", date = 1_760_100_000_000, createdAt = 2),
            TransactionEntity(id = "r1_0", type = TxnType.INCOME, amount = 6500000, categoryId = "cat_salary", accountId = "acc_cash", date = 1_760_200_000_000, recurringId = "r1", createdAt = 3),
        ),
        budgets = listOf(
            BudgetEntity(id = Ids.budget(BudgetEntity.OVERALL), categoryId = BudgetEntity.OVERALL, amount = 4500000),
            BudgetEntity(id = Ids.budget("cat_food"), categoryId = "cat_food", amount = 900000),
        ),
        recurring = listOf(
            RecurringEntity(id = "r1", type = TxnType.INCOME, amount = 6500000, categoryId = "cat_salary", accountId = "acc_cash", toAccountId = null, note = "", frequency = Frequency.MONTHLY, startDate = 1_760_200_000_000, generated = 2, active = false),
        ),
        currencyCode = "BDT",
        currencySymbol = "৳",
        grouping = Grouping.SOUTH_ASIAN,
    )

    @Test
    fun roundTripKeepsEverything() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample, exportedAt = 42))
        assertEquals(sample, decoded)
    }

    @Test
    fun restoresVersion1BackupsWithNumericIds() {
        val v1 = """
            {"format": "fintrack-backup", "version": 1, "currencySymbol": "৳",
             "accounts": [
               {"id": 1, "name": "Cash", "emoji": "💵", "openingBalance": 0, "archived": false, "sortOrder": 1},
               {"id": 2, "name": "bKash", "emoji": "📱", "openingBalance": 500, "archived": false, "sortOrder": 2}],
             "categories": [
               {"id": 5, "name": "Food", "type": "EXPENSE", "emoji": "🍔", "color": -18611, "archived": false, "sortOrder": 1},
               {"id": 9, "name": "Side gig", "type": "INCOME", "emoji": "💻", "color": -10453621, "archived": false, "sortOrder": 9}],
             "transactions": [
               {"id": 10, "type": "EXPENSE", "amount": 25050, "categoryId": 5, "accountId": 1, "toAccountId": null,
                "date": 1760000000000, "note": "lunch", "recurringId": null, "createdAt": 1},
               {"id": 11, "type": "TRANSFER", "amount": 1000, "categoryId": null, "accountId": 1, "toAccountId": 2,
                "date": 1760000000001, "note": "", "recurringId": null, "createdAt": 2}],
             "budgets": [{"id": 1, "categoryId": 0, "amount": 4500000}, {"id": 2, "categoryId": 5, "amount": 900000}],
             "recurring": []}
        """.trimIndent()
        val d = BackupCodec.decode(v1)

        // Starter records get their fixed ids back; the user's own get fresh ones.
        assertEquals("acc_cash", d.accounts[0].id)
        val bkash = d.accounts[1].id
        assertFalse(bkash.startsWith("acc_"))
        assertEquals("cat_food", d.categories[0].id)
        assertEquals(20, d.categories[1].id.length)

        // References follow the new ids.
        assertEquals("cat_food", d.transactions[0].categoryId)
        assertEquals("acc_cash", d.transactions[0].accountId)
        assertEquals(bkash, d.transactions[1].toAccountId)
        assertEquals(listOf("budget-overall", "budget-cat_food"), d.budgets.map { it.id })
        assertEquals(listOf(BudgetEntity.OVERALL, "cat_food"), d.budgets.map { it.categoryId })
        assertEquals("৳", d.currencySymbol)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        expectError("This file is not a FinTrack backup.") { BackupCodec.decode("not json at all") }
        expectError("This file is not a FinTrack backup.") { BackupCodec.decode("{\"hello\": 1}") }
    }

    @Test
    fun rejectsBackupsFromNewerVersions() {
        val newer = BackupCodec.encode(sample).replace("\"version\": 2", "\"version\": 99")
        expectError("newer version") { BackupCodec.decode(newer) }
    }

    @Test
    fun csvEscapesAndOrdersRows() {
        val csv = CsvExporter.build(sample, ZoneOffset.UTC)
        val lines = csv.removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals("Date,Type,Category,Account,To account,Amount,Note", lines[0])
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("2025-10-09 08:53,Expense,Food,Cash,,250.50,"))
        assertTrue(lines[1].endsWith("\"বাজার, \"\"fish\"\"\""))
        assertTrue(lines[2].contains(",Transfer,,Cash,bKash,1000.00,"))
        assertEquals("plain", CsvExporter.escape("plain"))
    }

    private fun expectError(fragment: String, block: () -> Unit) {
        try {
            block()
            fail("Expected an error containing '$fragment'")
        } catch (e: IllegalArgumentException) {
            assertTrue("'${e.message}' should contain '$fragment'", e.message.orEmpty().contains(fragment))
        }
    }
}
