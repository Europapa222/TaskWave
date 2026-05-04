package com.taskwave.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.taskwave.app.MainActivity
import com.taskwave.app.R
import com.taskwave.app.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat

private const val REMINDER_CHANNEL_ID = "taskwave_reminders"
private const val CLEANUP_CHANNEL_ID = "taskwave_cleanup"
private const val ACTION_REMINDER = "com.taskwave.app.ACTION_REMINDER"
private const val ACTION_CLEANUP = "com.taskwave.app.ACTION_CLEANUP"
private const val EXTRA_TASK_ID = "task_id"
private const val EXTRA_TASK_TITLE = "task_title"
private const val EXTRA_TASK_DUE = "task_due"
private const val COMPLETED_TASK_TTL_MS = 24 * 60 * 60 * 1000L

object ReminderScheduler {
    fun scheduleReminder(context: Context, taskId: String, title: String, dueAt: Long?, reminderAt: Long?) {
        cancelReminder(context, taskId)
        if (reminderAt == null || reminderAt <= System.currentTimeMillis()) return

        val intent = Intent(context, TaskAlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_TASK_TITLE, title)
            .putExtra(EXTRA_TASK_DUE, dueAt ?: 0L)

        scheduleAlarm(context, reminderAt, pendingIntent(context, taskId, intent))
    }

    fun cancelReminder(context: Context, taskId: String) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).setAction(ACTION_REMINDER)
        alarmManager(context).cancel(pendingIntent(context, taskId, intent))
    }

    fun scheduleCleanup(context: Context, taskId: String, title: String, completedAt: Long) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
            .setAction(ACTION_CLEANUP)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_TASK_TITLE, title)

        scheduleAlarm(context, completedAt + COMPLETED_TASK_TTL_MS, pendingIntent(context, "$taskId-cleanup", intent))
    }

    fun cancelCleanup(context: Context, taskId: String) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).setAction(ACTION_CLEANUP)
        alarmManager(context).cancel(pendingIntent(context, "$taskId-cleanup", intent))
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CLEANUP_CHANNEL_ID,
                context.getString(R.string.cleanup_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun alarmManager(context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private fun scheduleAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val manager = alarmManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context, key: String, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.ensureChannels(context)
        when (intent.action) {
            ACTION_REMINDER -> showReminder(context, intent)
            ACTION_CLEANUP -> cleanupTask(context, intent)
        }
    }

    private fun showReminder(context: Context, intent: Intent) {
        if (!canNotify(context)) return

        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        val dueAt = intent.getLongExtra(EXTRA_TASK_DUE, 0L).takeIf { it > 0L }
        val text = if (dueAt == null) {
            context.getString(R.string.reminder_notification_text)
        } else {
            context.getString(R.string.reminder_notification_text_with_due, DateFormat.getDateTimeInstance().format(dueAt))
        }

        NotificationManagerCompat.from(context).notify(
            intent.getStringExtra(EXTRA_TASK_ID).orEmpty().hashCode(),
            NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
                .setContentText(text)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    private fun cleanupTask(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            val repo = TaskRepository(context)
            val tasks = repo.loadTasksSnapshot()
            val task = tasks.firstOrNull { it.id == taskId && it.isDone } ?: return@launch
            val completedAt = task.completedAt ?: return@launch
            if (System.currentTimeMillis() - completedAt < COMPLETED_TASK_TTL_MS) return@launch

            repo.saveTasks(tasks.filterNot { it.id == taskId })
            showCleanupNotification(context, title)
        }
    }

    private fun showCleanupNotification(context: Context, title: String) {
        if (!canNotify(context)) return
        NotificationManagerCompat.from(context).notify(
            "${title}-cleanup".hashCode(),
            NotificationCompat.Builder(context, CLEANUP_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.cleanup_notification_title))
                .setContentText(context.getString(R.string.cleanup_notification_text, title))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
