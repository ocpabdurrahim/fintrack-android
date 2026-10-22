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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fin.data.AccountBalance
import io.github.ocpabdurrahim.fin.data.AccountEntity
import io.github.ocpabdurrahim.fin.data.FinRepository
import io.github.ocpabdurrahim.fin.data.RemoveResult
import io.github.ocpabdurrahim.fin.ui.LocalMoney
import io.github.ocpabdurrahim.fin.ui.components.AccountDialog
import io.github.ocpabdurrahim.fin.ui.components.EmojiBadge
import io.github.ocpabdurrahim.fin.ui.components.SectionHeader
import io.github.ocpabdurrahim.fin.ui.finViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountsViewModel(private val repo: FinRepository) : ViewModel() {
    val balances: StateFlow<List<AccountBalance>> =
        repo.accountBalances.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    fun save(account: AccountEntity) {
        viewModelScope.launch { repo.saveAccount(account) }
    }

    fun remove(account: AccountEntity) {
        viewModelScope.launch {
            val message = when (repo.removeAccount(account)) {
                RemoveResult.DELETED -> "${account.name} deleted"
                RemoveResult.ARCHIVED -> "${account.name} has history, so it was archived instead"
                RemoveResult.REFUSED -> "Keep at least one account"
            }
            messageChannel.trySend(message)
        }
    }

    fun restore(account: AccountEntity) {
        viewModelScope.launch { repo.saveAccount(account.copy(archived = false)) }
    }
}

@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val vm = finViewModel { AccountsViewModel(it.repository) }
    val balances by vm.balances.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountEntity?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val active = balances.filter { !it.account.archived }
    val archived = balances.filter { it.account.archived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_account")) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Total across accounts", style = MaterialTheme.typography.labelLarge)
                        Text(money.format(balances.sumOf { it.balance }), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            items(active, key = { it.account.id }) { ab ->
                AccountRow(ab, onClick = { editing = ab.account })
            }
            if (archived.isNotEmpty()) {
                item { SectionHeader("Archived") }
                items(archived, key = { it.account.id }) { ab ->
                    AccountRow(ab, onClick = { vm.restore(ab.account) }, action = "Restore")
                }
            }
        }
    }

    if (creating) {
        AccountDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = {
                vm.save(it)
                creating = false
            },
        )
    }
    editing?.let { account ->
        AccountDialog(
            initial = account,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
            onRemove = {
                vm.remove(account)
                editing = null
            },
        )
    }
}

@Composable
private fun AccountRow(ab: AccountBalance, onClick: () -> Unit, action: String? = null) {
    val money = LocalMoney.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(ab.account.emoji, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ab.account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ab.account.openingBalance != 0L) {
                    Text(
                        "Started with ${money.format(ab.account.openingBalance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (action != null) {
                TextButton(onClick = onClick) { Text(action) }
            } else {
                Text(money.format(ab.balance), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
