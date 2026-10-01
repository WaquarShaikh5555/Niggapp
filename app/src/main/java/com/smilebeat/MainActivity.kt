package com.smilebeat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smilebeat.ui.screens.MainScreen
import com.smilebeat.ui.screens.PermissionScreen
import com.smilebeat.ui.screens.SettingsScreen
import com.smilebeat.ui.theme.SmileBeatTheme
import com.smilebeat.viewmodel.MainViewModel
import com.smilebeat.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var permissionDenied by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        mainViewModel.setPermissionGranted(isGranted)
        permissionDenied = !isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check initial permission
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        mainViewModel.setPermissionGranted(hasPermission)
        permissionDenied = !hasPermission

        setContent {
            SmileBeatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val permissionGranted by mainViewModel.permissionGranted.collectAsState()
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            if (permissionGranted) {
                                MainScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    }
                                )
                            } else {
                                PermissionScreen(
                                    isDenied = permissionDenied && !permissionGranted,
                                    onRequestPermission = {
                                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                )
                            }
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // Observe permission changes to re-check
                    LaunchedEffect(Unit) {
                        mainViewModel.permissionGranted.collect { granted ->
                            if (!granted) {
                                // If we haven't asked yet, show rationale first
                                // PermissionScreen will handle
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when returning from settings
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        mainViewModel.setPermissionGranted(hasPermission)
        if (hasPermission) permissionDenied = false
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.setCameraActive(false)
    }
}
