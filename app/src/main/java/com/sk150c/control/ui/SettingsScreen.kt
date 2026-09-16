package com.sk150c.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.os.PowerManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.sk150c.control.R
import com.sk150c.control.data.AppUiState
import com.sk150c.control.data.ValueDisplayPosition
import com.sk150c.control.modbus.Reg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    viewModel: PowerSupplyViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Section: Interface & Behavior ---
            SettingsSection(title = "Интерфейс и поведение") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_save_to_flash)) },
                    supportingContent = { Text(stringResource(R.string.desc_save_to_flash)) },
                    trailingContent = {
                        Switch(
                            checked = state.saveToFlash,
                            onCheckedChange = { viewModel.setSaveToFlash(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_temp_unit)) },
                    supportingContent = { Text("Выберите единицу измерения для отображения температуры") },
                    trailingContent = {
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = state.reading.tempUnit == "°C",
                                onClick = { viewModel.setTempUnit(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text("°C") }
                            SegmentedButton(
                                selected = state.reading.tempUnit == "°F",
                                onClick = { viewModel.setTempUnit(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text("°F") }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_rounding)) },
                    supportingContent = { Text(stringResource(R.string.desc_rounding)) },
                    trailingContent = {
                        Switch(
                            checked = state.roundingEnabled,
                            onCheckedChange = { viewModel.setRoundingEnabled(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_value_display_pos)) },
                    supportingContent = {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ValueDisplayPosition.entries.forEachIndexed { index, position ->
                                    SegmentedButton(
                                        selected = state.valueDisplayPosition == position,
                                        onClick = { viewModel.setValueDisplayPosition(position) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = ValueDisplayPosition.entries.size
                                        )
                                    ) {
                                        Text(
                                            when (position) {
                                                ValueDisplayPosition.INSIDE -> stringResource(R.string.pos_inside)
                                                ValueDisplayPosition.ABOVE -> stringResource(R.string.pos_above)
                                                ValueDisplayPosition.NONE -> stringResource(R.string.pos_none)
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_keep_panels_state)) },
                    supportingContent = { Text(stringResource(R.string.desc_keep_panels_state)) },
                    trailingContent = {
                        Switch(
                            checked = state.keepPanelsState,
                            onCheckedChange = { viewModel.setKeepPanelsState(it) }
                        )
                    }
                )
            }

            // --- Section: Device Settings ---
            SettingsSection(title = "Настройки прибора") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_sleep_timeout)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.desc_sleep_timeout))
                            Slider(
                                value = state.reading.sleepMin.toFloat(),
                                onValueChange = { viewModel.setSleepMinutes(it.toInt()) },
                                valueRange = 0f..60f,
                                steps = 11
                            )
                        }
                    },
                    trailingContent = {
                        Text(
                            stringResource(R.string.unit_minutes, state.reading.sleepMin),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_slave_address)) },
                    supportingContent = { Text("Текущий Modbus ID: ${state.reading.slaveAddress}") },
                    trailingContent = {
                        var text by remember { mutableStateOf(state.reading.slaveAddress.toString()) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { text.toIntOrNull()?.let { viewModel.setSlaveAddress(it) } }) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_baud_rate)) },
                    supportingContent = { Text("Текущая скорость: ${state.reading.baudRate}") },
                    trailingContent = {
                        val rates = listOf(9600, 19200, 38400, 57600, 115200)
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(state.reading.baudRate.toString())
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                rates.forEach { rate ->
                                    DropdownMenuItem(
                                        text = { Text(rate.toString()) },
                                        onClick = { viewModel.setBaudRate(rate); expanded = false }
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // --- Section: Calibration ---
            SettingsSection(title = stringResource(R.string.label_temp_calibration)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        var inOffset by remember { mutableStateOf(state.reading.tempInOffset.toString()) }
                        var exOffset by remember { mutableStateOf(state.reading.tempExOffset.toString()) }

                        OutlinedTextField(
                            value = inOffset,
                            onValueChange = { inOffset = it },
                            label = { Text(stringResource(R.string.label_t_in_offset)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailingIcon = {
                                IconButton(onClick = { inOffset.toDoubleOrNull()?.let { viewModel.setTempOffset(
                                    Reg.T_IN_OFFSET, it) } }) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                        OutlinedTextField(
                            value = exOffset,
                            onValueChange = { exOffset = it },
                            label = { Text(stringResource(R.string.label_t_ex_offset)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailingIcon = {
                                IconButton(onClick = { exOffset.toDoubleOrNull()?.let { viewModel.setTempOffset(
                                    Reg.T_EX_OFFSET, it) } }) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                    }
                    
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_close_mask)) },
                        supportingContent = { Text("Биты: ${state.reading.protectCloseMask.toString(2).padStart(8, '0')}") },
                        trailingContent = {
                            Text("0x${state.reading.protectCloseMask.toString(16).uppercase()}", fontWeight = FontWeight.Bold)
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Программная коррекция (Software Calibration)", style = MaterialTheme.typography.labelSmall)
                    
                    var vinOff by remember { mutableStateOf(state.vInOffset.toString()) }
                    var voutOff by remember { mutableStateOf(state.vOutOffset.toString()) }
                    var ioutOff by remember { mutableStateOf(state.iOutOffset.toString()) }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditFieldSmall(stringResource(R.string.label_vin_offset), vinOff, "V", Modifier.weight(1f)) {
                                vinOff = it
                                it.toDoubleOrNull()?.let { v -> viewModel.setVInOffset(v) }
                            }
                            EditFieldSmall(stringResource(R.string.label_vout_offset), voutOff, "V", Modifier.weight(1f)) {
                                voutOff = it
                                it.toDoubleOrNull()?.let { v -> viewModel.setVOutOffset(v) }
                            }
                        }
                        EditFieldSmall(stringResource(R.string.label_iout_offset), ioutOff, "A", Modifier.fillMaxWidth()) {
                            ioutOff = it
                            it.toDoubleOrNull()?.let { v -> viewModel.setIOutOffset(v) }
                        }
                    }
                }
            }

            // --- Section: System & Debug ---
            SettingsSection(title = "Система и отладка") {
                val context = LocalContext.current
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else true

                ListItem(
                    headlineContent = { Text("Бесперебойная работа") },
                    supportingContent = { 
                        Text(if (isIgnoring) "Приложение работает в фоновом режиме без ограничений" 
                             else "Нажмите, чтобы разрешить работу в фоне без отключений системой") 
                    },
                    trailingContent = {
                        if (!isIgnoring) {
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }) {
                                Text("Разрешить")
                            }
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_debug_mode)) },
                    supportingContent = { Text(stringResource(R.string.desc_debug_mode)) },
                    trailingContent = {
                        Switch(
                            checked = state.debugEnabled,
                            onCheckedChange = { viewModel.setDebugEnabled(it) }
                        )
                    }
                )
            }

            // --- Section: Device Info ---
            if (state.model.isNotEmpty()) {
                SettingsSection(title = stringResource(R.string.label_device_info)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_model)) },
                        trailingContent = { Text(state.model, fontWeight = FontWeight.Bold) }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_version)) },
                        trailingContent = { Text(state.version, fontWeight = FontWeight.Bold) }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_device_status)) },
                        trailingContent = { Text("0x${state.reading.deviceStatus.toString(16).uppercase()}", fontWeight = FontWeight.Bold) }
                    )
                }
            }
            
            val uriHandler = LocalUriHandler.current
            TextButton(
                onClick = { uriHandler.openUri("http://www.qingdaowuzhi.com/Content/488969.html") },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.label_official_site))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EditFieldSmall(label: String, value: String, unit: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        suffix = { Text(unit, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(content = content)
        }
    }
}
