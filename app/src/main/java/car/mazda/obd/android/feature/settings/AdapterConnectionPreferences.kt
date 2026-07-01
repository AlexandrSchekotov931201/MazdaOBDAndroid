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

    fun save(endpoint: AdapterEndpoint) {
        preferences.edit()
            .putString(KEY_HOST, endpoint.host)
            .putInt(KEY_PORT, endpoint.port)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "adapter_connection_preferences"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val INVALID_PORT = -1
        val VALID_PORTS = 1..65535
    }
}
