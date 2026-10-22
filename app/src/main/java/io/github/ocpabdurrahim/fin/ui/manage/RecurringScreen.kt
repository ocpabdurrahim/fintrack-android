package io.github.ocpabdurrahim.fin.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fin.data.AccountEntity
import io.github.ocpabdurrahim.fin.data.CategoryEntity
import io.github.ocpabdurrahim.fin.data.FinRepository
import io.github.ocpabdurrahim.fin.data.RecurringEntity
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.domain.Dates
import io.github.ocpabdurrahim.fin.domain.Recurrence
import io.github.ocpabdurrahim.fin.ui.LocalMoney
import io.github.ocpabdurrahim.fin.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.fin.ui.components.EmojiBadge
import io.github.ocpabdurrahim.fin.ui.components.EmptyState
import io.github.ocpabdurrahim.fin.ui.components.forType
import io.github.ocpabdurrahim.fin.ui.finViewModel
import io.github.ocpabdurrahim.fin.ui.theme.FinTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringItem(
    val recurring: RecurringEntity,
    val category: CategoryEntity?,
    val account: AccountEntity?,
    val toAccount: AccountEntity?,
)

class RecurringViewModel(private val repo: FinRepository) : ViewModel() {
    val items: StateFlow<List<RecurringItem>?> =
        combine(repo.recurring, repo.categories, repo.accounts) { list, categories, accounts ->
            val cats = categories.associateBy { it.id }
            val accs = accounts.associateBy { it.id }
            list.map { r ->
                RecurringItem(
                    recurring = r,
                    category = r.categoryId?.let { cats[it] },
                    account = accs[r.accountId],
                    toAccount = r.toAccountId?.let { accs[it] },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setActive(r: RecurringEntity, active: Boolean) {
        viewModelScope.launch { repo.setRecurringActive(r, active) }
    }

    fun delete(r: RecurringEntity) {
        viewModelScope.launch { repo.deleteRecurring(r.id) }
    }
}

@Composable
fun RecurringScreen(onBack: () -> Unit) {
    val vm = finViewModel { RecurringViewModel(it.repository) }
    val recurringItems by vm.items.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<RecurringEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val list = recurringItems
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🔁",
                        title = "Nothing repeating yet",
                        body = "When you add rent, salary or a subscription, pick Repeat → Monthly and Fin will enter it for you each time.",
                    )
                }
            }
            items(list.orEmpty(), key = { it.recurring.id }) { item ->
                RecurringRow(
                    item,
                    onToggle = { vm.setActive(item.recurring, it) },
                    onDelete = { deleting = item.recurring },
                )
            }
        }
    }

    deleting?.let { r ->
        ConfirmDialog(
            title = "Stop repeating?",
            text = "Fin will stop adding this entry. The ones it already added stay in your history.",
            confirmLabel = "Stop",
            destructive = true,
            onConfirm = {
                vm.delete(r)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RecurringRow(item: RecurringItem, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val money = LocalMoney.current
    val colors = FinTheme.colors
    val r = item.recurring
    val isTransfer = r.type == TxnType.TRANSFER
    val title = if (isTransfer) {
        "${item.account?.name ?: "?"} → ${item.toAccount?.name ?: "?"}"
    } else {
        item.category?.name ?: "Uncategorised"
    }
    val next = Dates.toLocalDate(Recurrence.occurrence(r.startDate, r.frequency, r.generated))
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(
                if (isTransfer) "🔁" else item.category?.emoji ?: "❔",
                if (isTransfer) colors.transfer else item.category?.let { Color(it.color) } ?: Color.Gray,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    money.format(r.amount) + " · " + r.frequency.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.forType(r.type),
                )
                Text(
                    if (r.active) "Next: ${Dates.full(next)}" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (r.note.isNotBlank()) {
                    Text(
                        r.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(checked = r.active, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Stop repeating") }
        }
    }
}
