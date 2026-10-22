package io.github.ocpabdurrahim.fin.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ocpabdurrahim.fin.FinApp
import io.github.ocpabdurrahim.fin.MainActivity
import io.github.ocpabdurrahim.fin.R
import io.github.ocpabdurrahim.fin.domain.Dates
import io.github.ocpabdurrahim.fin.domain.Money
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** The once-a-day "did you record today?" notification. */
object Reminders {
    private const val CHANNEL_ID = "daily_reminder"
    private const val WORK_NAME = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "A nudge to record the day's income and expenses"
        manager.createNotificationChannel(channel)
    }

    /** (Re)starts the schedule at the new time. */
    fun schedule(context: Context, hour: Int, minute: Int) =
        enqueue(context, hour, minute, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)

    /** Makes sure a schedule exists without moving one that is already set. */
    fun ensureScheduled(context: Context, hour: Int, minute: Int) =
        enqueue(context, hour, minute, ExistingPeriodicWorkPolicy.KEEP)

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun enqueue(context: Context, hour: Int, minute: Int, policy: ExistingPeriodicWorkPolicy) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    @SuppressLint("MissingPermission") // checked by canNotify()
    fun show(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fin)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Runs once a day: writes due recurring entries, then shows the reminder. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FinApp).container
        container.repository.processRecurring(System.currentTimeMillis())

        val settings = container.settings.settings.first()
        if (!settings.reminderEnabled) return Result.success()

        val today = LocalDate.now()
        val totals = container.repository.totalsBetween(Dates.dayStart(today), Dates.dayStart(today.plusDays(1)))
        val text = if (totals.count == 0) {
            "Nothing recorded today yet. Take ten seconds to add today's income and expenses."
        } else {
            val spent = Money.format(totals.expense, settings.currencySymbol, settings.grouping)
            val earned = Money.format(totals.income, settings.currencySymbol, settings.grouping)
            "Today so far: spent $spent, earned $earned in ${totals.count} entries. Anything missing?"
        }
        Reminders.show(applicationContext, "Fin daily check-in", text)
        return Result.success()
    }
}
