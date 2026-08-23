package com.dopashift.interception.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.DailyTodoRepository
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a todo reminder notification triggered by allowance depletion.
 *
 * Requirement 5.10: When screen-time allowance is depleted, fire a notification
 * summarizing the top 5 highest-priority pending tasks. This is distinct from
 * scheduled reminders — it is triggered by the interception event itself.
 *
 * Triggered by [AllowanceTracker.OnAllowanceDepleted] callback.
 */
@Singleton
class InterceptionTodoReminderNotifier @Inject constructor(
    private val context: Context,
    private val dailyTodoRepository: DailyTodoRepository
) {

    companion object {
        private const val CHANNEL_ID = "interception_todo_reminder"
        private const val CHANNEL_NAME = "Screen-Time Task Reminders"
        private const val NOTIFICATION_ID = 3001
        private const val MAX_TASKS_IN_NOTIFICATION = 5
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Shows a notification with the top pending tasks for today.
     * Called when an app's screen-time allowance is depleted.
     *
     * @param userId The user whose pending tasks to display.
     * @param triggeringApp The package name of the app that triggered the notification.
     */
    suspend fun notifyPendingTasks(userId: UUID, triggeringApp: String) {
        val today = LocalDate.now()
        val allTodos = dailyTodoRepository.findByUserIdAndDate(userId, today)
        val pendingTodos = allTodos
            .filter { !it.isCompleted }
            .sortedBy { it.createdAt } // Oldest first = highest priority
            .take(MAX_TASKS_IN_NOTIFICATION)

        if (pendingTodos.isEmpty()) {
            Timber.d("No pending tasks to notify for user $userId")
            return
        }

        val notification = buildNotification(pendingTodos, triggeringApp)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Timber.i("Interception todo reminder shown: ${pendingTodos.size} tasks for $triggeringApp depletion")
    }

    /**
     * Builds the notification with an inbox-style layout for up to 5 tasks.
     */
    private fun buildNotification(
        todos: List<DailyTodoItem>,
        triggeringApp: String
    ): android.app.Notification {
        val totalPending = todos.size
        val title = "You have $totalPending pending task${if (totalPending > 1) "s" else ""}"
        val summary = "Time's up on your app — tackle these instead"

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(summary)

        todos.forEach { todo ->
            val taskLine = "• ${todo.text.take(60)}${if (todo.text.length > 60) "…" else ""}"
            inboxStyle.addLine(taskLine)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(todos.first().text.take(80))
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup("interception_reminders")
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders shown when screen-time allowance runs out"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
