package io.github.ocpabdurrahim.fintrack.data

import io.github.ocpabdurrahim.fintrack.domain.Grouping
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything a backup file carries. */
data class BackupData(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>,
    val currencyCode: String? = null,
    val currencySymbol: String? = null,
    val grouping: Grouping? = null,
)

/**
 * Plain JSON so a backup stays readable, and restorable by future versions.
 * Version 1 (FinTrack 1.0.0) used numeric ids; version 2 uses string ids.
 * Both can be restored.
 */
object BackupCodec {
    private const val FORMAT = "fintrack-backup"
    const val VERSION = 2

    fun encode(data: BackupData, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
        data.currencyCode?.let { root.put("currencyCode", it) }
        data.currencySymbol?.let { root.put("currencySymbol", it) }
        data.grouping?.let { root.put("grouping", it.name) }

        root.put("accounts", data.accounts.toJsonArray { a ->
            JSONObject()
                .put("id", a.id)
                .put("name", a.name)
                .put("emoji", a.emoji)
                .put("openingBalance", a.openingBalance)
                .put("archived", a.archived)
                .put("sortOrder", a.sortOrder)
        })
        root.put("categories", data.categories.toJsonArray { c ->
            JSONObject()
                .put("id", c.id)
                .put("name", c.name)
                .put("type", c.type.name)
                .put("emoji", c.emoji)
                .put("color", c.color)
                .put("archived", c.archived)
                .put("sortOrder", c.sortOrder)
        })
        root.put("transactions", data.transactions.toJsonArray { t ->
            JSONObject()
                .put("id", t.id)
                .put("type", t.type.name)
                .put("amount", t.amount)
                .put("categoryId", t.categoryId ?: JSONObject.NULL)
                .put("accountId", t.accountId)
                .put("toAccountId", t.toAccountId ?: JSONObject.NULL)
                .put("date", t.date)
                .put("note", t.note)
                .put("recurringId", t.recurringId ?: JSONObject.NULL)
                .put("createdAt", t.createdAt)
        })
        root.put("budgets", data.budgets.toJsonArray { b ->
            JSONObject()
                .put("id", b.id)
                .put("categoryId", b.categoryId)
                .put("amount", b.amount)
        })
        root.put("recurring", data.recurring.toJsonArray { r ->
            JSONObject()
                .put("id", r.id)
                .put("type", r.type.name)
                .put("amount", r.amount)
                .put("categoryId", r.categoryId ?: JSONObject.NULL)
                .put("accountId", r.accountId)
                .put("toAccountId", r.toAccountId ?: JSONObject.NULL)
                .put("note", r.note)
                .put("frequency", r.frequency.name)
                .put("startDate", r.startDate)
                .put("generated", r.generated)
                .put("active", r.active)
        })
        return root.toString(2)
    }

    /** Throws IllegalArgumentException with a message fit for the user when the file is wrong. */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is not a FinTrack backup.")
        }
        require(root.optString("format") in FORMATS) { "This file is not a FinTrack backup." }
        val version = root.optInt("version")
        require(version in 1..VERSION) { "This backup comes from a newer version of FinTrack. Update the app first." }

        try {
            val data = if (version == 1) decodeV1(root) else decodeV2(root)
            return data.copy(
                currencyCode = root.optString("currencyCode").ifEmpty { null },
                currencySymbol = root.optString("currencySymbol").ifEmpty { null },
                grouping = root.optString("grouping").let { g -> Grouping.entries.firstOrNull { it.name == g } },
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("The backup file is damaged: ${e.message}")
        }
    }

    private fun decodeV2(root: JSONObject) = BackupData(
        accounts = root.getJSONArray("accounts").mapObjects { o ->
            AccountEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "💵"),
                openingBalance = o.optLong("openingBalance"),
                archived = o.optBoolean("archived"),
                sortOrder = o.optInt("sortOrder"),
            )
        },
        categories = root.getJSONArray("categories").mapObjects { o ->
            CategoryEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                type = TxnType.valueOf(o.getString("type")),
                emoji = o.optString("emoji", "📦"),
                color = o.optInt("color", DefaultData.colors.last()),
                archived = o.optBoolean("archived"),
                sortOrder = o.optInt("sortOrder"),
            )
        },
        transactions = root.getJSONArray("transactions").mapObjects { o ->
            val date = o.getLong("date")
            TransactionEntity(
                id = o.getString("id"),
                type = TxnType.valueOf(o.getString("type")),
                amount = o.getLong("amount"),
                categoryId = o.stringOrNull("categoryId"),
                accountId = o.getString("accountId"),
                toAccountId = o.stringOrNull("toAccountId"),
                date = date,
                note = o.optString("note"),
                recurringId = o.stringOrNull("recurringId"),
                createdAt = o.optLong("createdAt", date),
            )
        },
        budgets = root.optJSONArray("budgets")?.mapObjects { o ->
            val categoryId = o.getString("categoryId")
            BudgetEntity(id = Ids.budget(categoryId), categoryId = categoryId, amount = o.getLong("amount"))
        }.orEmpty(),
        recurring = root.optJSONArray("recurring")?.mapObjects { o ->
            RecurringEntity(
                id = o.getString("id"),
                type = TxnType.valueOf(o.getString("type")),
                amount = o.getLong("amount"),
                categoryId = o.stringOrNull("categoryId"),
                accountId = o.getString("accountId"),
                toAccountId = o.stringOrNull("toAccountId"),
                note = o.optString("note"),
                frequency = Frequency.valueOf(o.getString("frequency")),
                startDate = o.getLong("startDate"),
                generated = o.getInt("generated"),
                active = o.optBoolean("active", true),
            )
        }.orEmpty(),
    )

    /** FinTrack 1.0.0 files: numeric ids, mapped the same way the database migration maps them. */
    private fun decodeV1(root: JSONObject): BackupData {
        val ids = LegacyIdMapper()
        val accounts = root.getJSONArray("accounts").mapObjects { o ->
            AccountEntity(
                id = ids.account(o.getLong("id"), o.getString("name")),
                name = o.getString("name"),
                emoji = o.optString("emoji", "💵"),
                openingBalance = o.optLong("openingBalance"),
                archived = o.optBoolean("archived"),
                sortOrder = o.optInt("sortOrder"),
            )
        }
        val categories = root.getJSONArray("categories").mapObjects { o ->
            val type = TxnType.valueOf(o.getString("type"))
            CategoryEntity(
                id = ids.category(o.getLong("id"), type, o.getString("name")),
                name = o.getString("name"),
                type = type,
                emoji = o.optString("emoji", "📦"),
                color = o.optInt("color", DefaultData.colors.last()),
                archived = o.optBoolean("archived"),
                sortOrder = o.optInt("sortOrder"),
            )
        }
        val recurring = root.optJSONArray("recurring")?.mapObjects { o ->
            RecurringEntity(
                id = ids.recurring(o.getLong("id")),
                type = TxnType.valueOf(o.getString("type")),
                amount = o.getLong("amount"),
                categoryId = ids.categoryRef(o.longOrNull("categoryId")),
                accountId = ids.accountRef(o.getLong("accountId")),
                toAccountId = o.longOrNull("toAccountId")?.let(ids::accountRef),
                note = o.optString("note"),
                frequency = Frequency.valueOf(o.getString("frequency")),
                startDate = o.getLong("startDate"),
                generated = o.getInt("generated"),
                active = o.optBoolean("active", true),
            )
        }.orEmpty()
        val transactions = root.getJSONArray("transactions").mapObjects { o ->
            val date = o.getLong("date")
            TransactionEntity(
                type = TxnType.valueOf(o.getString("type")),
                amount = o.getLong("amount"),
                categoryId = ids.categoryRef(o.longOrNull("categoryId")),
                accountId = ids.accountRef(o.getLong("accountId")),
                toAccountId = o.longOrNull("toAccountId")?.let(ids::accountRef),
                date = date,
                note = o.optString("note"),
                recurringId = ids.recurringRef(o.longOrNull("recurringId")),
                createdAt = o.optLong("createdAt", date),
            )
        }
        val budgets = root.optJSONArray("budgets")?.mapObjects { o ->
            val categoryId = ids.budgetCategoryRef(o.getLong("categoryId"))
            BudgetEntity(id = Ids.budget(categoryId), categoryId = categoryId, amount = o.getLong("amount"))
        }.orEmpty()
        return BackupData(accounts, categories, transactions, budgets, recurring)
    }

    // 1.0.0 was still called "Fin" when some of its backups were made.
    private val FORMATS = setOf(FORMAT, "fin-backup")

    private inline fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        for (item in this) array.put(transform(item))
        return array
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}

/** Spreadsheet export: one row per transaction, oldest first. */
object CsvExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    fun build(data: BackupData, zone: ZoneId = ZoneId.systemDefault()): String {
        val accounts = data.accounts.associateBy { it.id }
        val categories = data.categories.associateBy { it.id }
        // The byte-order mark makes Excel read the file as UTF-8, so Bangla notes survive.
        val out = StringBuilder("﻿")
        out.append("Date,Type,Category,Account,To account,Amount,Note\r\n")
        for (t in data.transactions.sortedWith(compareBy({ it.date }, { it.createdAt }))) {
            val row = listOf(
                Instant.ofEpochMilli(t.date).atZone(zone).format(dateFormat),
                t.type.name.lowercase().replaceFirstChar { it.uppercase() },
                t.categoryId?.let { categories[it]?.name }.orEmpty(),
                accounts[t.accountId]?.name.orEmpty(),
                t.toAccountId?.let { accounts[it]?.name }.orEmpty(),
                plainAmount(t.amount),
                t.note,
            )
            out.append(row.joinToString(",") { escape(it) }).append("\r\n")
        }
        return out.toString()
    }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun plainAmount(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val a = kotlin.math.abs(minor)
        return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }
}
