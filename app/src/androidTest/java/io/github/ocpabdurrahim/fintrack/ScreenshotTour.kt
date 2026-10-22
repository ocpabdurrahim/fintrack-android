package io.github.ocpabdurrahim.fintrack

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ocpabdurrahim.fintrack.data.Settings
import io.github.ocpabdurrahim.fintrack.data.ThemeMode
import io.github.ocpabdurrahim.fintrack.domain.Pin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks every screen with demo data loaded and saves a screenshot of each. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        runBlocking { container.settings.update { Settings() } }
    }

    private fun tab(name: String) {
        compose.onNodeWithTag("nav_$name").performClick()
    }

    private fun shot(name: String) {
        compose.waitForIdle() // make sure the last tap has been drawn
        Screenshots.take(name)
    }

    @Test
    fun tour() {
        runBlocking { DemoData.load(container) }
        // The recent rows can sit just below the fold, so wait for the balance instead.
        compose.waitForTextOtherThan("balance_total", "৳0")
        shot("01_home")

        tab("transactions")
        compose.waitForTag("search_open")
        compose.waitForTag("txn_row")
        shot("02_history")

        tab("stats")
        compose.waitForTag("donut_chart")
        shot("03_stats")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        shot("04_stats_trends")

        tab("budgets")
        compose.waitForText("By category")
        shot("05_budgets")

        tab("more")
        compose.waitForTag("more_accounts")
        shot("06_more")

        compose.onNodeWithTag("more_accounts").performClick()
        compose.waitForTag("add_account")
        shot("07_accounts")
        compose.onNodeWithContentDescription("Back").performClick()

        tab("home")
        compose.waitForTag("fab_add")
        compose.onNodeWithTag("fab_add").performClick()
        compose.waitForTag("amount_input")
        compose.onNodeWithTag("amount_input").performTextInput("350")
        compose.onNodeWithTag("amount_input").performImeAction()
        compose.onNodeWithTag("cat_Food").performClick()
        shot("08_add_expense")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForTag("fab_add")

        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK) } }
        compose.waitForIdle()
        shot("09_home_dark")
        tab("stats")
        compose.waitForTag("donut_chart")
        shot("10_stats_dark")

        runBlocking { container.settings.update { it.copy(theme = ThemeMode.LIGHT, pinHash = Pin.hash("2468")) } }
        compose.runOnUiThread { container.appLock.unlocked = false }
        compose.waitForTag("lock_screen")
        compose.onNodeWithTag("pin_key_2").performClick()
        compose.onNodeWithTag("pin_key_4").performClick()
        shot("11_lock")
    }
}
