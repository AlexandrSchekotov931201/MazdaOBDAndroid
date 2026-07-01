package car.mazda.obd.android.feature.settings

import android.content.Context
import car.mazda.obd.android.core.elm.transport.AdapterEndpoint

class AdapterConnectionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): AdapterEndpoint? {
        val host = preferences.getString(KEY_HOST, null)?.takeIf(String::isNotBlank) ?: return null
        val port = preferences.getInt(KEY_PORT, INVALID_PORT).takeIf { it in VALID_PORTS } ?: return null
        return AdapterEndpoint(host = host, port = port)
    }

    fun loadVerified(): AdapterEndpoint? = load()?.takeIf { isVerified }

    val isVerified: Boolean
        get() = preferences.getBoolean(KEY_VERIFIED, preferences.contains(KEY_HOST))

    val onboardingCompleted: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, preferences.contains(KEY_HOST))

    fun savePending(endpoint: AdapterEndpoint) {
        val completedBeforeThisChange = onboardingCompleted
        preferences.edit()
            .putString(KEY_HOST, endpoint.host)
            .putInt(KEY_PORT, endpoint.port)
            .putBoolean(KEY_VERIFIED, false)
            .putBoolean(KEY_ONBOARDING_COMPLETED, completedBeforeThisChange)
            .apply()
    }

    fun markVerified(endpoint: AdapterEndpoint) {
        if (load() != endpoint) return
        preferences.edit()
            .putBoolean(KEY_VERIFIED, true)
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "adapter_connection_preferences"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_VERIFIED = "verified"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val INVALID_PORT = -1
        val VALID_PORTS = 1..65535
    }
}
