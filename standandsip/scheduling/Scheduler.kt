package com.standandsip.scheduling

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.standandsip.data.prefs.Prefs
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Scheduler {

    fun cancelTag(context: Context, tag: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }

    /** Re-schedule Stand/Water/Bath starting now using saved settings. */
    suspend fun scheduleAllFromNow(context: Context) {
        val s = Prefs.flow(context).first()
        scheduleStream(context, "stand_stream", "Time to stand", "Let’s do 10 seconds on your feet ❤️", s)
        scheduleStream(context, "water_stream", "Hydration check", "Have a sip of water 💧", s)
        scheduleStream(context, "bath_stream",  "Bathroom check", "Quick bathroom break 🚻", s)
    }

    /** Enqueue next for a single tag (used by Worker after it fires). */
    suspend fun enqueueNext(context: Context, tag: String) {
        val s = Prefs.flow(context).first()
        val (title, text) = when (tag) {
            "water_stream" -> "Hydration check" to "Have a sip of water 💧"
            "bath_stream"  -> "Bathroom check"  to "Quick bathroom break 🚻"
            else -> "Time to stand" to "Let’s do 10 seconds on your feet ❤️"
        }
        scheduleStream(context, tag, title, text, s)
    }

    private fun scheduleStream(
        context: Context,
        tag: String,
        title: String,
        text: String,
        s: Prefs.Settings
    ) {
        val delayMin = nextDelayMinutes(s)
        if (delayMin < 0) return
        val data = workDataOf(
            ReminderWorker.KEY_TITLE to title,
            ReminderWorker.KEY_TEXT  to text,
            ReminderWorker.KEY_TAG   to tag
        )
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMin.toLong(), TimeUnit.MINUTES)
            .setInputData(data)
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    /** Compute minutes until next tick that falls inside [start,end) with [interval]. */
    private fun nextDelayMinutes(s: Prefs.Settings): Int {
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val start = s.startMin
        val end   = s.endMin
        val step  = s.intervalMin.coerceAtLeast(15) // guard

        // If current window has ended, schedule at tomorrow's start
        if (!isInWindow(nowMin, start, end)) {
            val minutesUntilStart = (
                    if (nowMin < start) start - nowMin else (24*60 - nowMin) + start
                    )
            return minutesUntilStart
        }

        // In window: snap to next interval boundary from start
        val elapsedFromStart = nowMin - start
        val next = ((elapsedFromStart / step) + 1) * step + start
        return next - nowMin
    }

    private fun isInWindow(now: Int, start: Int, end: Int): Boolean =
        if (start < end) now in start until end else (now >= start || now < end)
}