package io.github.ocpabdurrahim.fintrack

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ocpabdurrahim.fintrack.data.Settings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

val container: AppContainer
    get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinApp).container

/** Empty database with the starter accounts/categories, default settings, unlocked. */
fun resetApp() {
    runBlocking {
        container.repository.resetAll()
        container.settings.update { Settings() }
    }
}

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitForGone(tag: String, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.waitForTextOtherThan(tag: String, text: String, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
            val shown = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            shown != null && shown != text
        }
    }

fun ComposeTestRule.waitForTextOf(tag: String, expected: String, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) { runCatching { onNodeWithTag(tag).assertTextEquals(expected) }.isSuccess }

/**
 * Saves a full-screen PNG into the app's files dir; CI pulls them out with
 * `run-as` after the run so they can be looked at.
 */
object Screenshots {
    fun take(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(900) // let fades and the donut animation finish
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.filesDir, "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
