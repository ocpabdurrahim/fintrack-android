package io.github.ocpabdurrahim.fintrack

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ocpabdurrahim.fintrack.domain.Pin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end flows through the real UI and database. */
@RunWith(AndroidJUnit4::class)
class FinFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        resetApp()
        compose.waitForTag("fab_add")
        compose.waitForTextOf("balance_total", "৳0")
    }

    private fun addTransaction(openWith: String, amount: String, categoryTag: String?) {
        compose.onNodeWithTag(openWith).performClick()
        compose.waitForTag("amount_input")
        compose.onNodeWithTag("amount_input").performTextInput(amount)
        if (categoryTag != null) compose.onNodeWithTag(categoryTag).performClick()
        compose.onNodeWithTag("save_button").performClick()
        compose.waitForTag("fab_add")
    }

    @Test
    fun expenseAndIncomeUpdateTheBalance() {
        addTransaction("fab_add", "250", "cat_Food")
        compose.waitForTextOf("balance_total", "-৳250")
        compose.waitForTextOf("today_expense", "-৳250")

        addTransaction("quick_income", "5000", "cat_Salary")
        compose.waitForTextOf("balance_total", "৳4,750")
    }

    @Test
    fun transferMovesMoneyButKeepsTheTotal() {
        addTransaction("quick_income", "1000", "cat_Salary")
        compose.waitForTextOf("balance_total", "৳1,000")

        // A new transfer defaults to the first account -> the second (Cash -> Bank).
        addTransaction("quick_transfer", "400", null)
        compose.waitUntil(10_000) {
            runBlocking { container.repository.accountBalances.first() }
                .associate { it.account.name to it.balance }["Bank"] == 400_00L
        }
        val balances = runBlocking { container.repository.accountBalances.first() }.associate { it.account.name to it.balance }
        assertEquals(600_00L, balances["Cash"])
        assertEquals(400_00L, balances["Bank"])
        compose.waitForTextOf("balance_total", "৳1,000")
    }

    @Test
    fun deletingATransactionRestoresTheBalance() {
        addTransaction("fab_add", "120", "cat_Transport")
        compose.waitForTextOf("balance_total", "-৳120")

        compose.onAllNodesWithTag("txn_row")[0].performClick()
        compose.waitForTag("delete_button")
        compose.onNodeWithTag("delete_button").performClick()
        compose.onNodeWithTag("confirm_button").performClick()
        compose.waitForTextOf("balance_total", "৳0")
    }

    @Test
    fun savingWithoutAnAmountShowsAnError() {
        compose.onNodeWithTag("fab_add").performClick()
        compose.waitForTag("save_button")
        compose.onNodeWithTag("save_button").performClick()
        // The keyboard may still be sliding up and moving the bottom bar; wait for it to settle.
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("form_error").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Enter an amount above zero").assertIsDisplayed()
    }

    @Test
    fun everyTabOpens() {
        compose.onNodeWithTag("nav_transactions").performClick()
        compose.waitForTag("search_open")
        compose.onNodeWithTag("nav_stats").performClick()
        compose.waitForText("Statistics")
        compose.onNodeWithTag("nav_budgets").performClick()
        compose.waitForTag("set_overall")
        compose.onNodeWithTag("nav_more").performClick()
        compose.waitForTag("more_accounts")
        compose.onNodeWithTag("more_accounts").performClick()
        compose.waitForTag("add_account")
    }

    @Test
    fun appLockNeedsTheRightPin() {
        runBlocking { container.settings.update { it.copy(pinHash = Pin.hash("1234")) } }
        compose.runOnUiThread { container.appLock.unlocked = false }
        compose.waitForTag("lock_screen")

        listOf("1", "2", "3", "5").forEach { compose.onNodeWithTag("pin_key_$it").performClick() }
        compose.waitForText("Wrong PIN, try again")
        compose.onNodeWithTag("lock_screen").assertIsDisplayed()

        listOf("1", "2", "3", "4").forEach { compose.onNodeWithTag("pin_key_$it").performClick() }
        compose.waitForGone("lock_screen")
    }
}
