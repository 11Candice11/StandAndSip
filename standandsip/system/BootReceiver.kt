package com.standandsip.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.standandsip.scheduling.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.Default).launch {
                Scheduler.scheduleAllFromNow(context)
            }
        }
    }
}