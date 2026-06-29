package car.mazda.obd.android.core.elm.transport

interface ElmTransport {
    suspend fun connect()

    suspend fun exchange(command: String, readTimeoutMs: Int): String

    fun disconnect()
}

internal class ElmTransportReadTimeoutException(
    val partialRaw: String,
    cause: Throwable,
) : Exception("Timed out waiting for ELM response", cause)
