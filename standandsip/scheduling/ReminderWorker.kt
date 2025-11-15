package com.standandsip.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.standandsip.util.showStickyReminder

class ReminderWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_TEXT  = "text"
        const val KEY_TAG   = "tag"
    }

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Reminder"
        val text  = inputData.getString(KEY_TEXT)  ?: ""
        val tag   = inputData.getString(KEY_TAG)   ?: "stand_stream"

        // 🔔 Show a sticky, non-dismissible notification (with Done/Snooze actions)
        showStickyReminder(applicationContext, title, text, tag)

        // 📅 Schedule the next reminder according to Settings (quiet hours + frequency)
        Scheduler.enqueueNext(applicationContext, tag)

        return Result.success()
    }
}