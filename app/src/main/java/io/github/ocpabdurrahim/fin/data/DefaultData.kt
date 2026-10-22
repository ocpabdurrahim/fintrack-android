package io.github.ocpabdurrahim.fin.data

/** What a fresh install starts with. Everything here can be renamed or removed in the app. */
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

    fun accounts(): List<AccountEntity> = listOf(
        AccountEntity(name = "Cash", emoji = "💵", sortOrder = 1),
        AccountEntity(name = "Bank", emoji = "🏦", sortOrder = 2),
        AccountEntity(name = "Mobile Wallet", emoji = "📱", sortOrder = 3),
    )

    fun categories(): List<CategoryEntity> {
        val expense = listOf(
            Triple("Food", "🍔", 0xFFFFB74D),
            Triple("Groceries", "🛒", 0xFF81C784),
            Triple("Transport", "🚌", 0xFF64B5F6),
            Triple("Rent", "🏠", 0xFFA1887F),
            Triple("Bills & Utilities", "💡", 0xFFFFD54F),
            Triple("Mobile & Internet", "📶", 0xFF4DD0E1),
            Triple("Shopping", "🛍️", 0xFFF06292),
            Triple("Health", "💊", 0xFFE57373),
            Triple("Education", "📚", 0xFF7986CB),
            Triple("Entertainment", "🎬", 0xFFBA68C8),
            Triple("Family", "👪", 0xFF4DB6AC),
            Triple("Gifts & Charity", "🎁", 0xFF9575CD),
            Triple("Travel", "✈️", 0xFF4FC3F7),
            Triple("Personal Care", "💇", 0xFFFF8A65),
            Triple("Other", "📦", 0xFF90A4AE),
        )
        val income = listOf(
            Triple("Salary", "💼", 0xFF66BB6A),
            Triple("Business", "🏪", 0xFF26A69A),
            Triple("Freelance", "💻", 0xFF5C6BC0),
            Triple("Profit & Interest", "📈", 0xFF42A5F5),
            Triple("Rent Received", "🏘️", 0xFF8D6E63),
            Triple("Gifts Received", "🎁", 0xFFAB47BC),
            Triple("Other Income", "💰", 0xFFFFCA28),
        )
        return expense.mapIndexed { i, (name, emoji, color) ->
            CategoryEntity(name = name, type = TxnType.EXPENSE, emoji = emoji, color = color.toInt(), sortOrder = i + 1)
        } + income.mapIndexed { i, (name, emoji, color) ->
            CategoryEntity(name = name, type = TxnType.INCOME, emoji = emoji, color = color.toInt(), sortOrder = i + 1)
        }
    }
}
