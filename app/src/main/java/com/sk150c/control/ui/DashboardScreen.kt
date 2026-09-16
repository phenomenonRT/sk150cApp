package com.sk150c.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sk150c.control.R
import com.sk150c.control.data.AppUiState
import com.sk150c.control.data.MemoryGroup
import com.sk150c.control.data.ValueDisplayPosition
import com.sk150c.control.modbus.ProtectStatus
import com.sk150c.control.ui.components.RotaryKnob
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: AppUiState,
    viewModel: PowerSupplyViewModel,
    onOpenSettings: () -> Unit,
    onOpenBatteryLab: () -> Unit
) {
    val reading = state.reading

    var vInput by remember { mutableStateOf("%.2f".format(reading.vSet).replace(',', '.')) }
    var iInput by remember { mutableStateOf("%.3f".format(reading.iSet).replace(',', '.')) }
    var editingVoltage by remember { mutableStateOf(false) }
    var editingCurrent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.app_header), fontWeight = FontWeight.Bold)
                        if (state.model.isNotEmpty()) {
                            Text(
                                "${state.model} ${state.version}", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setSaveToFlash(!state.saveToFlash) }) {
                        Icon(
                            if (state.saveToFlash) Icons.Filled.Save else Icons.Filled.FlashOff,
                            contentDescription = stringResource(R.string.label_save_to_flash),
                            tint = if (state.saveToFlash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onOpenBatteryLab) {
                        Icon(
                            Icons.Filled.BatteryChargingFull,
                            contentDescription = stringResource(R.string.lab_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.title_settings)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(
                                Icons.Filled.BluetoothConnected,
                                contentDescription = stringResource(R.string.btn_disconnect),
                                tint = if (state.isStale) Color(0xFFFFA000)
                                       else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.isStale) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFA000)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (reading.protectCode != 0) {
                val protectResId = ProtectStatus.getResourceId(reading.protectCode)
                val protectDesc = if (protectResId != 0) stringResource(protectResId) else "Unknown (${reading.protectCode})"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.dashboard_protect_tripped, protectDesc),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { viewModel.resetProtection() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onError,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(stringResource(R.string.btn_reset_protect), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Primary Readouts Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        BigReadout(stringResource(R.string.label_vout), "%.2f".format(reading.vOut), "V", Modifier.weight(1.2f))
                        BigReadout(stringResource(R.string.label_iout), "%.3f".format(reading.iOut), "A", Modifier.weight(1f))
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.label_power), style = MaterialTheme.typography.labelLarge)
                            Text(
                                "%.1f W".format(reading.power),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        VerticalDivider(modifier = Modifier.height(48.dp).padding(horizontal = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.label_vin), style = MaterialTheme.typography.labelLarge)
                            Text("%.2f V".format(reading.vIn), 
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SmallReadout(stringResource(R.string.label_mode), if (reading.isConstantCurrent) "CC" else "CV")
                        SmallReadout(stringResource(R.string.label_t_in), "%.1f%s".format(reading.tIn, reading.tempUnit))
                        SmallReadout(stringResource(R.string.label_t_ex), "%.1f%s".format(reading.tEx, reading.tempUnit))
                    }
                }
            }

            // High-reachability Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val outputOnColor = if (reading.outputOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val outputContentColor = if (reading.outputOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                
                Button(
                    onClick = { viewModel.setOutputOn(!reading.outputOn) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = outputOnColor,
                        contentColor = outputContentColor
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        if (reading.outputOn) Icons.Filled.PowerSettingsNew else Icons.Filled.PowerSettingsNew,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (reading.outputOn) stringResource(R.string.btn_output_on) else stringResource(R.string.btn_output_off),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = { viewModel.setLocked(!reading.locked) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (reading.locked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        if (reading.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (reading.locked) stringResource(R.string.btn_locked) else stringResource(R.string.btn_unlocked),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Setpoint Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    SetpointControl(
                        label = "V-SET",
                        value = vInput,
                        onValueChange = { vInput = it; editingVoltage = true },
                        onApply = {
                            val v = vInput.replace(',', '.').toDoubleOrNull()
                            if (v != null) {
                                viewModel.setVoltage(v)
                                editingVoltage = false
                            }
                        },
                        steps = listOf(-1.0, -0.1, 0.1, 1.0),
                        unit = "V",
                        format = "%.2f",
                        range = 0f..36f,
                        roundingEnabled = state.roundingEnabled,
                        valueDisplayPosition = state.valueDisplayPosition,
                        saveToFlash = state.saveToFlash,
                        modifier = Modifier.weight(1f)
                    )

                    SetpointControl(
                        label = "I-SET",
                        value = iInput,
                        onValueChange = { iInput = it; editingCurrent = true },
                        onApply = {
                            val i = iInput.replace(',', '.').toDoubleOrNull()
                            if (i != null) {
                                viewModel.setCurrent(i)
                                editingCurrent = false
                            }
                        },
                        steps = listOf(-0.1, -0.01, 0.01, 0.1),
                        unit = "A",
                        format = "%.3f",
                        range = 0f..5.1f,
                        roundingEnabled = state.roundingEnabled,
                        valueDisplayPosition = state.valueDisplayPosition,
                        saveToFlash = state.saveToFlash,
                        modifier = Modifier.weight(1f)
                    )
                }

            LaunchedEffect(reading.vSet) {
                if (!editingVoltage) {
                    vInput = "%.2f".format(reading.vSet).replace(',', '.')
                }
            }
            LaunchedEffect(reading.iSet) {
                if (!editingCurrent) {
                    iInput = "%.3f".format(reading.iSet).replace(',', '.')
                }
            }

            // Memory Groups
            CollapsibleSection(
                title = stringResource(R.string.dashboard_recall),
                icon = Icons.Filled.Storage,
                expanded = state.expandedPanels.contains("memory"),
                onToggle = { viewModel.togglePanel("memory", it) }
            ) {
                var editingGroup by remember { mutableStateOf<MemoryGroup?>(null) }
                
                LaunchedEffect(Unit) {
                    if (state.memoryGroups.isEmpty()) viewModel.fetchAllMemoryGroups()
                }
                if (state.loadingMemory) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (g in 0..10) {
                        MemoryGroupCard(
                            index = g,
                            group = state.memoryGroups[g],
                            onClick = { viewModel.recallGroup(g) },
                            onEdit = { editingGroup = state.memoryGroups[g] }
                        )
                    }
                }
                
                if (editingGroup != null) {
                    MemoryGroupEditDialog(
                        group = editingGroup!!,
                        onDismiss = { editingGroup = null },
                        onSave = { 
                            viewModel.updateMemoryGroup(it)
                            editingGroup = null
                        }
                    )
                }
            }

            // Protection Settings
            CollapsibleSection(
                title = stringResource(R.string.dashboard_protection_settings),
                icon = Icons.Outlined.Shield,
                expanded = state.expandedPanels.contains("protection"),
                onToggle = { viewModel.togglePanel("protection", it) }
            ) {
                ProtectionLimitRow(stringResource(R.string.label_lvp_limit), reading.limitLup, "V") { viewModel.setLimitLup(it) }
                ProtectionLimitRow(stringResource(R.string.label_ovp_limit), reading.limitOvp, "V") { viewModel.setLimitOvp(it) }
                ProtectionLimitRow(stringResource(R.string.label_ocp_limit), reading.limitOcp, "A") { viewModel.setLimitOcp(it) }
                ProtectionLimitRow(stringResource(R.string.label_opp_limit), reading.limitOpp, "W") { viewModel.setLimitOpp(it) }
                ProtectionLimitRow(stringResource(R.string.label_otp_limit), reading.limitOtp, "\u00b0C") { viewModel.setLimitOtp(it) }
                ProtectionLimitRow(stringResource(R.string.label_oah_limit), reading.limitOah, "Ah") { viewModel.setLimitOah(it) }
                ProtectionLimitRow(stringResource(R.string.label_owh_limit), reading.limitOwh, "Wh") { viewModel.setLimitOwh(it) }
                TimeLimitRow(stringResource(R.string.label_ohp_limit), reading.limitOhpH, reading.limitOhpM) { h, m -> viewModel.setLimitOhp(h, m) }
                
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                var maskText by remember(reading.protectCloseMask) { mutableStateOf(reading.protectCloseMask.toString()) }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_close_mask), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = maskText, onValueChange = { maskText = it }, modifier = Modifier.width(110.dp),
                        textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, suffix = { Text("Hex", style = MaterialTheme.typography.labelSmall) }, shape = MaterialTheme.shapes.small
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { 
                        maskText.toIntOrNull()?.let { 
                            viewModel.updateMemoryGroup(state.memoryGroups[0]?.copy(closeMask = it) ?: return@IconButton) 
                        } 
                    }) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.btn_apply), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Display and Sound
            CollapsibleSection(
                title = stringResource(R.string.dashboard_display_sound),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                expanded = state.expandedPanels.contains("display"),
                onToggle = { viewModel.togglePanel("display", it) }
            ) {
                Text(
                    "${stringResource(R.string.label_backlight)}: ${reading.backlight}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = reading.backlight.toFloat(),
                    onValueChange = { viewModel.setBacklight(it.roundToInt()) },
                    valueRange = 0f..5f,
                    steps = 4
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_buzzer), style = MaterialTheme.typography.bodyLarge)
                    var buzzerOn by remember { mutableStateOf(true) }
                    Switch(checked = buzzerOn, onCheckedChange = {
                        buzzerOn = it
                        viewModel.setBuzzer(it)
                    })
                }
            }

            // Stats
            CollapsibleSection(
                title = stringResource(R.string.dashboard_stats),
                icon = Icons.Filled.BarChart,
                expanded = state.expandedPanels.contains("stats"),
                onToggle = { viewModel.togglePanel("stats", it) },
                action = {
                    IconButton(onClick = { viewModel.zeroStats() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_reset_stats), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            ) {
                StatRow(Icons.Filled.Timer, stringResource(R.string.label_on_time), "%02d:%02d:%02d".format(reading.outH, reading.outM, reading.outS))
                StatRow(Icons.Filled.BatteryFull, stringResource(R.string.label_capacity), "%.3f Ah".format(reading.capacityAh))
                StatRow(Icons.Filled.Bolt, stringResource(R.string.label_energy), "%.3f Wh".format(reading.energyWh))
            }

            // System Settings
            CollapsibleSection(
                title = stringResource(R.string.dashboard_system_settings),
                icon = Icons.Filled.SettingsSuggest,
                expanded = state.expandedPanels.contains("system"),
                onToggle = { viewModel.togglePanel("system", it) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_power_on_state), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = reading.powerOnOutput, onCheckedChange = { viewModel.setPowerOnOutput(it) })
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_reboot))
                    }
                    OutlinedButton(
                        onClick = { viewModel.restoreFactory() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_factory_reset))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SetpointControl(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    steps: List<Double>,
    unit: String,
    format: String,
    range: ClosedFloatingPointRange<Float>,
    roundingEnabled: Boolean,
    valueDisplayPosition: ValueDisplayPosition,
    saveToFlash: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))

            if (valueDisplayPosition == ValueDisplayPosition.ABOVE) {
                Text(
                    text = "$value $unit",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val precision = try { format.filter { it.isDigit() }.toInt() } catch (_: Exception) { 2 }
                val knobStep = if (roundingEnabled) { if (unit == "V") 0.1f else 0.01f } else null

                RotaryKnob(
                    value = value.replace(',', '.').toFloatOrNull() ?: 0f,
                    onValueChange = { newVal -> onValueChange(format.format(newVal).replace(',', '.')) },
                    range = range,
                    size = 130.dp,
                    unit = unit,
                    precision = precision,
                    step = knobStep,
                    showValue = valueDisplayPosition == ValueDisplayPosition.INSIDE
                )
            }

            Spacer(Modifier.height(4.dp))
            
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text(unit, style = MaterialTheme.typography.labelSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = onApply,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = if (!saveToFlash) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Icon(
                        if (saveToFlash) Icons.Filled.Check else Icons.Filled.Timer,
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (saveToFlash) stringResource(R.string.btn_apply) else "Temp Apply",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.chunked(2).forEach { rowSteps ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowSteps.forEach { step ->
                            val sign = if (step > 0) "+" else ""
                            FilledTonalButton(
                                onClick = {
                                    val current = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val newVal = (current + step).coerceAtLeast(0.0)
                                    onValueChange(format.format(newVal).replace(',', '.'))
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("$sign$step", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    action?.invoke()
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
private fun MemoryGroupCard(
    index: Int,
    group: MemoryGroup?,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "M$index", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (group != null) {
                        Text(text = "${"%.2f".format(group.vSet)}V / ${"%.3f".format(group.iSet)}A", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(text = "---", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (group?.powerOn == true) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Power, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = stringResource(R.string.label_power_on_state), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.btn_edit_group), tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun MemoryGroupEditDialog(
    group: MemoryGroup,
    onDismiss: () -> Unit,
    onSave: (MemoryGroup) -> Unit
) {
    var vSet by remember { mutableStateOf(group.vSet.toString()) }
    var iSet by remember { mutableStateOf(group.iSet.toString()) }
    var lvp by remember { mutableStateOf(group.lvp.toString()) }
    var ovp by remember { mutableStateOf(group.ovp.toString()) }
    var ocp by remember { mutableStateOf(group.ocp.toString()) }
    var opp by remember { mutableStateOf(group.opp.toString()) }
    var ohpH by remember { mutableStateOf(group.ohpH.toString()) }
    var ohpM by remember { mutableStateOf(group.ohpM.toString()) }
    var oah by remember { mutableStateOf(group.oah.toString()) }
    var owh by remember { mutableStateOf(group.owh.toString()) }
    var otp by remember { mutableStateOf(group.otp.toString()) }
    var powerOn by remember { mutableStateOf(group.powerOn) }
    var closeMask by remember { mutableStateOf(group.closeMask.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile M${group.index}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField("V-SET", vSet, "V", Modifier.weight(1f)) { vSet = it }
                    EditField("I-SET", iSet, "A", Modifier.weight(1f)) { iSet = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField("LVP", lvp, "V", Modifier.weight(1f)) { lvp = it }
                    EditField("OVP", ovp, "V", Modifier.weight(1f)) { ovp = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField("OCP", ocp, "A", Modifier.weight(1f)) { ocp = it }
                    EditField("OPP", opp, "W", Modifier.weight(1f)) { opp = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField("OHP H", ohpH, "h", Modifier.weight(1f)) { ohpH = it }
                    EditField("OHP M", ohpM, "m", Modifier.weight(1f)) { ohpM = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField("OAH", oah, "Ah", Modifier.weight(1f)) { oah = it }
                    EditField("OWH", owh, "Wh", Modifier.weight(1f)) { owh = it }
                }
                EditField("OTP", otp, "°C", Modifier.fillMaxWidth()) { otp = it }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Power-on Output", modifier = Modifier.weight(1f))
                    Switch(checked = powerOn, onCheckedChange = { powerOn = it })
                }
                EditField("Close Mask (S-CLOSE)", closeMask, "Hex", Modifier.fillMaxWidth()) { closeMask = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(group.copy(
                    vSet = vSet.toDoubleOrNull() ?: group.vSet,
                    iSet = iSet.toDoubleOrNull() ?: group.iSet,
                    lvp = lvp.toDoubleOrNull() ?: group.lvp,
                    ovp = ovp.toDoubleOrNull() ?: group.ovp,
                    ocp = ocp.toDoubleOrNull() ?: group.ocp,
                    opp = opp.toDoubleOrNull() ?: group.opp,
                    ohpH = ohpH.toIntOrNull() ?: group.ohpH,
                    ohpM = ohpM.toIntOrNull() ?: group.ohpM,
                    oah = oah.toDoubleOrNull() ?: group.oah,
                    owh = owh.toDoubleOrNull() ?: group.owh,
                    otp = otp.toDoubleOrNull() ?: group.otp,
                    powerOn = powerOn,
                    closeMask = closeMask.toIntOrNull() ?: group.closeMask
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditField(label: String, value: String, unit: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ProtectionLimitRow(
    label: String,
    currentValue: Double,
    unit: String,
    onApply: (Double) -> Unit
) {
    var textValue by remember(currentValue) { mutableStateOf("%.2f".format(currentValue)) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = textValue, onValueChange = { textValue = it }, modifier = Modifier.width(110.dp),
            textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, suffix = { Text(unit, style = MaterialTheme.typography.labelMedium) }, shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { textValue.toDoubleOrNull()?.let { onApply(it) } }) {
            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.btn_apply), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TimeLimitRow(
    label: String,
    currentHours: Int,
    currentMinutes: Int,
    onApply: (Int, Int) -> Unit
) {
    var hours by remember(currentHours) { mutableStateOf(currentHours.toString()) }
    var minutes by remember(currentMinutes) { mutableStateOf(currentMinutes.toString()) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = hours, onValueChange = { hours = it }, modifier = Modifier.width(80.dp),
            textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, suffix = { Text("h", style = MaterialTheme.typography.labelSmall) }, shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = minutes, onValueChange = { minutes = it }, modifier = Modifier.width(80.dp),
            textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, suffix = { Text("m", style = MaterialTheme.typography.labelSmall) }, shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onApply(hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0) }) {
            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.btn_apply), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BigReadout(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.Start, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp), fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SmallReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}
