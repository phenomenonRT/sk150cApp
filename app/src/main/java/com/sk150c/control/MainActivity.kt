package com.sk150c.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.sk150c.control.ble.BtPermissions
import com.sk150c.control.ui.PowerSupplyViewModel
import com.sk150c.control.ui.SK150CApp
import com.sk150c.control.ui.theme.SK150CTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PowerSupplyViewModel by viewModels {
        PowerSupplyViewModel.Factory(application)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result observed via hasPermissions() below on next recomposition */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val missing = BtPermissions.required().filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            SK150CTheme {
                SK150CApp(viewModel = viewModel, onRequestPermissions = {
                    permissionLauncher.launch(BtPermissions.required())
                })
            }
        }
    }
}
