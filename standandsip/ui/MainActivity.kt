package com.standandsip.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.standandsip.data.db.AppDatabase
import com.standandsip.data.db.LogEntry
import com.standandsip.data.prefs.Prefs
import com.standandsip.scheduling.ReminderWorker
import com.standandsip.scheduling.Scheduler
import com.standandsip.util.RefreshBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(onOpenHistory = { nav.navigate("history") }) }
                    composable("history")   { HistoryScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

/* -------------------- DASHBOARD (Home) -------------------- */

@Composable
fun DashboardScreen(onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { AppDatabase.getDatabase(context) }
    val wm = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var standCount by remember { mutableStateOf(0) }
    var waterCount by remember { mutableStateOf(0) }
    var bathCount  by remember { mutableStateOf(0) }

    suspend fun reload() {
        standCount = db.logDao().todayCount("stand")
        waterCount = db.logDao().todayCount("water")
        bathCount  = db.logDao().todayCount("bath")
    }
    LaunchedEffect(Unit) { reload() }

    // Refresh on resume
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) scope.launch { reload() } }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // Refresh when popup logs success
    LaunchedEffect(Unit) {
        RefreshBus.events.collectLatest { reload() }
    }

    // Settings
    val settings by Prefs.flow(context).collectAsState(initial = Prefs.Settings())
    var startMin by remember(settings) { mutableStateOf(settings.startMin) }
    var endMin   by remember(settings) { mutableStateOf(settings.endMin) }
    var interval by remember(settings) { mutableStateOf(settings.intervalMin) }

    fun mmToLabel(min: Int) = String.format(Locale.getDefault(), "%02d:%02d", min/60, min%60)
    fun showTimePicker(currentMin: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, hh, mm -> onPicked(hh * 60 + mm) }, currentMin/60, currentMin%60, true).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Stand & Sip", style = MaterialTheme.typography.headlineMedium)
        Text("Today's Progress", style = MaterialTheme.typography.titleMedium)

        StatCard("Stands", standCount,
            onDec = { scope.launch { db.logDao().deleteLatestToday("stand"); reload() } },
            onInc = { scope.launch { db.logDao().insert(LogEntry("stand", System.currentTimeMillis())); reload() } }
        )

        StatCard("Water", waterCount,
            onDec = { scope.launch { db.logDao().deleteLatestToday("water"); reload() } },
            onInc = { scope.launch { db.logDao().insert(LogEntry("water", System.currentTimeMillis())); reload() } }
        )

        StatCard("Bathroom", bathCount,
            onDec = { scope.launch { db.logDao().deleteLatestToday("bath"); reload() } },
            onInc = { scope.launch { db.logDao().insert(LogEntry("bath", System.currentTimeMillis())); reload() } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { scope.launch { reload() } }) { Text("Refresh") }
            OutlinedButton(onClick = onOpenHistory) { Text("View history") }
        }

        // Settings card
        SettingsCard(
            startMin = startMin,
            endMin = endMin,
            interval = interval,
            onChangeStart = { startMin = it },
            onChangeEnd = { endMin = it },
            onChangeInterval = { interval = it },
            onSave = {
                scope.launch {
                    Prefs.save(context, Prefs.Settings(startMin, endMin, interval))
                    Scheduler.cancelTag(context, "stand_stream")
                    Scheduler.cancelTag(context, "water_stream")
                    Scheduler.cancelTag(context, "bath_stream")
                    Scheduler.scheduleAllFromNow(context)
                }
            },
            mmToLabel = ::mmToLabel,
            showTimePicker = ::showTimePicker
        )

        // One-minute test buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TestButton("Test Stand (1m)") {
                wm.enqueue(
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(1, TimeUnit.MINUTES)
                        .setInputData(workDataOf(
                            ReminderWorker.KEY_TITLE to "Time to stand",
                            ReminderWorker.KEY_TEXT  to "Let’s do 10 seconds on your feet ❤️",
                            ReminderWorker.KEY_TAG   to "stand_stream"
                        ))
                        .addTag("stand_stream")
                        .build()
                )
            }
            TestButton("Test Water (1m)") {
                wm.enqueue(
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(1, TimeUnit.MINUTES)
                        .setInputData(workDataOf(
                            ReminderWorker.KEY_TITLE to "Hydration check",
                            ReminderWorker.KEY_TEXT  to "Have a sip of water 💧",
                            ReminderWorker.KEY_TAG   to "water_stream"
                        ))
                        .addTag("water_stream")
                        .build()
                )
            }
            TestButton("Test Bath (1m)") {
                wm.enqueue(
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(1, TimeUnit.MINUTES)
                        .setInputData(workDataOf(
                            ReminderWorker.KEY_TITLE to "Bathroom check",
                            ReminderWorker.KEY_TEXT  to "Quick bathroom break 🚻",
                            ReminderWorker.KEY_TAG   to "bath_stream"
                        ))
                        .addTag("bath_stream")
                        .build()
                )
            }
        }
    }
}

@Composable
private fun TestButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun StatCard(
    label: String,
    count: Int,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onDec,
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("−") }
                Spacer(Modifier.width(16.dp))
                Text("$count", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.width(16.dp))
                Button(onClick = onInc) { Text("+") }
            }
        }
    }
}

/* -------------------- SETTINGS CARD -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(
    startMin: Int,
    endMin: Int,
    interval: Int,
    onChangeStart: (Int) -> Unit,
    onChangeEnd: (Int) -> Unit,
    onChangeInterval: (Int) -> Unit,
    onSave: () -> Unit,
    mmToLabel: (Int) -> String,
    showTimePicker: (Int, (Int) -> Unit) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Start time: ${mmToLabel(startMin)}")
                OutlinedButton(onClick = { showTimePicker(startMin, onChangeStart) }) { Text("Change") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("End time: ${mmToLabel(endMin)}")
                OutlinedButton(onClick = { showTimePicker(endMin, onChangeEnd) }) { Text("Change") }
            }

            var expanded by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Frequency: every $interval min")
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Change") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(30, 60, 90, 120, 180).forEach { min ->
                            DropdownMenuItem(text = { Text("Every $min min") }, onClick = {
                                onChangeInterval(min); expanded = false
                            })
                        }
                    }
                }
            }

            Button(modifier = Modifier.align(Alignment.End), onClick = onSave) { Text("Save & Apply") }
        }
    }
}
