package com.standandsip.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.standandsip.ui.ReminderDialogActivity
import com.standandsip.system.ActionReceiver

private const val CHANNEL_ID = "standandsip_reminders"

private fun ensureChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val existing = nm.getNotificationChannel(CHANNEL_ID)
    if (existing == null) {
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Stand & Sip Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent reminders with sound"
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }
}

/** Sticky (ongoing) reminder that stays until user taps Done/Snooze. */
fun showStickyReminder(context: Context, title: String, text: String, tag: String) {
    ensureChannel(context)

    // Open the dialog if user taps the body
    val openIntent = Intent(context, ReminderDialogActivity::class.java).apply {
        putExtra("title", title)
        putExtra("text", text)
        putExtra("tag", tag)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val contentPi = PendingIntent.getActivity(
        context,
        tag.hashCode(),
        openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // DONE action -> ActionReceiver
    val doneIntent = Intent(context, ActionReceiver::class.java).apply {
        action = "com.standandsip.ACTION_DONE"
        putExtra("tag", tag)
    }
    val donePi = PendingIntent.getBroadcast(
        context, ("done_$tag").hashCode(), doneIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // SNOOZE action -> ActionReceiver
    val snoozeIntent = Intent(context, ActionReceiver::class.java).apply {
        action = "com.standandsip.ACTION_SNOOZE"
        putExtra("tag", tag)
    }
    val snoozePi = PendingIntent.getBroadcast(
        context, ("snooze_$tag").hashCode(), snoozeIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val n = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)   // sound + vibrate
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(contentPi)
        .setOngoing(true)                               // 👈 makes it sticky (can’t swipe away)
        .setAutoCancel(false)
        .addAction(0, "Done", donePi)
        .addAction(0, "Snooze", snoozePi)
        .build()

    NotificationManagerCompat.from(context).notify(tag.hashCode(), n)
}

/** Optionally clear a specific sticky reminder by tag */
fun cancelReminder(context: Context, tag: String) {
    NotificationManagerCompat.from(context).cancel(tag.hashCode())
}