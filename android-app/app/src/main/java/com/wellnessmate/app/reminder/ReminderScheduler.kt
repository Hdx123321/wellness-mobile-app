package com.wellnessmate.app.reminder

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
import com.wellnessmate.app.MainActivity
import com.wellnessmate.app.R
import java.util.Calendar

data class ReminderSettings(val enabled: Boolean, val hour: Int, val minute: Int)

object ReminderScheduler {
    private const val PREFS = "daily-reminder"
    private const val REQUEST_CODE = 4101

    fun settings(context: Context): ReminderSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ReminderSettings(
            enabled = prefs.getBoolean("enabled", false),
            hour = prefs.getInt("hour", 20),
            minute = prefs.getInt("minute", 0),
        )
    }

    fun save(context: Context, settings: ReminderSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("hour", settings.hour)
            .putInt("minute", settings.minute)
            .apply()
        if (settings.enabled) schedule(context, settings) else cancel(context)
    }

    fun schedule(context: Context, settings: ReminderSettings = settings(context)) {
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
            pendingIntent(context),
        )
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction("com.wellnessmate.DAILY_REMINDER"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.schedule(context)
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
        val notification = NotificationCompat.Builder(context, "daily-wellness")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("WellnessMate daily check-in")
            .setContentText("Review today's trackers and record anything missing.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(4101, notification)
    }
}
