package com.alpinefitness.app.reminder

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
import com.alpinefitness.app.MainActivity
import com.alpinefitness.app.R
import java.util.Calendar

data class ReminderSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val title: String = "WellnessMate daily check-in",
    val content: String = "Review today's trackers and record anything missing.",
)

object ReminderScheduler {
    private const val PREFS = "daily-reminder"
    private const val REQUEST_CODE = 4101
    const val DAILY_KEY = "daily"
    const val SLEEP_KEY = "sleep"
    const val WAKE_KEY = "wake"

    fun settings(context: Context): ReminderSettings = settings(context, DAILY_KEY)

    fun settings(context: Context, key: String): ReminderSettings {
        val prefs = context.getSharedPreferences(prefsName(key), Context.MODE_PRIVATE)
        val defaults = defaultSettings(key)
        return ReminderSettings(
            enabled = prefs.getBoolean("enabled", false),
            hour = prefs.getInt("hour", defaults.hour),
            minute = prefs.getInt("minute", defaults.minute),
            title = prefs.getString("title", defaults.title) ?: defaults.title,
            content = prefs.getString("content", defaults.content) ?: defaults.content,
        )
    }

    fun save(context: Context, settings: ReminderSettings) = save(context, DAILY_KEY, settings)

    fun save(context: Context, key: String, settings: ReminderSettings) {
        context.getSharedPreferences(prefsName(key), Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("hour", settings.hour)
            .putInt("minute", settings.minute)
            .putString("title", settings.title)
            .putString("content", settings.content)
            .apply()
        if (settings.enabled) schedule(context, key, settings) else cancel(context, key)
    }

    fun schedule(context: Context, settings: ReminderSettings = settings(context)) {
        schedule(context, DAILY_KEY, settings)
    }

    fun scheduleAll(context: Context) {
        listOf(DAILY_KEY, SLEEP_KEY, WAKE_KEY).forEach { key ->
            schedule(context, key, settings(context, key))
        }
    }

    fun schedule(context: Context, key: String, settings: ReminderSettings = settings(context, key)) {
        if (!settings.enabled) return
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.hour)
            set(Calendar.MINUTE, settings.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            trigger.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context, key),
        )
    }

    private fun cancel(context: Context, key: String = DAILY_KEY) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, key))
    }

    private fun pendingIntent(context: Context, key: String) = PendingIntent.getBroadcast(
        context,
        requestCode(key),
        Intent(context, ReminderReceiver::class.java)
            .setAction("com.wellnessmate.REMINDER.$key")
            .putExtra("reminder_key", key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun prefsName(key: String) = if (key == DAILY_KEY) PREFS else "$PREFS-$key"

    private fun requestCode(key: String) = when (key) {
        SLEEP_KEY -> REQUEST_CODE + 1
        WAKE_KEY -> REQUEST_CODE + 2
        else -> REQUEST_CODE
    }

    private fun defaultSettings(key: String) = when (key) {
        SLEEP_KEY -> ReminderSettings(false, 23, 30, "Sleep time", "Time to wind down and get ready for bed.")
        WAKE_KEY -> ReminderSettings(false, 7, 30, "Wake up", "Good morning. Time to start your day.")
        else -> ReminderSettings(false, 20, 0)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "daily-wellness", "Daily wellness reminder", NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val key = intent.getStringExtra("reminder_key") ?: ReminderScheduler.DAILY_KEY
        val s = ReminderScheduler.settings(context, key)
        val notification = NotificationCompat.Builder(context, "daily-wellness")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.title)
            .setContentText(s.content)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(4101, notification)
    }
}
