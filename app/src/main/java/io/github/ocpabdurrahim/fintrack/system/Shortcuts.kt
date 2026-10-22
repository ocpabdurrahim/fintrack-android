package io.github.ocpabdurrahim.fintrack.system

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.ocpabdurrahim.fintrack.MainActivity
import io.github.ocpabdurrahim.fintrack.R
import io.github.ocpabdurrahim.fintrack.data.TxnType

/** Long-press the launcher icon: "Add expense" / "Add income". */
object Shortcuts {
    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "add_expense", "Add expense", R.drawable.ic_shortcut_expense, TxnType.EXPENSE),
            shortcut(context, "add_income", "Add income", R.drawable.ic_shortcut_income, TxnType.INCOME),
        )
        // Some launchers refuse shortcuts; the app works the same without them.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, label: String, icon: Int, type: TxnType) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_ADD)
                    .putExtra(MainActivity.EXTRA_TYPE, type.name)
            )
            .build()
}
