package io.github.ocpabdurrahim.fintrack.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fintrack.data.CategoryEntity
import io.github.ocpabdurrahim.fintrack.data.FinRepository
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Dates
import io.github.ocpabdurrahim.fintrack.ui.LocalMoney
import io.github.ocpabdurrahim.fintrack.ui.components.BudgetDialog
import io.github.ocpabdurrahim.fintrack.ui.components.EmojiBadge
import io.github.ocpabdurrahim.fintrack.ui.components.EmptyState
import io.github.ocpabdurrahim.fintrack.ui.components.MonthSelector
import io.github.ocpabdurrahim.fintrack.ui.components.ProgressBar
import io.github.ocpabdurrahim.fintrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.fintrack.ui.components.forBudget
import io.github.ocpabdurrahim.fintrack.ui.finViewModel
import io.github.ocpabdurrahim.fintrack.ui.theme.FinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class BudgetLine(val category: CategoryEntity?, val categoryId: Long, val limit: Long, val spent: Long) {
    val fraction: Float get() = if (limit > 0) spent.toFloat() / limit else 0f
}

data class BudgetsState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val overall: BudgetLine? = null,
    val totalSpent: Long = 0,
    val lines: List<BudgetLine> = emptyList(),
    val available: List<CategoryEntity> = emptyList(),
)

class BudgetsViewModel(private val repo: FinRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    private val monthTxns = month.flatMapLatest { m ->
        repo.rawBetween(Dates.monthStart(m), Dates.monthEnd(m)).map { m to it }
    }

    val state: StateFlow<BudgetsState> =
        combine(monthTxns, repo.budgets, repo.categories) { (m, txns), budgets, categories ->
            val expenses = txns.filter { it.type == TxnType.EXPENSE }
            val spentBy = expenses.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
            val total = expenses.sumOf { it.amount }
            val byId = categories.associateBy { it.id }
            val budgeted = budgets.map { it.categoryId }.toSet()
            BudgetsState(
                loading = false,
                month = m,
                overall = budgets.firstOrNull { it.categoryId == 0L }?.let { BudgetLine(null, 0L, it.amount, total) },
                totalSpent = total,
                lines = budgets
                    .filter { it.categoryId != 0L }
                    .mapNotNull { b -> byId[b.categoryId]?.let { c -> BudgetLine(c, c.id, b.amount, spentBy[c.id] ?: 0L) } }
                    .sortedByDescending { it.fraction },
                available = categories.filter { it.type == TxnType.EXPENSE && !it.archived && it.id !in budgeted },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState())

    fun setMonth(m: YearMonth) {
        month.value = m
    }

    fun setBudget(categoryId: Long, amount: Long) {
        viewModelScope.launch { repo.setBudget(categoryId, amount) }
    }

    fun removeBudget(categoryId: Long) {
        viewModelScope.launch { repo.removeBudget(categoryId) }
    }
}

/** Which budget dialog is open: the overall one, a new category, or an existing line. */
private sealed interface BudgetEditor {
    data object Overall : BudgetEditor
    data object NewCategory : BudgetEditor
    data class Existing(val line: BudgetLine) : BudgetEditor
}

@Composable
fun BudgetsScreen() {
    val vm = finViewModel { BudgetsViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val colors = FinTheme.colors
    var editor by remember { mutableStateOf<BudgetEditor?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Budgets") }) }) { padding ->
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
                val overall = state.overall
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Monthly budget", style = MaterialTheme.typography.titleMedium)
                        if (overall == null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Spent ${money.format(state.totalSpent)} in ${Dates.month(state.month)}. " +
                                    "Set a limit to see how much is left each day.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { editor = BudgetEditor.Overall }, modifier = Modifier.testTag("set_overall")) {
                                Text("Set monthly budget")
                            }
                        } else {
                            val left = overall.limit - overall.spent
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    money.format(overall.spent),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.forBudget(overall.fraction),
                                )
                                Text(
                                    "  of ${money.format(overall.limit)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            ProgressBar(overall.fraction, colors.forBudget(overall.fraction), height = 10.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (left >= 0) "${money.format(left)} left" else "Over budget by ${money.format(-left)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (left >= 0) MaterialTheme.colorScheme.onSecondaryContainer else colors.expense,
                            )
                            val today = LocalDate.now()
                            if (state.month == YearMonth.from(today) && left > 0) {
                                val daysLeft = state.month.lengthOfMonth() - today.dayOfMonth + 1
                                Text(
                                    "About ${money.format(left / daysLeft)} a day for the remaining $daysLeft ${if (daysLeft == 1) "day" else "days"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Change",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { editor = BudgetEditor.Overall }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            item { SectionHeader("By category") }
            if (state.lines.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        emoji = "🎯",
                        title = "No category budgets",
                        body = "Cap the categories that tend to run away — food, shopping, transport.",
                    )
                }
            }
            items(state.lines, key = { it.categoryId }) { line ->
                BudgetRow(line, onClick = { editor = BudgetEditor.Existing(line) })
            }
            if (state.available.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { editor = BudgetEditor.NewCategory },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_category_budget"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add a category budget")
                    }
                }
            }
        }
    }

    when (val e = editor) {
        null -> Unit
        BudgetEditor.Overall -> BudgetDialog(
            title = "Monthly budget",
            initialAmount = state.overall?.limit,
            onDismiss = { editor = null },
            onSave = { _, amount ->
                vm.setBudget(0L, amount)
                editor = null
            },
            onRemove = if (state.overall != null) {
                {
                    vm.removeBudget(0L)
                    editor = null
                }
            } else {
                null
            },
        )
        BudgetEditor.NewCategory -> BudgetDialog(
            title = "Category budget",
            initialAmount = null,
            choices = state.available,
            onDismiss = { editor = null },
            onSave = { categoryId, amount ->
                vm.setBudget(categoryId, amount)
                editor = null
            },
        )
        is BudgetEditor.Existing -> BudgetDialog(
            title = "${e.line.category?.emoji ?: ""} ${e.line.category?.name ?: "Budget"}".trim(),
            initialAmount = e.line.limit,
            fixedCategoryId = e.line.categoryId,
            onDismiss = { editor = null },
            onSave = { categoryId, amount ->
                vm.setBudget(categoryId, amount)
                editor = null
            },
            onRemove = {
                vm.removeBudget(e.line.categoryId)
                editor = null
            },
        )
    }
}

@Composable
private fun BudgetRow(line: BudgetLine, onClick: () -> Unit) {
    val money = LocalMoney.current
    val colors = FinTheme.colors
    val color = colors.forBudget(line.fraction)
    val left = line.limit - line.spent
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(line.category?.emoji ?: "❔", line.category?.let { Color(it.color) } ?: Color.Gray, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        line.category?.name ?: "Category",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${money.format(line.spent)} of ${money.format(line.limit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (left >= 0) "${money.format(left)} left" else "${money.format(-left)} over",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(line.fraction, color)
        }
    }
}
