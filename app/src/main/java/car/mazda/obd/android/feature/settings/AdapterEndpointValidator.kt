package car.mazda.obd.android.feature.settings

import car.mazda.obd.android.core.elm.transport.AdapterEndpoint

object AdapterEndpointValidator {
    fun validate(hostInput: String, portInput: String): Result<AdapterEndpoint> {
        val host = hostInput.trim()
        if (host.isEmpty()) return Result.failure(IllegalArgumentException("Enter the adapter IP address or host name"))
        if (host.length > 253 || host.any(Char::isWhitespace) || host.contains("://") || host.contains('/') || host.contains('\\')) {
            return Result.failure(IllegalArgumentException("Enter only an IP address or host name, without a protocol or path"))
        }
        val port = portInput.trim().toIntOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a numeric port"))
        if (port !in 1..65535) return Result.failure(IllegalArgumentException("Port must be between 1 and 65535"))
        return Result.success(AdapterEndpoint(host = host, port = port))
    }
}
