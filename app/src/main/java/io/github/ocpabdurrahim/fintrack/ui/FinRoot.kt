package io.github.ocpabdurrahim.fintrack.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ocpabdurrahim.fintrack.AppContainer
import io.github.ocpabdurrahim.fintrack.FinApp
import io.github.ocpabdurrahim.fintrack.data.ThemeMode
import io.github.ocpabdurrahim.fintrack.data.TxnType
import io.github.ocpabdurrahim.fintrack.domain.Grouping
import io.github.ocpabdurrahim.fintrack.domain.Money
import io.github.ocpabdurrahim.fintrack.ui.budgets.BudgetsScreen
import io.github.ocpabdurrahim.fintrack.ui.edit.EditTransactionScreen
import io.github.ocpabdurrahim.fintrack.ui.home.HomeScreen
import io.github.ocpabdurrahim.fintrack.ui.lock.LockScreen
import io.github.ocpabdurrahim.fintrack.ui.manage.AccountsScreen
import io.github.ocpabdurrahim.fintrack.ui.manage.CategoriesScreen
import io.github.ocpabdurrahim.fintrack.ui.manage.RecurringScreen
import io.github.ocpabdurrahim.fintrack.ui.more.MoreScreen
import io.github.ocpabdurrahim.fintrack.ui.stats.StatsScreen
import io.github.ocpabdurrahim.fintrack.ui.theme.FinTheme
import io.github.ocpabdurrahim.fintrack.ui.transactions.TransactionsScreen

/** The user's currency and digit grouping, available to every screen. */
@Immutable
data class MoneyFormat(val symbol: String = "৳", val grouping: Grouping = Grouping.SOUTH_ASIAN) {
    fun format(minor: Long, showPlus: Boolean = false): String = Money.format(minor, symbol, grouping, showPlus)
    fun compact(minor: Long): String = Money.compact(minor, symbol, grouping)
}

val LocalMoney = staticCompositionLocalOf { MoneyFormat() }

/** A ViewModel scoped to the current screen, built from the app's dependencies. */
@Composable
inline fun <reified VM : ViewModel> finViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as FinApp).container
    return viewModel { create(container) }
}

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val STATS = "stats"
    const val BUDGETS = "budgets"
    const val MORE = "more"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val RECURRING = "recurring"
    const val EDIT = "edit?id={id}&type={type}"

    /** No [id] opens an empty form for a new transaction. Ids are URL-safe (letters, digits, _ and -). */
    fun edit(id: String? = null, type: TxnType = TxnType.EXPENSE) =
        if (id == null) "edit?type=${type.name}" else "edit?id=$id&type=${type.name}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.TRANSACTIONS, "History", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(Routes.STATS, "Stats", Icons.Outlined.PieChart, Icons.Filled.PieChart),
    Tab(Routes.BUDGETS, "Budgets", Icons.Outlined.Savings, Icons.Filled.Savings),
    Tab(Routes.MORE, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

@Composable
fun FinRoot(container: AppContainer, pendingAdd: TxnType?, onPendingAddHandled: () -> Unit) {
    val settings by container.settingsState.collectAsStateWithLifecycle()
    // Settings load in a few milliseconds; until then the window background shows.
    val s = settings ?: return
    val dark = when (s.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        // Status-bar icons must follow the app's theme, not the system's.
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
        )
    }
    FinTheme(dark = dark, dynamicColor = s.dynamicColor) {
        CompositionLocalProvider(LocalMoney provides MoneyFormat(s.currencySymbol, s.grouping)) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    // Exposes test tags as view ids, so the CI smoke test can find buttons.
                    .semantics { testTagsAsResourceId = true },
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize()) {
                    FinNavHost(pendingAdd = pendingAdd, onPendingAddHandled = onPendingAddHandled)
                    // Drawn over the app rather than instead of it, so the screen you
                    // were on is still there after unlocking.
                    val pinHash = s.pinHash
                    if (pinHash != null && !container.appLock.unlocked) {
                        LockScreen(
                            pinHash = pinHash,
                            biometric = s.biometric,
                            onUnlock = { container.appLock.unlocked = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinNavHost(pendingAdd: TxnType?, onPendingAddHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(pendingAdd) {
        if (pendingAdd != null) {
            nav.navigate(Routes.edit(type = pendingAdd))
            onPendingAddHandled()
        }
    }

    Scaffold(
        // Each screen handles its own insets; the outer scaffold only adds the bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("nav_${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAdd = { type -> nav.navigate(Routes.edit(type = type)) },
                    onOpen = { id -> nav.navigate(Routes.edit(id = id)) },
                    onSeeAll = { nav.openTab(Routes.TRANSACTIONS) },
                    onAccounts = { nav.navigate(Routes.ACCOUNTS) },
                    onBudgets = { nav.openTab(Routes.BUDGETS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onAdd = { type -> nav.navigate(Routes.edit(type = type)) },
                    onOpen = { id -> nav.navigate(Routes.edit(id = id)) },
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.MORE) { MoreScreen(onOpen = { r -> nav.navigate(r) }) }
            composable(Routes.ACCOUNTS) { AccountsScreen(onBack = { nav.navigateUp() }) }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.navigateUp() }) }
            composable(Routes.RECURRING) { RecurringScreen(onBack = { nav.navigateUp() }) }
            composable(
                route = Routes.EDIT,
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = TxnType.EXPENSE.name
                    },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                val id = args?.getString("id")
                val typeName = args?.getString("type")
                val type = TxnType.entries.firstOrNull { it.name == typeName } ?: TxnType.EXPENSE
                EditTransactionScreen(id = id, initialType = type, onDone = { nav.navigateUp() })
            }
        }
    }
}

/** Bottom-tab navigation: one copy of each tab, and each tab keeps its scroll state. */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
