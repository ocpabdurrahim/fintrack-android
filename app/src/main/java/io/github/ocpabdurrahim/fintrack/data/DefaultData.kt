package io.github.ocpabdurrahim.fintrack.data

/**
 * What a fresh install starts with. Everything here can be renamed or removed
 * in the app. The ids are fixed (not random), so the phone and the web app,
 * each seeding on its own, end up with the same records instead of two copies.
 * web/src/data/defaults.ts holds the same list; keep the two in step.
 */
object DefaultData {

    val colors: List<Int> = listOf(
        0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB,
        0xFF64B5F6, 0xFF4FC3F7, 0xFF4DD0E1, 0xFF4DB6AC, 0xFF81C784,
        0xFFAED581, 0xFFFFD54F, 0xFFFFB74D, 0xFFFF8A65, 0xFFA1887F,
        0xFF90A4AE,
    ).map { it.toInt() }

    // Older emoji only, so they render on every supported Android version (8.0+).
    val categoryEmojis = listOf(
        "🍔", "🍕", "☕", "🛒", "🍎", "🚌", "🚗", "⛽", "🚕", "🏠",
        "💡", "💧", "🔥", "📱", "📶", "🛍️", "👕", "👟", "💊", "🏥",
        "📚", "🎓", "🎬", "🎮", "🎵", "✈️", "🏖️", "🎁", "💝", "👪",
        "👶", "🐾", "💇", "💄", "⚽", "🔧", "💳", "🏦", "📦", "🕌",
        "🙏", "💼", "💻", "🏪", "📈", "💰", "🏘️", "🎉", "🏷️", "❔",
    )

    val accountEmojis = listOf("💵", "🏦", "📱", "💳", "👛", "💰", "🏧", "📈", "🐷", "🏠", "💼", "🌐")

    private data class Seed(val id: String, val name: String, val emoji: String, val color: Long = 0)

    private val accountSeeds = listOf(
        Seed("acc_cash", "Cash", "💵"),
        Seed("acc_bank", "Bank", "🏦"),
        Seed("acc_wallet", "Mobile Wallet", "📱"),
    )

    private val expenseSeeds = listOf(
        Seed("cat_food", "Food", "🍔", 0xFFFFB74D),
        Seed("cat_groceries", "Groceries", "🛒", 0xFF81C784),
        Seed("cat_transport", "Transport", "🚌", 0xFF64B5F6),
        Seed("cat_rent", "Rent", "🏠", 0xFFA1887F),
        Seed("cat_bills", "Bills & Utilities", "💡", 0xFFFFD54F),
        Seed("cat_mobile", "Mobile & Internet", "📶", 0xFF4DD0E1),
        Seed("cat_shopping", "Shopping", "🛍️", 0xFFF06292),
        Seed("cat_health", "Health", "💊", 0xFFE57373),
        Seed("cat_education", "Education", "📚", 0xFF7986CB),
        Seed("cat_entertainment", "Entertainment", "🎬", 0xFFBA68C8),
        Seed("cat_family", "Family", "👪", 0xFF4DB6AC),
        Seed("cat_gifts", "Gifts & Charity", "🎁", 0xFF9575CD),
        Seed("cat_travel", "Travel", "✈️", 0xFF4FC3F7),
        Seed("cat_personal", "Personal Care", "💇", 0xFFFF8A65),
        Seed("cat_other", "Other", "📦", 0xFF90A4AE),
    )

    private val incomeSeeds = listOf(
        Seed("cat_salary", "Salary", "💼", 0xFF66BB6A),
        Seed("cat_business", "Business", "🏪", 0xFF26A69A),
        Seed("cat_freelance", "Freelance", "💻", 0xFF5C6BC0),
        Seed("cat_profit", "Profit & Interest", "📈", 0xFF42A5F5),
        Seed("cat_rent_income", "Rent Received", "🏘️", 0xFF8D6E63),
        Seed("cat_gifts_income", "Gifts Received", "🎁", 0xFFAB47BC),
        Seed("cat_other_income", "Other Income", "💰", 0xFFFFCA28),
    )

    fun accounts(): List<AccountEntity> = accountSeeds.mapIndexed { i, s ->
        AccountEntity(id = s.id, name = s.name, emoji = s.emoji, sortOrder = i + 1)
    }

    fun categories(): List<CategoryEntity> =
        expenseSeeds.mapIndexed { i, s -> s.toCategory(TxnType.EXPENSE, i + 1) } +
            incomeSeeds.mapIndexed { i, s -> s.toCategory(TxnType.INCOME, i + 1) }

    /** The fixed id a starter account has, by its (original) name. */
    fun accountIdFor(name: String): String? = accountSeeds.firstOrNull { it.name == name }?.id

    fun categoryIdFor(type: TxnType, name: String): String? =
        (if (type == TxnType.INCOME) incomeSeeds else expenseSeeds).firstOrNull { it.name == name }?.id

    private fun Seed.toCategory(type: TxnType, order: Int) =
        CategoryEntity(id = id, name = name, type = type, emoji = emoji, color = color.toInt(), sortOrder = order)
}
