package io.github.ocpabdurrahim.fintrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fintrack.data.AccountBalance
import io.github.ocpabdurrahim.fintrack.data.BudgetEntity
import io.github.ocpabdurrahim.fintrack.data.FinRepository
import io.github.ocpabdurrahim.fintrack.data.SettingsStore
import io.github.ocpabdurrahim.fintrack.data.TxnItem
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Dates
import io.github.ocpabdurrahim.fintrack.domain.Totals
import io.github.ocpabdurrahim.fintrack.ui.LocalMoney
import io.github.ocpabdurrahim.fintrack.ui.components.EmojiBadge
import io.github.ocpabdurrahim.fintrack.ui.components.EmptyState
import io.github.ocpabdurrahim.fintrack.ui.components.ProgressBar
import io.github.ocpabdurrahim.fintrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.fintrack.ui.components.TransactionRow
import io.github.ocpabdurrahim.fintrack.ui.components.forBudget
import io.github.ocpabdurrahim.fintrack.ui.finViewModel
import io.github.ocpabdurrahim.fintrack.ui.theme.FinTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class HomeState(
    val loading: Boolean = true,
    val total: Long = 0,
    val accounts: List<AccountBalance> = emptyList(),
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val todayIncome: Long = 0,
    val todayExpense: Long = 0,
    val todayCount: Int = 0,
    val overallBudget: Long? = null,
    val recent: List<TxnItem> = emptyList(),
    val hideBalance: Boolean = false,
)

class HomeViewModel(repo: FinRepository, private val settings: SettingsStore) : ViewModel() {
    private val month: YearMonth = YearMonth.now()

    val state: StateFlow<HomeState> = combine(
        repo.accountBalances,
        repo.rawBetween(Dates.monthStart(month), Dates.monthEnd(month)),
        repo.recent(8),
        repo.budgets,
        settings.settings,
    ) { balances, monthTxns, recent, budgets, s ->
        val today = LocalDate.now()
        val todayStart = Dates.dayStart(today)
        val todayEnd = Dates.dayStart(today.plusDays(1))
        val monthTotals = Totals.of(monthTxns)
        val todayTotals = Totals.of(monthTxns.filter { it.date in todayStart until todayEnd })
        HomeState(
            loading = false,
            total = balances.sumOf { it.balance },
            accounts = balances.filter { !it.account.archived },
            monthIncome = monthTotals.income,
            monthExpense = monthTotals.expense,
            todayIncome = todayTotals.income,
            todayExpense = todayTotals.expense,
            todayCount = todayTotals.count,
            overallBudget = budgets.firstOrNull { it.categoryId == BudgetEntity.OVERALL }?.amount,
            recent = recent,
            hideBalance = s.hideBalance,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun toggleHide() {
        viewModelScope.launch { settings.update { it.copy(hideBalance = !it.hideBalance) } }
    }
}

@Composable
fun HomeScreen(
    onAdd: (TxnType) -> Unit,
    onOpen: (String) -> Unit,
    onSeeAll: () -> Unit,
    onAccounts: () -> Unit,
    onBudgets: () -> Unit,
) {
    val vm = finViewModel { HomeViewModel(it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            greeting(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("FinTrack", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(TxnType.EXPENSE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.testTag("fab_add"),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BalanceCard(state, onToggleHide = vm::toggleHide) }
            item { QuickActions(onAdd) }
            item { TodayCard(state, onBudgets) }
            item { SectionHeader("Accounts", action = "Manage", onAction = onAccounts) }
            item { AccountsRow(state.accounts, state.hideBalance, onAccounts) }
            item { SectionHeader("Recent", action = "See all", onAction = onSeeAll) }
            item {
                if (state.recent.isEmpty()) {
                    if (!state.loading) {
                        EmptyState(
                            emoji = "🌱",
                            title = "No transactions yet",
                            body = "Tap Add to record your first income or expense. It takes a few seconds.",
                        )
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            state.recent.forEach { item ->
                                TransactionRow(item, onClick = { onOpen(item.txn.id) }, showDate = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
private fun BalanceCard(state: HomeState, onToggleHide: () -> Unit) {
    val money = LocalMoney.current
    val colors = FinTheme.colors
    val hidden = state.hideBalance
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleHide, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (hidden) "Show amounts" else "Hide amounts",
                        tint = Color.White,
                    )
                }
            }
            Text(
                if (hidden) "••••••" else money.format(state.total),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("balance_total"),
            )
            Spacer(Modifier.height(18.dp))
            Row {
                HeroStat(
                    label = "Income",
                    value = if (hidden) "••••" else money.format(state.monthIncome),
                    icon = Icons.Filled.ArrowDownward,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = "Expense",
                    value = if (hidden) "••••" else money.format(state.monthExpense),
                    icon = Icons.Filled.ArrowUpward,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "This month · ${Dates.month(YearMonth.now())}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickActions(onAdd: (TxnType) -> Unit) {
    val colors = FinTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickButton("Income", Icons.Filled.Add, colors.income, "quick_income", Modifier.weight(1f)) { onAdd(TxnType.INCOME) }
        QuickButton("Expense", Icons.Filled.Remove, colors.expense, "quick_expense", Modifier.weight(1f)) { onAdd(TxnType.EXPENSE) }
        QuickButton("Transfer", Icons.Filled.SwapHoriz, colors.transfer, "quick_transfer", Modifier.weight(1f)) { onAdd(TxnType.TRANSFER) }
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector, tint: Color, tag: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.testTag(tag),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun TodayCard(state: HomeState, onBudgets: () -> Unit) {
    val money = LocalMoney.current
    val colors = FinTheme.colors
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (state.todayCount) {
                            0 -> "Nothing recorded yet"
                            1 -> "1 entry"
                            else -> "${state.todayCount} entries"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (state.todayExpense > 0) "-" else "") + money.format(state.todayExpense),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.expense,
                        modifier = Modifier.testTag("today_expense"),
                    )
                    if (state.todayIncome > 0) {
                        Text("+" + money.format(state.todayIncome), style = MaterialTheme.typography.bodyMedium, color = colors.income)
                    }
                }
            }
            val budget = state.overallBudget
            if (budget != null && budget > 0) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val fraction = state.monthExpense.toFloat() / budget
                val left = budget - state.monthExpense
                Column(Modifier.clickable(onClick = onBudgets)) {
                    Row {
                        Text("Monthly budget", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            if (left >= 0) "${money.format(left)} left" else "${money.format(-left)} over",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.forBudget(fraction),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(fraction, colors.forBudget(fraction))
                }
            }
        }
    }
}

@Composable
private fun AccountsRow(accounts: List<AccountBalance>, hidden: Boolean, onAccounts: () -> Unit) {
    val money = LocalMoney.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(accounts, key = { it.account.id }) { ab ->
            Card(
                onClick = onAccounts,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.width(156.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    EmojiBadge(ab.account.emoji, MaterialTheme.colorScheme.primary, size = 34.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        ab.account.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (hidden) "••••" else money.format(ab.balance),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
