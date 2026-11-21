package com.keepsakeai.app.notify

import android.Manifest
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
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.R

/** keepsake notifications: money alerts, reminders and the weekly review. */
object Notifier {
    const val MONEY = "money"
    const val REMINDERS = "reminders"
    const val REVIEW = "review"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MONEY, "Budgets & renewals", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Budget warnings and upcoming subscription renewals"
        })
        nm.createNotificationChannel(NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders you set from snaps and notes"
        })
        nm.createNotificationChannel(NotificationChannel(REVIEW, "Weekly review", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Your Sunday summary"
        })
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** [openTab] / [openOverlay] decide where tapping the notification lands. */
    fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        openTab: String = "home",
        openOverlay: String? = null,
    ) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
            openOverlay?.let { putExtra(MainActivity.EXTRA_OPEN_OVERLAY, it) }
        }
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_keepsake)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
