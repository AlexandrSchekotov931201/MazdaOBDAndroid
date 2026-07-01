package car.mazda.obd.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.dashboard.MainViewModel
import car.mazda.obd.android.feature.dashboard.MainViewModelFactory
import car.mazda.obd.android.feature.dashboard.ui.MainScreen
import car.mazda.obd.android.feature.logs.LogsScreen
import car.mazda.obd.android.feature.monitor.ObdMonitorStateStore
import car.mazda.obd.android.feature.monitor.ObdMonitorService
import car.mazda.obd.android.feature.settings.SettingsScreen
import car.mazda.obd.android.feature.settings.AdapterConnectionPreferences
import car.mazda.obd.android.feature.settings.AdapterOnboardingScreen
import car.mazda.obd.android.feature.trip.summary.TripsScreen
import car.mazda.obd.android.feature.trip.route.TripRouteSettingsViewModel
import car.mazda.obd.android.feature.trip.route.TripRouteSettingsViewModelFactory
import car.mazda.obd.android.feature.trip.route.TripRouteViewModel
import car.mazda.obd.android.feature.trip.route.TripRouteViewModelFactory
import car.mazda.obd.android.feature.trip.route.TripRouteScreen
import car.mazda.obd.android.feature.trip.route.LocationPermissionSettingsDialog
import car.mazda.obd.android.feature.trip.route.LocationPermissionCoordinator
import car.mazda.obd.android.ui.theme.MazdaOBDAndroidTheme
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {

    private val adapterConnectionPreferences by lazy { AdapterConnectionPreferences(applicationContext) }

    private val viewModelFactory by lazy {
        MainViewModelFactory(applicationContext)
    }

    private val viewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory
        )[MainViewModel::class.java]
    }

    private val routeSettingsViewModel by lazy {
        ViewModelProvider(
            this,
            TripRouteSettingsViewModelFactory(applicationContext),
        )[TripRouteSettingsViewModel::class.java]
    }

    private val tripRouteViewModel by lazy {
        ViewModelProvider(
            this,
            TripRouteViewModelFactory(applicationContext),
        )[TripRouteViewModel::class.java]
    }

    private val locationPermissionCoordinator = LocationPermissionCoordinator(this) {
        routeSettingsViewModel.setRecordingEnabled(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionCoordinator.restoreState(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MazdaOBDAndroidTheme {
                var adapterEndpoint by remember {
                    mutableStateOf(adapterConnectionPreferences.load())
                }
                var onboardingCompleted by remember {
                    mutableStateOf(adapterConnectionPreferences.onboardingCompleted)
                }
                var pendingOnboardingEndpoint by remember { mutableStateOf<car.mazda.obd.android.core.elm.transport.AdapterEndpoint?>(null) }
                val monitorState by ObdMonitorStateStore.state.collectAsStateWithLifecycle()
                androidx.compose.runtime.LaunchedEffect(monitorState.connectionStatus, pendingOnboardingEndpoint) {
                    if (pendingOnboardingEndpoint != null && adapterConnectionPreferences.isVerified) {
                        adapterEndpoint = adapterConnectionPreferences.loadVerified()
                        onboardingCompleted = true
                        pendingOnboardingEndpoint = null
                        requestWifiPermission()
                        requestNotificationPermission()
                    }
                }
                if (!onboardingCompleted) {
                    AdapterOnboardingScreen(
                        onSave = { endpoint ->
                            adapterConnectionPreferences.savePending(endpoint)
                            pendingOnboardingEndpoint = endpoint
                            ObdMonitorService.validateSavedEndpoint(applicationContext)
                        },
                        onSkip = {
                            adapterConnectionPreferences.completeOnboardingOffline()
                            onboardingCompleted = true
                            ObdMonitorStateStore.update {
                                it.copy(
                                    connectionStatus = car.mazda.obd.android.feature.monitor.MonitorConnectionStatus.Offline,
                                    connectionError = "Offline mode. Add adapter details in Settings to connect.",
                                )
                            }
                        },
                    )
                } else {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var screen by rememberSaveable { mutableStateOf("main") }
                val connectionStatus by viewModel.connectionStatusState.collectAsStateWithLifecycle()
                val activeTrip by viewModel.activeTripSummaryState.collectAsStateWithLifecycle()
                val routeState by tripRouteViewModel.state.collectAsStateWithLifecycle()
                val selectedDestination = when (screen) {
                    "trips" -> AppDrawerDestination.Trips
                    "trip_route" -> AppDrawerDestination.Trips
                    "logs" -> AppDrawerDestination.Logs
                    "settings" -> AppDrawerDestination.Settings
                    else -> AppDrawerDestination.Dashboard
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = screen != "trip_route" || drawerState.isOpen,
                    drawerContent = {
                        AppDrawer(
                            selectedDestination = selectedDestination,
                            connectionStatus = connectionStatus,
                            onSelectDestination = { destination ->
                                screen = when (destination) {
                                    AppDrawerDestination.Dashboard -> "main"
                                    AppDrawerDestination.Trips -> "trips"
                                    AppDrawerDestination.Logs -> "logs"
                                    AppDrawerDestination.Settings -> "settings"
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        val openDrawer = {
                            scope.launch { drawerState.open() }
                            Unit
                        }

                        when (screen) {
                            "main" -> MainScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                onOpenMenu = openDrawer,
                            )

                            "logs" -> LogsScreen(
                                modifier = Modifier.padding(innerPadding),
                                onOpenMenu = openDrawer
                            )

                            "trips" -> TripsScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                onOpenMenu = openDrawer,
                                onOpenTripRoute = { startedAtMs ->
                                    tripRouteViewModel.showTrip(startedAtMs)
                                    screen = "trip_route"
                                }
                            )

                            "trip_route" -> TripRouteScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = tripRouteViewModel,
                                isActiveTrip = activeTrip?.startedAtMs == routeState.tripStartedAtMs,
                                onOpenDrawer = openDrawer,
                                onBack = {
                                    tripRouteViewModel.clearSelection()
                                    screen = "trips"
                                },
                            )

                            "settings" -> SettingsScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                routeSettingsViewModel = routeSettingsViewModel,
                                onOpenMenu = openDrawer,
                                onRequestOverlayPermission = ::openOverlayPermissionSettings,
                                locationPermissionGranted = locationPermissionCoordinator.permissionGranted,
                                onSetRouteRecordingEnabled = ::setRouteRecordingEnabled,
                                adapterEndpoint = adapterEndpoint,
                                onSaveAdapterEndpoint = { endpoint ->
                                    adapterConnectionPreferences.savePending(endpoint)
                                    adapterEndpoint = endpoint
                                    ObdMonitorService.validateSavedEndpoint(applicationContext)
                                },
                            )
                        }
                    }
                }

                if (locationPermissionCoordinator.showSettingsPrompt) {
                    LocationPermissionSettingsDialog(
                        onOpenSettings = locationPermissionCoordinator::openAppSettings,
                        onDismiss = locationPermissionCoordinator::dismissSettingsPrompt,
                    )
                }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ObdMonitorStateStore.update { it.copy(isAppForeground = true) }
        if (adapterConnectionPreferences.loadVerified() != null) {
            requestWifiPermission()
            requestNotificationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        locationPermissionCoordinator.onResume()
        if (!locationPermissionCoordinator.permissionGranted && routeSettingsViewModel.recordingEnabled.value) {
            routeSettingsViewModel.setRecordingEnabled(false)
        } else if (locationPermissionCoordinator.permissionGranted &&
            routeSettingsViewModel.recordingEnabled.value &&
            adapterConnectionPreferences.loadVerified() != null
        ) {
            ObdMonitorService.refreshRouteRecording(applicationContext)
        }
    }

    override fun onStop() {
        ObdMonitorStateStore.update { it.copy(isAppForeground = false) }
        super.onStop()
    }

    private fun requestWifiPermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                    1001
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1002
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1003
            )
        }
    }

    private fun openOverlayPermissionSettings() {
        if (Settings.canDrawOverlays(this)) return
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        locationPermissionCoordinator.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setRouteRecordingEnabled(enabled: Boolean) {
        if (!enabled) {
            routeSettingsViewModel.setRecordingEnabled(false)
            return
        }
        locationPermissionCoordinator.requestPermissionOrShowSettings()
    }
}
