package com.standandsip.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.standandsip.data.db.AppDatabase
import com.standandsip.data.db.LogEntry
import com.standandsip.scheduling.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val tag = intent.getStringExtra("tag") ?: "stand_stream"

        when (action) {
            "com.standandsip.ACTION_DONE" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val type = when (tag) {
                        "water_stream" -> "water"
                        "bath_stream"  -> "bath"
                        else -> "stand"
                    }
                    db.logDao().insert(LogEntry(type = type, timestamp = System.currentTimeMillis()))

                    val data = workDataOf(
                        ReminderWorker.KEY_TITLE to when (tag) {
                            "water_stream" -> "Hydration check"
                            "bath_stream"  -> "Bathroom check"
                            else           -> "Time to stand"
                        },
                        ReminderWorker.KEY_TEXT to when (tag) {
                            "water_stream" -> "Have a sip of water 💧"
                            "bath_stream"  -> "Quick bathroom break 🚻"
                            else           -> "Let’s do 10 seconds on your feet ❤️"
                        },
                        ReminderWorker.KEY_TAG to tag
                    )

                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<ReminderWorker>()
                            .setInitialDelay(1, TimeUnit.MINUTES)
                            .setInputData(data)
                            .addTag(tag)
                            .build()
                    )
                }
            }

            "com.standandsip.ACTION_SNOOZE" -> {
                // Snooze reminder (+5 min)
                val data = workDataOf(
                    ReminderWorker.KEY_TITLE to "Snoozed reminder",
                    ReminderWorker.KEY_TEXT to "You postponed this reminder ⏰",
                    ReminderWorker.KEY_TAG to tag
                )

                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(5, TimeUnit.MINUTES)
                        .setInputData(data)
                        .addTag(tag)
                        .build()
                )

            }
        }
    }
}