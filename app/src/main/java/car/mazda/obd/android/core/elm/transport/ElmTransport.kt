package car.mazda.obd.android.core.elm.transport

interface ElmTransport {
    suspend fun connect()

    suspend fun exchange(command: String, readTimeoutMs: Int): String

    fun disconnect()
}
