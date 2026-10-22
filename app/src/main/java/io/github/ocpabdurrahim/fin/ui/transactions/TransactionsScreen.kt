package io.github.ocpabdurrahim.fin.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fin.data.FinRepository
import io.github.ocpabdurrahim.fin.data.TxnItem
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.domain.Dates
import io.github.ocpabdurrahim.fin.ui.LocalMoney
import io.github.ocpabdurrahim.fin.ui.components.EmptyState
import io.github.ocpabdurrahim.fin.ui.components.MonthSelector
import io.github.ocpabdurrahim.fin.ui.components.StatTile
import io.github.ocpabdurrahim.fin.ui.components.TransactionRow
import io.github.ocpabdurrahim.fin.ui.finViewModel
import io.github.ocpabdurrahim.fin.ui.theme.FinTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(val date: LocalDate, val items: List<TxnItem>, val net: Long)

data class TransactionsState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val searching: Boolean = false,
    val filter: TxnType? = null,
    val days: List<DayGroup> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
)

private data class Source(val month: YearMonth, val query: String, val items: List<TxnItem>)

class TransactionsViewModel(private val repo: FinRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<TxnType?>(null)

    // A search looks through every month; otherwise the list shows one month.
    private val source: Flow<Source> =
        combine(month, query.debounce { if (it.isBlank()) 0L else 250L }) { m, q -> m to q.trim() }
            .flatMapLatest { (m, q) ->
                val items = if (q.isEmpty()) {
                    repo.transactionsBetween(Dates.monthStart(m), Dates.monthEnd(m))
                } else {
                    repo.search(q)
                }
                items.map { Source(m, q, it) }
            }

    val state: StateFlow<TransactionsState> = combine(source, filter) { src, f ->
        val visible = if (f == null) src.items else src.items.filter { it.txn.type == f }
        val days = visible
            .groupBy { Dates.toLocalDate(it.txn.date) }
            .map { (date, items) ->
                val net = items.sumOf {
                    when (it.txn.type) {
                        TxnType.INCOME -> it.txn.amount
                        TxnType.EXPENSE -> -it.txn.amount
                        TxnType.TRANSFER -> 0L
                    }
                }
                DayGroup(date, items, net)
            }
            .sortedByDescending { it.date }
        TransactionsState(
            loading = false,
            month = src.month,
            searching = src.query.isNotEmpty(),
            filter = f,
            days = days,
            income = visible.filter { it.txn.type == TxnType.INCOME }.sumOf { it.txn.amount },
            expense = visible.filter { it.txn.type == TxnType.EXPENSE }.sumOf { it.txn.amount },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun setMonth(m: YearMonth) {
        month.value = m
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setFilter(f: TxnType?) {
        filter.value = f
    }
}

@Composable
fun TransactionsScreen(onAdd: (TxnType) -> Unit, onOpen: (Long) -> Unit) {
    val vm = finViewModel { TransactionsViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var queryText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            if (searchOpen) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            searchOpen = false
                            queryText = ""
                            vm.setQuery("")
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                    },
                    title = {
                        TextField(
                            value = queryText,
                            onValueChange = {
                                queryText = it
                                vm.setQuery(it)
                            },
                            placeholder = { Text("Search notes and categories") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .testTag("search_input"),
                        )
                    },
                    actions = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = {
                                queryText = ""
                                vm.setQuery("")
                            }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("History") },
                    actions = {
                        IconButton(onClick = { searchOpen = true }, modifier = Modifier.testTag("search_open")) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(TxnType.EXPENSE) }, modifier = Modifier.testTag("fab_add")) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        val money = LocalMoney.current
        val colors = FinTheme.colors
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            if (!state.searching) {
                item { MonthSelector(state.month, vm::setMonth, Modifier.padding(horizontal = 8.dp)) }
            }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val options = listOf<Pair<String, TxnType?>>(
                        "All" to null,
                        "Expenses" to TxnType.EXPENSE,
                        "Income" to TxnType.INCOME,
                        "Transfers" to TxnType.TRANSFER,
                    )
                    options.forEach { (label, type) ->
                        FilterChip(
                            selected = state.filter == type,
                            onClick = { vm.setFilter(type) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile("Income", money.format(state.income), colors.income, Modifier.weight(1f))
                    StatTile("Expense", money.format(state.expense), colors.expense, Modifier.weight(1f))
                    val net = state.income - state.expense
                    StatTile(
                        "Net",
                        money.format(net, showPlus = true),
                        if (net >= 0) colors.income else colors.expense,
                        Modifier.weight(1f),
                    )
                }
            }
            if (state.days.isEmpty() && !state.loading) {
                item {
                    if (state.searching) {
                        EmptyState("🔍", "No matches", "Nothing in your notes or category names matches that search.")
                    } else {
                        EmptyState("🗓️", "Nothing this month", "Transactions you add for ${Dates.month(state.month)} will show here.")
                    }
                }
            }
            state.days.forEach { day ->
                stickyHeader(key = "day-${day.date}") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Dates.dayHeader(day.date),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (day.net != 0L) {
                                Text(
                                    money.format(day.net, showPlus = true),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(day.items, key = { it.txn.id }) { item ->
                    TransactionRow(
                        item,
                        onClick = { onOpen(item.txn.id) },
                        showDate = state.searching,
                    )
                }
            }
        }
    }
}
