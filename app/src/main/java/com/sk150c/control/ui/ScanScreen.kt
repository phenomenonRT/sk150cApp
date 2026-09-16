package com.sk150c.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sk150c.control.R
import com.sk150c.control.ble.FoundDevice
import com.sk150c.control.ble.Transport
import com.sk150c.control.data.AppUiState
import com.sk150c.control.data.ConnectionPhase
import com.sk150c.control.data.SavedDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: AppUiState,
    viewModel: PowerSupplyViewModel,
    onRequestPermissions: () -> Unit
) {
    var deviceToRename by remember { mutableStateOf<SavedDevice?>(null) }
    var newNameText by remember { mutableStateOf("") }

    if (deviceToRename != null) {
        AlertDialog(
            onDismissRequest = { deviceToRename = null },
            title = { Text(stringResource(R.string.scan_dialog_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newNameText,
                    onValueChange = { newNameText = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deviceToRename?.let { viewModel.renameSavedDevice(it.address, newNameText) }
                    deviceToRename = null
                }) {
                    Text(stringResource(R.string.lab_btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRename = null }) {
                    Text(stringResource(R.string.lab_btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_header), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            
            Icon(
                Icons.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                stringResource(R.string.scan_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_grant_perms), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            // Scan Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.scan(Transport.BLE) },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.btn_scan_ble), 
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { viewModel.scan(Transport.CLASSIC) },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.btn_scan_classic), 
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            if (state.phase == ConnectionPhase.SCANNING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.message?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(msg, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // --- SAVED DEVICES SECTION ---
            if (state.savedDevices.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.scan_header_saved))
                Spacer(Modifier.height(8.dp))
                state.savedDevices.forEach { device ->
                    SavedDeviceRow(
                        device = device,
                        onConnect = { viewModel.connectSaved(device) },
                        onRename = { 
                            deviceToRename = device
                            newNameText = device.customName
                        },
                        onDelete = { viewModel.deleteSavedDevice(device.address) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // --- DISCOVERED DEVICES SECTION ---
            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(R.string.scan_header_found))
            Spacer(Modifier.height(8.dp))

            if (state.foundDevices.isEmpty() && state.phase != ConnectionPhase.SCANNING) {
                Text(
                    stringResource(R.string.scan_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                state.foundDevices.forEach { device ->
                    // Only show if not already saved
                    if (state.savedDevices.none { it.address == device.address }) {
                        FoundDeviceRow(
                            device = device,
                            onConnect = { viewModel.connect(device) },
                            onAdd = { viewModel.addSavedDevice(device) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (state.phase == ConnectionPhase.CONNECTING) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
                Text(stringResource(R.string.scan_connecting), modifier = Modifier.padding(top = 8.dp))
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.weight(2f).padding(start = 16.dp))
    }
}

@Composable
private fun SavedDeviceRow(
    device: SavedDevice,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (device.transport == Transport.BLE) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.customName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Rename", modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", modifier = Modifier.size(20.dp)) }
            Button(onClick = onConnect, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FoundDeviceRow(
    device: FoundDevice,
    onConnect: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (device.transport == Transport.BLE) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.scan_btn_add))
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onClick = onConnect) {
                Text("OK")
            }
        }
    }
}
