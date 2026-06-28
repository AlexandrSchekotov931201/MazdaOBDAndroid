package car.mazda.obd.android.feature.trip.route

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LocationPermissionCoordinator(
    private val activity: ComponentActivity,
    private val onPermissionGranted: () -> Unit,
) {
    var permissionGranted by mutableStateOf(false)
        private set

    var showSettingsPrompt by mutableStateOf(false)
        private set

    private var hasRequestedPermission = false
    private var awaitingSettingsResult = false

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionGranted = hasLocationPermission()
        if (permissionGranted) {
            onPermissionGranted()
        } else if (!canRequestPermissionAgain()) {
            showSettingsPrompt = true
        }
    }

    fun restoreState(savedInstanceState: Bundle?) {
        hasRequestedPermission = savedInstanceState?.getBoolean(KEY_PERMISSION_REQUESTED) == true
        awaitingSettingsResult = savedInstanceState?.getBoolean(KEY_AWAITING_SETTINGS) == true
        showSettingsPrompt = savedInstanceState?.getBoolean(KEY_SHOW_SETTINGS_PROMPT) == true
    }

    fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_PERMISSION_REQUESTED, hasRequestedPermission)
        outState.putBoolean(KEY_AWAITING_SETTINGS, awaitingSettingsResult)
        outState.putBoolean(KEY_SHOW_SETTINGS_PROMPT, showSettingsPrompt)
    }

    fun onResume() {
        permissionGranted = hasLocationPermission()
        if (!awaitingSettingsResult) return
        awaitingSettingsResult = false
        if (permissionGranted) {
            showSettingsPrompt = false
            onPermissionGranted()
        }
    }

    fun requestPermissionOrShowSettings() {
        if (hasLocationPermission()) {
            permissionGranted = true
            onPermissionGranted()
            return
        }
        if (hasRequestedPermission && !canRequestPermissionAgain()) {
            showSettingsPrompt = true
            return
        }
        hasRequestedPermission = true
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    fun openAppSettings() {
        showSettingsPrompt = false
        awaitingSettingsResult = true
        activity.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${activity.packageName}"),
            )
        )
    }

    fun dismissSettingsPrompt() {
        showSettingsPrompt = false
    }

    private fun canRequestPermissionAgain(): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val KEY_PERMISSION_REQUESTED = "location_permission_requested"
        const val KEY_AWAITING_SETTINGS = "awaiting_location_settings"
        const val KEY_SHOW_SETTINGS_PROMPT = "show_location_settings_prompt"
    }
}
