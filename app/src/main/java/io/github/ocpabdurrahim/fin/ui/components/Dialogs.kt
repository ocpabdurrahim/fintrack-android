package io.github.ocpabdurrahim.fin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ocpabdurrahim.fin.data.AccountEntity
import io.github.ocpabdurrahim.fin.data.CategoryEntity
import io.github.ocpabdurrahim.fin.data.DefaultData
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.domain.Money
import io.github.ocpabdurrahim.fin.domain.Pin
import io.github.ocpabdurrahim.fin.ui.LocalMoney

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_button")) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Amount input that only accepts what could become a valid amount. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
) {
    val money = LocalMoney.current
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> Money.sanitize(raw, allowNegative)?.let(onValueChange) },
        label = { Text(label) },
        prefix = { Text(money.symbol + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun EmojiPicker(options: List<String>, selected: String, onPick: (String) -> Unit, tint: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { emoji ->
            val isSelected = emoji == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) tint.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DefaultData.colors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                    )
                    .clickable { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun CategoryDialog(
    initial: CategoryEntity?,
    type: TxnType,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: if (type == TxnType.INCOME) "💰" else "📦") }
    var color by remember { mutableIntStateOf(initial?.color ?: DefaultData.colors.random()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New ${if (type == TxnType.INCOME) "income" else "expense"} category" else "Edit category") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(emoji, Color(color), size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 30) name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_name"),
                    )
                }
                FieldLabel("Icon")
                EmojiPicker(DefaultData.categoryEmojis, emoji, { emoji = it }, Color(color))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 8) emoji = it },
                    label = { Text("Or type any emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel("Colour")
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && emoji.isNotBlank(),
                onClick = {
                    val base = initial ?: CategoryEntity(name = "", type = type, emoji = "", color = color)
                    onSave(base.copy(name = name.trim(), emoji = emoji.trim(), color = color))
                },
                modifier = Modifier.testTag("dialog_save"),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun AccountDialog(
    initial: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "💵") }
    var opening by remember { mutableStateOf(initial?.let { if (it.openingBalance == 0L) "" else Money.toInput(it.openingBalance) } ?: "") }
    val tint = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New account" else "Edit account") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(emoji, tint, size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 30) name = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. bKash, Dutch-Bangla") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                FieldLabel("Icon")
                EmojiPicker(DefaultData.accountEmojis, emoji, { emoji = it }, tint)
                FieldLabel("Starting balance")
                AmountField(opening, { opening = it }, "Money already in it", allowNegative = true)
                Text(
                    "What the account held before you started using Fin. Use a minus sign for a credit card or loan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = initial ?: AccountEntity(name = "", emoji = emoji)
                    onSave(base.copy(name = name.trim(), emoji = emoji, openingBalance = Money.parse(opening, allowNegative = true) ?: 0L))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Set a monthly limit. With [choices] the user picks the category first;
 * without, the dialog edits the budget for [fixedCategoryId].
 */
@Composable
fun BudgetDialog(
    title: String,
    initialAmount: Long?,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, amount: Long) -> Unit,
    choices: List<CategoryEntity>? = null,
    fixedCategoryId: Long = 0L,
    onRemove: (() -> Unit)? = null,
) {
    var amount by remember { mutableStateOf(initialAmount?.let { Money.toInput(it) } ?: "") }
    var categoryId by remember { mutableLongStateOf(choices?.firstOrNull()?.id ?: fixedCategoryId) }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (choices != null) {
                    FieldLabel("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { c ->
                            FilterChip(
                                selected = c.id == categoryId,
                                onClick = { categoryId = c.id },
                                label = { Text("${c.emoji} ${c.name}") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                AmountField(amount, { amount = it }, "Monthly limit", modifier = Modifier.testTag("budget_amount"))
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0L && (choices == null || choices.isNotEmpty()),
                onClick = { if (parsed != null) onSave(categoryId, parsed) },
                modifier = Modifier.testTag("dialog_save"),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun accept(raw: String): String? = raw.takeIf { it.length <= Pin.LENGTH && it.all(Char::isDigit) }
    val errorText = error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a ${Pin.LENGTH}-digit PIN") },
        text = {
            Column {
                Text(
                    "Fin will ask for it when it opens. If you forget it, you will need to clear the app's data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { raw -> accept(raw)?.let { pin = it; error = null } },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { raw -> accept(raw)?.let { confirm = it; error = null } },
                    label = { Text("Repeat PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorText != null,
                    supportingText = if (errorText != null) {
                        { Text(errorText) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == Pin.LENGTH && confirm.length == Pin.LENGTH,
                onClick = { if (pin == confirm) onSet(pin) else error = "The two PINs don't match" },
            ) { Text("Turn on") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
