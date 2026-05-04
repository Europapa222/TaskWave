package com.taskwave.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.taskwave.app.MainActivity
import com.taskwave.app.R
import com.taskwave.app.data.Priority
import com.taskwave.app.data.TaskRepository
import com.taskwave.app.data.TodoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val tasks = TaskRepository(context).loadTasksSnapshot()
                val activeTasks = tasks.filter { !it.isDone }.sortedWith(todayTaskComparator())
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildViews(context, activeTasks))
                }
            }
        }

        private fun buildViews(context: Context, tasks: List<TodoItem>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.task_widget)
            views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))

            val rows = listOf(R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3)
            rows.forEachIndexed { index, viewId ->
                val task = tasks.getOrNull(index)
                if (task == null) {
                    views.setViewVisibility(viewId, if (index == 0) View.VISIBLE else View.GONE)
                    views.setTextViewText(viewId, if (index == 0) context.getString(R.string.widget_empty) else "")
                } else {
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, widgetTaskText(task))
                }
                views.setOnClickPendingIntent(viewId, openAppIntent(context))
            }

            val remaining = tasks.size - rows.size
            if (remaining > 0) {
                views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                views.setTextViewText(R.id.widget_more, context.getString(R.string.widget_more, remaining))
            } else {
                views.setViewVisibility(R.id.widget_more, View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_more, openAppIntent(context))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun widgetTaskText(task: TodoItem): String {
            val prefix = if (task.priority == Priority.HIGH) "!" else "•"
            val due = task.dueAt?.let { " · ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it))}" }.orEmpty()
            return "$prefix ${task.title}$due"
        }

        private fun todayTaskComparator(): Comparator<TodoItem> {
            val now = System.currentTimeMillis()
            val endOfToday = endOfTodayMillis(now)
            return compareBy<TodoItem> {
                when {
                    it.dueAt != null && it.dueAt < now -> 0
                    it.priority == Priority.HIGH -> 1
                    it.dueAt != null && it.dueAt <= endOfToday -> 2
                    it.dueAt != null -> 3
                    else -> 4
                }
            }.thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { priorityRank(it.priority) }
                .thenBy { it.createdAt }
        }

        private fun priorityRank(priority: Priority): Int {
            return when (priority) {
                Priority.HIGH -> 0
                Priority.MEDIUM -> 1
                Priority.LOW -> 2
            }
        }

        private fun endOfTodayMillis(now: Long): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return calendar.timeInMillis
        }
    }
}
