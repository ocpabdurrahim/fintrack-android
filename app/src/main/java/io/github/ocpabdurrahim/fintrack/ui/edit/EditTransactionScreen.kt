package io.github.ocpabdurrahim.fintrack.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ocpabdurrahim.fintrack.data.AccountEntity
import io.github.ocpabdurrahim.fintrack.data.CategoryEntity
import io.github.ocpabdurrahim.fintrack.data.FinRepository
import io.github.ocpabdurrahim.fintrack.data.Frequency
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Dates
import io.github.ocpabdurrahim.fintrack.domain.Money
import io.github.ocpabdurrahim.fintrack.ui.LocalMoney
import io.github.ocpabdurrahim.fintrack.ui.components.CategoryDialog
import io.github.ocpabdurrahim.fintrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.fintrack.ui.components.EmojiBadge
import io.github.ocpabdurrahim.fintrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.fintrack.ui.components.forType
import io.github.ocpabdurrahim.fintrack.ui.finViewModel
import io.github.ocpabdurrahim.fintrack.ui.theme.FinTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class EditTransactionViewModel(
    private val repo: FinRepository,
    private val id: Long,
    initialType: TxnType,
) : ViewModel() {
    val isEdit = id != 0L

    var type by mutableStateOf(initialType)
        private set
    var amountText by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(LocalDate.now())
    var note by mutableStateOf("")
    var repeat by mutableStateOf<Frequency?>(null)
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    private var time: LocalTime = LocalTime.now()
    private var original: TransactionEntity? = null

    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<AccountEntity>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (isEdit) {
                val t = repo.getTransaction(id) ?: return@launch
                original = t
                type = t.type
                amountText = Money.toInput(t.amount)
                categoryId = t.categoryId
                accountId = t.accountId
                toAccountId = t.toAccountId
                date = Dates.toLocalDate(t.date)
                time = Dates.toLocalTime(t.date)
                note = t.note
            } else {
                val active = repo.accounts.first().filter { !it.archived }
                if (accountId == null) accountId = active.firstOrNull()?.id
                if (toAccountId == null) toAccountId = active.getOrNull(1)?.id
            }
        }
    }

    fun changeType(newType: TxnType) {
        if (newType == type) return
        type = newType
        categoryId = null // categories belong to one type
        error = null
    }

    fun clearError() {
        error = null
    }

    fun save(onDone: () -> Unit) {
        if (saving) return
        val amount = Money.parse(amountText)
        val from = accountId
        val to = toAccountId
        val problem = when {
            amount == null || amount <= 0L -> "Enter an amount above zero"
            from == null -> "Choose an account"
            type != TxnType.TRANSFER && categoryId == null -> "Choose a category"
            type == TxnType.TRANSFER && (to == null || to == from) -> "Pick two different accounts"
            else -> null
        }
        error = problem
        if (problem != null || amount == null || from == null) return

        saving = true
        val isTransfer = type == TxnType.TRANSFER
        val base = original ?: TransactionEntity(type = type, amount = amount, categoryId = null, accountId = from, date = 0L)
        val txn = base.copy(
            type = type,
            amount = amount,
            categoryId = if (isTransfer) null else categoryId,
            accountId = from,
            toAccountId = if (isTransfer) to else null,
            date = Dates.toMillis(date, time),
            note = note.trim(),
        )
        viewModelScope.launch {
            val newRepeat = if (isEdit) null else repeat
            repo.saveTransaction(txn, newRepeat)
            // A repeating entry dated in the past fills in the occurrences since then.
            if (newRepeat != null) repo.processRecurring(System.currentTimeMillis())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
            onDone()
        }
    }

    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryId = repo.saveCategory(category)
        }
    }
}

@Composable
fun EditTransactionScreen(id: Long, initialType: TxnType, onDone: () -> Unit) {
    val vm = finViewModel { EditTransactionViewModel(it.repository, id, initialType) }
    val allCategories by vm.categories.collectAsStateWithLifecycle()
    val allAccounts by vm.accounts.collectAsStateWithLifecycle()
    val colors = FinTheme.colors
    val money = LocalMoney.current

    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!vm.isEdit) {
            delay(200)
            runCatching { amountFocus.requestFocus() }
        }
    }

    val accounts = allAccounts.filter { !it.archived || it.id == vm.accountId || it.id == vm.toAccountId }
    val categories = allCategories.filter { it.type == vm.type && (!it.archived || it.id == vm.categoryId) }
    val typeColor = colors.forType(vm.type)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (vm.isEdit) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_button")) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                ) {
                    // Next to the button, so the message is visible however far the form scrolls.
                    val message = vm.error
                    if (message != null) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .testTag("form_error"),
                        )
                    }
                    Button(
                        onClick = { vm.save(onDone) },
                        enabled = !vm.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_button"),
                    ) {
                        Text(if (vm.isEdit) "Save changes" else "Save", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val types = listOf(TxnType.EXPENSE to "Expense", TxnType.INCOME to "Income", TxnType.TRANSFER to "Transfer")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (t, label) ->
                    SegmentedButton(
                        selected = vm.type == t,
                        onClick = { vm.changeType(t) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                        modifier = Modifier.testTag("type_${t.name.lowercase()}"),
                    ) { Text(label) }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = vm.amountText,
                onValueChange = { raw ->
                    Money.sanitize(raw)?.let {
                        vm.amountText = it
                        vm.clearError()
                    }
                },
                label = { Text("Amount") },
                prefix = { Text(money.symbol + " ", style = MaterialTheme.typography.headlineMedium, color = typeColor) },
                placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = typeColor, fontWeight = FontWeight.SemiBold),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
                    .testTag("amount_input"),
            )

            if (vm.type == TxnType.TRANSFER) {
                FieldLabel("From")
                AccountChips(accounts, vm.accountId, prefix = "from") { vm.accountId = it }
                FieldLabel("To")
                AccountChips(accounts, vm.toAccountId, prefix = "to") { vm.toAccountId = it }
            } else {
                FieldLabel("Category")
                CategoryGrid(
                    categories = categories,
                    selectedId = vm.categoryId,
                    onPick = {
                        vm.categoryId = it
                        vm.clearError()
                    },
                    onNew = { newCategory = true },
                )
                FieldLabel(if (vm.type == TxnType.INCOME) "Received in" else "Paid from")
                AccountChips(accounts, vm.accountId, prefix = "account") { vm.accountId = it }
            }

            FieldLabel("Date")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val today = LocalDate.now()
                FilterChip(selected = vm.date == today, onClick = { vm.date = today }, label = { Text("Today") })
                FilterChip(
                    selected = vm.date == today.minusDays(1),
                    onClick = { vm.date = today.minusDays(1) },
                    label = { Text("Yesterday") },
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(Dates.full(vm.date)) },
                    leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                )
            }

            FieldLabel("Note")
            OutlinedTextField(
                value = vm.note,
                onValueChange = { if (it.length <= 200) vm.note = it },
                placeholder = { Text("What was it for? (optional)") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_input"),
            )

            if (!vm.isEdit) {
                FieldLabel("Repeat")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = vm.repeat == null, onClick = { vm.repeat = null }, label = { Text("Never") })
                    Frequency.entries.forEach { f ->
                        FilterChip(selected = vm.repeat == f, onClick = { vm.repeat = f }, label = { Text(f.label) })
                    }
                }
                val every = when (vm.repeat) {
                    Frequency.DAILY -> "day"
                    Frequency.WEEKLY -> "week"
                    Frequency.MONTHLY -> "month"
                    Frequency.YEARLY -> "year"
                    null -> null
                }
                if (every != null) {
                    Text(
                        "FinTrack will add this again every $every by itself. Manage it under More → Recurring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker works in UTC midnights; turn that back into a calendar date.
                    pickerState.selectedDateMillis?.let {
                        vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this transaction?",
            text = "It will be removed from your history and balances. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete(onDone)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (newCategory) {
        val categoryType = if (vm.type == TxnType.INCOME) TxnType.INCOME else TxnType.EXPENSE
        CategoryDialog(
            initial = null,
            type = categoryType,
            onDismiss = { newCategory = false },
            onSave = {
                newCategory = false
                vm.addCategory(it)
            },
        )
    }
}

@Composable
private fun AccountChips(accounts: List<AccountEntity>, selectedId: Long?, prefix: String, onPick: (Long) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { a ->
            FilterChip(
                selected = a.id == selectedId,
                onClick = { onPick(a.id) },
                label = { Text("${a.emoji}  ${a.name}") },
                modifier = Modifier.testTag("${prefix}_${a.name}"),
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onNew: () -> Unit,
) {
    // An even grid: as many ~84dp columns as fit, each stretched to share the width.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 84.dp).toInt().coerceAtLeast(3)
        val tiles: List<CategoryEntity?> = categories + null // null is the "New" tile
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        if (c != null) {
                            CategoryTile(
                                emoji = c.emoji,
                                name = c.name,
                                color = Color(c.color),
                                selected = c.id == selectedId,
                                onClick = { onPick(c.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cat_${c.name}"),
                            )
                        } else {
                            CategoryTile(
                                emoji = "＋",
                                name = "New",
                                color = MaterialTheme.colorScheme.outline,
                                selected = false,
                                onClick = onNew,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cat_new"),
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    emoji: String,
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmojiBadge(emoji, color, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
