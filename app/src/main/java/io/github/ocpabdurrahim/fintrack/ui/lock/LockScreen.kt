package io.github.ocpabdurrahim.fintrack.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ocpabdurrahim.fintrack.domain.Pin
import io.github.ocpabdurrahim.fintrack.system.Biometrics
import io.github.ocpabdurrahim.fintrack.system.findActivity
import io.github.ocpabdurrahim.fintrack.ui.theme.FinTheme

@Composable
fun LockScreen(pinHash: String, biometric: Boolean, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val biometricReady = remember(context) { Biometrics.available(context) }
    val canUseBiometric = biometric && biometricReady && activity != null
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun askBiometric() {
        activity?.let { Biometrics.prompt(it, onUnlock) }
    }

    fun press(digit: Char) {
        if (entered.length >= Pin.LENGTH) return
        wrong = false
        entered += digit
        if (entered.length == Pin.LENGTH) {
            if (Pin.verify(entered, pinHash)) {
                onUnlock()
            } else {
                wrong = true
                entered = ""
            }
        }
    }

    LaunchedEffect(Unit) { if (canUseBiometric) askBiometric() }
    // Back leaves the app instead of reaching the screens underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Swallow touches so nothing underneath can be tapped.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("lock_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val colors = FinTheme.colors
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd))),
                contentAlignment = Alignment.Center,
            ) {
                Text("F", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Text("FinTrack is locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                if (wrong) "Wrong PIN, try again" else "Enter your PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(Pin.LENGTH) { i ->
                    val filled = i < entered.length
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    wrong -> MaterialTheme.colorScheme.error
                                    filled -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            val rows = listOf("123", "456", "789")
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { d -> Key(d.toString(), onClick = { press(d) }) }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (canUseBiometric) {
                    KeyIcon(onClick = ::askBiometric, tag = "pin_biometric") {
                        Icon(Icons.Filled.Fingerprint, contentDescription = "Use fingerprint")
                    }
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                Key("0", onClick = { press('0') })
                KeyIcon(onClick = { if (entered.isNotEmpty()) entered = entered.dropLast(1) }, tag = "pin_delete") {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit")
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("pin_key_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun KeyIcon(onClick: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
