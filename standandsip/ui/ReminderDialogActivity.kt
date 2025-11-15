package com.standandsip.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.standandsip.data.db.AppDatabase
import com.standandsip.data.db.LogEntry
import com.standandsip.scheduling.ReminderWorker
import com.standandsip.scheduling.Scheduler
import com.standandsip.util.RefreshBus
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReminderDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 🔒 Make sure this pops above lock screen/other apps
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: "Reminder"
        val text  = intent.getStringExtra("text") ?: ""
        val tag   = intent.getStringExtra("tag") ?: "stand_stream"

        setContent {
            MaterialTheme {
                ReminderDialogScreen(
                    title = title,
                    text  = text,
                    tag   = tag,
                    onFinished = { finish() }
                )
            }
        }
    }
}

@Composable
fun ReminderDialogScreen(
    title: String,
    text: String,
    tag: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val vm = remember { CountdownViewModel() }
    val scope = rememberCoroutineScope()
    val wm = remember { WorkManager.getInstance(context) }
    val scroll = rememberScrollState()

    val secondsLeft by vm.secondsLeft.collectAsState()
    val isRunning by vm.isRunning.collectAsState()

    val isStand = tag == "stand_stream"
    val primaryButtonLabel = when (tag) {
        "water_stream" -> "I got a glass"
        "bath_stream"  -> "I went"
        else           -> "I’m standing"
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            if (!isRunning || !isStand) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))

                // Stack buttons so they never clip on small screens
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (isStand) {
                            vm.resetTo(10) // test: 10s
                            vm.startCountdown(10) {
                                scope.launch {
                                    db.logDao().insert(
                                        LogEntry(type = "stand", timestamp = System.currentTimeMillis())
                                    )
                                    RefreshBus.fire()
                                    // Reset next reminder (test: +1 min)
                                    Scheduler.cancelTag(context, "stand_stream")
                                    wm.enqueue(
                                        OneTimeWorkRequestBuilder<ReminderWorker>()
                                            .setInitialDelay(1, TimeUnit.MINUTES)
                                            .setInputData(
                                                workDataOf(
                                                    ReminderWorker.KEY_TITLE to "Time to stand",
                                                    ReminderWorker.KEY_TEXT  to "Let’s do 10 seconds on your feet ❤️",
                                                    ReminderWorker.KEY_TAG   to "stand_stream"
                                                )
                                            )
                                            .addTag("stand_stream")
                                            .build()
                                    )
                                    onFinished()
                                }
                            }
                        } else {
                            val type = if (tag == "water_stream") "water" else "bath"
                            scope.launch {
                                db.logDao().insert(
                                    LogEntry(type = type, timestamp = System.currentTimeMillis())
                                )
                                RefreshBus.fire()
                                Scheduler.cancelTag(context, tag)
                                wm.enqueue(
                                    OneTimeWorkRequestBuilder<ReminderWorker>()
                                        .setInitialDelay(1, TimeUnit.MINUTES)
                                        .setInputData(
                                            workDataOf(
                                                ReminderWorker.KEY_TITLE to if (tag=="water_stream") "Hydration check" else "Bathroom check",
                                                ReminderWorker.KEY_TEXT  to if (tag=="water_stream") "Have a sip of water 💧" else "Quick bathroom break 🚻",
                                                ReminderWorker.KEY_TAG   to tag
                                            )
                                        )
                                        .addTag(tag)
                                        .build()
                                )
                                onFinished()
                            }
                        }
                    }) { Text(primaryButtonLabel) }

                    OutlinedButton(onClick = {
                        Scheduler.cancelTag(context, tag)
                        wm.enqueue(
                            OneTimeWorkRequestBuilder<ReminderWorker>()
                                .setInitialDelay(5, TimeUnit.MINUTES)
                                .setInputData(
                                    workDataOf(
                                        ReminderWorker.KEY_TITLE to title,
                                        ReminderWorker.KEY_TEXT  to text,
                                        ReminderWorker.KEY_TAG   to tag
                                    )
                                )
                                .addTag(tag)
                                .build()
                        )
                        onFinished()
                    }) { Text("Snooze 5 min") }

                    TextButton(onClick = { (context as? android.app.Activity)?.finishAffinity() }) {
                        Text("Close")
                    }
                }
            } else {
                Text("Standing...", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                Text("${secondsLeft}s", style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}