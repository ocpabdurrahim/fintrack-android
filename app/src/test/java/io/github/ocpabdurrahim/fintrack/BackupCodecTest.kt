package io.github.ocpabdurrahim.fintrack

import io.github.ocpabdurrahim.fintrack.data.AccountEntity
import io.github.ocpabdurrahim.fintrack.data.BackupCodec
import io.github.ocpabdurrahim.fintrack.data.BackupData
import io.github.ocpabdurrahim.fintrack.data.BudgetEntity
import io.github.ocpabdurrahim.fintrack.data.CategoryEntity
import io.github.ocpabdurrahim.fintrack.data.CsvExporter
import io.github.ocpabdurrahim.fintrack.data.Frequency
import io.github.ocpabdurrahim.fintrack.data.RecurringEntity
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Grouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneOffset

class BackupCodecTest {
    private val sample = BackupData(
        accounts = listOf(
            AccountEntity(id = 1, name = "Cash", emoji = "💵", openingBalance = 50000, sortOrder = 1),
            AccountEntity(id = 2, name = "bKash", emoji = "📱", openingBalance = -1500, archived = true, sortOrder = 2),
        ),
        categories = listOf(
            CategoryEntity(id = 5, name = "Food", type = TxnType.EXPENSE, emoji = "🍔", color = 0xFFFFB74D.toInt(), sortOrder = 1),
            CategoryEntity(id = 6, name = "Salary", type = TxnType.INCOME, emoji = "💼", color = 0xFF66BB6A.toInt()),
        ),
        transactions = listOf(
            TransactionEntity(id = 10, type = TxnType.EXPENSE, amount = 25050, categoryId = 5, accountId = 1, date = 1_760_000_000_000, note = "বাজার, \"fish\"", createdAt = 1),
            TransactionEntity(id = 11, type = TxnType.TRANSFER, amount = 100000, categoryId = null, accountId = 1, toAccountId = 2, date = 1_760_100_000_000, createdAt = 2),
            TransactionEntity(id = 12, type = TxnType.INCOME, amount = 6500000, categoryId = 6, accountId = 1, date = 1_760_200_000_000, recurringId = 3, createdAt = 3),
        ),
        budgets = listOf(BudgetEntity(id = 1, categoryId = 0, amount = 4500000), BudgetEntity(id = 2, categoryId = 5, amount = 900000)),
        recurring = listOf(
            RecurringEntity(id = 3, type = TxnType.INCOME, amount = 6500000, categoryId = 6, accountId = 1, toAccountId = null, note = "", frequency = Frequency.MONTHLY, startDate = 1_760_200_000_000, generated = 2, active = false),
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
    fun rejectsFilesThatAreNotBackups() {
        expectError("This file is not a FinTrack backup.") { BackupCodec.decode("not json at all") }
        expectError("This file is not a FinTrack backup.") { BackupCodec.decode("{\"hello\": 1}") }
    }

    @Test
    fun rejectsBackupsFromNewerVersions() {
        val newer = BackupCodec.encode(sample).replace("\"version\": 1", "\"version\": 99")
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
