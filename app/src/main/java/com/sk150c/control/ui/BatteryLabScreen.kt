package com.sk150c.control.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sk150c.control.R
import com.sk150c.control.data.AppUiState
import com.sk150c.control.data.db.BatteryProfile
import com.sk150c.control.data.db.ChargingSession
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryLabScreen(
    state: AppUiState,
    viewModel: PowerSupplyViewModel,
    onBack: () -> Unit
) {
    val profiles by viewModel.batteryProfiles.collectAsState(initial = emptyList())
    val history by viewModel.chargingHistory.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val view = LocalView.current

    // Keep screen on during active charging
    SideEffect {
        view.keepScreenOn = state.chargingActive
    }

    var showAddProfileDialog by remember { mutableStateOf(false) }
    var profileToStart by remember { mutableStateOf<BatteryProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val csv = generateCsv(history)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.lab_export_chooser_title)))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.lab_btn_export_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddProfileDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lab_btn_add_profile_cd))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Section: Active Charge
            if (state.chargingActive && state.activeProfile != null) {
                item {
                    ActiveChargeCard(
                        profile = state.activeProfile!!,
                        reading = state.reading,
                        onStop = { viewModel.stopCharging() }
                    )
                }
            }

            // Middle Section: Profiles
            item {
                Text(
                    stringResource(R.string.lab_profiles_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

            }

            items(profiles) { profile ->
                ProfileCard(
                    profile = profile,
                    onStart = { profileToStart = profile },
                    onDelete = { viewModel.deleteProfile(profile) }
                )
            }

            // Bottom Section: History
            item {
                Text(
                    stringResource(R.string.lab_history_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            items(history) { session ->
                HistoryItemCard(
                    session = session,
                    onDelete = { viewModel.deleteSession(session.id) }
                )
            }
        }
    }

    if (showAddProfileDialog) {
        AddProfileDialog(
            onDismiss = { showAddProfileDialog = false },
            onSave = { viewModel.saveProfile(it) }
        )
    }

    profileToStart?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToStart = null },
            title = { Text(stringResource(R.string.lab_dialog_start_title)) },
            text = { Text(stringResource(R.string.lab_dialog_start_text, profile.name)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.startCharging(profile)
                    profileToStart = null
                }) {
                    Text(stringResource(R.string.lab_btn_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToStart = null }) {
                    Text(stringResource(R.string.lab_btn_cancel))
                }
            }
        )
    }
}

@Composable
fun ActiveChargeCard(
    profile: BatteryProfile,
    reading: com.sk150c.control.data.PowerReading,
    onStop: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.lab_active_charging_title, profile.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.lab_current_label, reading.vOut, reading.iOut), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.3f Ah".format(reading.capacityAh), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("%.3f Wh".format(reading.energyWh), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lab_btn_stop))
            }
        }
    }
}

@Composable
fun ProfileCard(
    profile: BatteryProfile,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${stringResource(R.string.lab_field_end_i)}: ${profile.endCurrent}A", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.lab_btn_start_cd), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lab_btn_delete_cd))
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    session: ChargingSession,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.profileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(sdf.format(Date(session.startTime)), style = MaterialTheme.typography.bodySmall)
                Text("%.3f Ah, %.3f Wh".format(session.chargedAh, session.chargedWh), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.lab_status_prefix, session.status), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lab_btn_delete_cd), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onSave: (BatteryProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var endI by remember { mutableStateOf("0.05") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lab_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.lab_field_name)) })
                OutlinedTextField(
                    value = endI,
                    onValueChange = { endI = it },
                    label = { Text(stringResource(R.string.lab_field_end_i)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val ei = endI.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) {
                    onSave(BatteryProfile(name = name, endCurrent = ei))
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.lab_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.lab_btn_cancel))
            }
        }
    )
}

private fun generateCsv(history: List<ChargingSession>): String {
    val sb = StringBuilder()
    sb.append("Date,Profile,Ah,Wh,Status\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    history.forEach {
        sb.append("${sdf.format(Date(it.startTime))},${it.profileName},${it.chargedAh},${it.chargedWh},${it.status}\n")
    }
    return sb.toString()
}
