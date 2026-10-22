package io.github.ocpabdurrahim.fin.ui.manage

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.ocpabdurrahim.fin.data.CategoryEntity
import io.github.ocpabdurrahim.fin.data.FinRepository
import io.github.ocpabdurrahim.fin.data.RemoveResult
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.ui.components.CategoryDialog
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

class CategoriesViewModel(private val repo: FinRepository) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    fun save(category: CategoryEntity) {
        viewModelScope.launch { repo.saveCategory(category) }
    }

    fun remove(category: CategoryEntity) {
        viewModelScope.launch {
            val message = when (repo.removeCategory(category)) {
                RemoveResult.DELETED -> "${category.name} deleted"
                RemoveResult.ARCHIVED -> "${category.name} is used by past entries, so it was archived instead"
                RemoveResult.REFUSED -> "Keep at least one category of each kind"
            }
            messageChannel.trySend(message)
        }
    }

    fun restore(category: CategoryEntity) {
        viewModelScope.launch { repo.saveCategory(category.copy(archived = false)) }
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = finViewModel { CategoriesViewModel(it.repository) }
    val all by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(TxnType.EXPENSE) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val ofType = all.filter { it.type == tab }
    val active = ofType.filter { !it.archived }
    val archived = ofType.filter { it.archived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_category")) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val options = listOf(TxnType.EXPENSE to "Expense", TxnType.INCOME to "Income")
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    options.forEachIndexed { index, (t, label) ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { tab = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        ) { Text(label) }
                    }
                }
            }
            items(active, key = { it.id }) { c ->
                CategoryRow(c, onClick = { editing = c })
            }
            if (archived.isNotEmpty()) {
                item { SectionHeader("Archived") }
                items(archived, key = { it.id }) { c ->
                    CategoryRow(c, onClick = { vm.restore(c) }, action = "Restore")
                }
            }
        }
    }

    if (creating) {
        CategoryDialog(
            initial = null,
            type = tab,
            onDismiss = { creating = false },
            onSave = {
                vm.save(it)
                creating = false
            },
        )
    }
    editing?.let { category ->
        CategoryDialog(
            initial = category,
            type = category.type,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
            onDelete = {
                vm.remove(category)
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, onClick: () -> Unit, action: String? = null) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(category.emoji, Color(category.color))
            Spacer(Modifier.width(12.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (action != null) TextButton(onClick = onClick) { Text(action) }
        }
    }
}
