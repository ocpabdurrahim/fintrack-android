package io.github.ocpabdurrahim.fin.ui.stats

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fin.data.CategoryEntity
import io.github.ocpabdurrahim.fin.data.FinRepository
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.domain.Dates
import io.github.ocpabdurrahim.fin.domain.Stats
import io.github.ocpabdurrahim.fin.domain.Totals
import io.github.ocpabdurrahim.fin.ui.LocalMoney
import io.github.ocpabdurrahim.fin.ui.components.BarGroup
import io.github.ocpabdurrahim.fin.ui.components.DailyBars
import io.github.ocpabdurrahim.fin.ui.components.DonutChart
import io.github.ocpabdurrahim.fin.ui.components.EmojiBadge
import io.github.ocpabdurrahim.fin.ui.components.EmptyState
import io.github.ocpabdurrahim.fin.ui.components.FinCard
import io.github.ocpabdurrahim.fin.ui.components.GroupedBarChart
import io.github.ocpabdurrahim.fin.ui.components.LegendDot
import io.github.ocpabdurrahim.fin.ui.components.MonthSelector
import io.github.ocpabdurrahim.fin.ui.components.ProgressBar
import io.github.ocpabdurrahim.fin.ui.components.StatTile
import io.github.ocpabdurrahim.fin.ui.finViewModel
import io.github.ocpabdurrahim.fin.ui.theme.FinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

data class ShareItem(val category: CategoryEntity?, val amount: Long, val fraction: Float)

data class StatsState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val type: TxnType = TxnType.EXPENSE,
    val totals: Totals = Totals(),
    val shares: List<ShareItem> = emptyList(),
    val months: List<Pair<YearMonth, Totals>> = emptyList(),
    val daily: List<Long> = emptyList(),
    val dailyAverage: Long = 0,
)

class StatsViewModel(repo: FinRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val type = MutableStateFlow(TxnType.EXPENSE)

    // Six months ending at the selected one, loaded together for the trend chart.
    private val window = month.flatMapLatest { m ->
        repo.rawBetween(Dates.monthStart(m.minusMonths(5)), Dates.monthEnd(m)).map { m to it }
    }

    val state: StateFlow<StatsState> = combine(window, type, repo.categories) { (m, txns), t, categories ->
        val zone = ZoneId.systemDefault()
        val start = Dates.monthStart(m, zone)
        val end = Dates.monthEnd(m, zone)
        val inMonth = txns.filter { it.date in start until end }
        val byId = categories.associateBy { it.id }
        val totals = Totals.of(inMonth)
        val months = (5 downTo 0).map { m.minusMonths(it.toLong()) }
        StatsState(
            loading = false,
            month = m,
            type = t,
            totals = totals,
            shares = Stats.byCategory(inMonth, t).map { s ->
                ShareItem(s.categoryId?.let { byId[it] }, s.amount, s.fraction)
            },
            months = months.zip(Stats.monthly(txns, months, zone)),
            daily = Stats.daily(inMonth, m, t, zone),
            dailyAverage = Stats.dailyAverage(
                if (t == TxnType.EXPENSE) totals.expense else totals.income,
                m,
                LocalDate.now(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setMonth(m: YearMonth) {
        month.value = m
    }

    fun setType(t: TxnType) {
        type.value = t
    }
}

@Composable
fun StatsScreen() {
    val vm = finViewModel { StatsViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val colors = FinTheme.colors
    val isExpense = state.type == TxnType.EXPENSE
    val typeColor = if (isExpense) colors.expense else colors.income
    val typeTotal = if (isExpense) state.totals.expense else state.totals.income

    Scaffold(topBar = { TopAppBar(title = { Text("Statistics") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSelector(state.month, vm::setMonth) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Income", money.format(state.totals.income), colors.income, Modifier.weight(1f))
                    StatTile("Expense", money.format(state.totals.expense), colors.expense, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val net = state.totals.net
                    StatTile(
                        "Saved",
                        money.format(net, showPlus = true),
                        if (net >= 0) colors.income else colors.expense,
                        Modifier.weight(1f),
                    )
                    StatTile(
                        if (isExpense) "Spent per day" else "Earned per day",
                        money.format(state.dailyAverage),
                        MaterialTheme.colorScheme.onSurface,
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                val options = listOf(TxnType.EXPENSE to "Expenses", TxnType.INCOME to "Income")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (t, label) ->
                        SegmentedButton(
                            selected = state.type == t,
                            onClick = { vm.setType(t) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        ) { Text(label) }
                    }
                }
            }
            item {
                FinCard {
                    Text(
                        if (isExpense) "Where the money went" else "Where the money came from",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.shares.isEmpty()) {
                        EmptyState(
                            emoji = "📊",
                            title = "No ${if (isExpense) "expenses" else "income"} in ${Dates.month(state.month)}",
                            body = "Add a few transactions and the breakdown appears here.",
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                        DonutChart(
                            slices = state.shares.map { it.amount to categoryColor(it.category) },
                            modifier = Modifier
                                .size(220.dp)
                                .align(Alignment.CenterHorizontally)
                                .testTag("donut_chart"),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (isExpense) "Spent" else "Earned",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(money.format(typeTotal), style = MaterialTheme.typography.titleLarge, color = typeColor)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        state.shares.forEach { share -> ShareRow(share) }
                    }
                }
            }
            item {
                FinCard {
                    Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(colors.income, "Income")
                        LegendDot(colors.expense, "Expense")
                    }
                    Spacer(Modifier.height(16.dp))
                    GroupedBarChart(
                        groups = state.months.map { (m, t) -> BarGroup(Dates.shortMonth(m), listOf(t.income, t.expense)) },
                        seriesColors = listOf(colors.income, colors.expense),
                        highlight = state.months.lastIndex,
                    )
                    val best = state.months.maxByOrNull { it.second.net }
                    if (best != null && best.second.net > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Best month for saving: ${Dates.month(best.first)} (${money.format(best.second.net, showPlus = true)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                FinCard {
                    Text(
                        if (isExpense) "Daily spending" else "Daily income",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val peak = state.daily.withIndex().maxByOrNull { it.value }
                    if (peak != null && peak.value > 0) {
                        Text(
                            "Biggest day: ${peak.index + 1} ${Dates.shortMonth(state.month)} · ${money.format(peak.value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DailyBars(state.daily, typeColor)
                }
            }
        }
    }
}

@Composable
private fun categoryColor(category: CategoryEntity?): Color =
    category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline

@Composable
private fun ShareRow(share: ShareItem) {
    val money = LocalMoney.current
    val color = categoryColor(share.category)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(share.category?.emoji ?: "❔", color, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    share.category?.name ?: "Uncategorised",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(money.format(share.amount), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(share.fraction, color, Modifier.weight(1f), height = 6.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(share.fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
    }
}
