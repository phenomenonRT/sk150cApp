package com.sk150c.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sk150c.control.data.ConnectionPhase

@Composable
fun SK150CApp(
    viewModel: PowerSupplyViewModel,
    onRequestPermissions: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBatteryLab by remember { mutableStateOf(false) }

    if (showDiagnostics) {
        DiagnosticsScreen(onBack = { showDiagnostics = false })
        return
    }

    if (showSettings) {
        SettingsScreen(
            state = state,
            viewModel = viewModel,
            onBack = { showSettings = false }
        )
        return
    }

    if (showBatteryLab) {
        BatteryLabScreen(
            state = state,
            viewModel = viewModel,
            onBack = { showBatteryLab = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state.phase) {
                ConnectionPhase.LIVE -> DashboardScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenSettings = { showSettings = true },
                    onOpenBatteryLab = { showBatteryLab = true }
                )
                else -> ScanScreen(
                    state = state,
                    viewModel = viewModel,
                    onRequestPermissions = onRequestPermissions
                )
            }
        }
        
        if (state.debugEnabled) {
            FloatingActionButton(
                onClick = { showDiagnostics = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.BugReport, contentDescription = "Diagnostics")
            }
        }
    }
}
