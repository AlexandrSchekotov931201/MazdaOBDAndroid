package car.mazda.obd.android.core.elm.transport

internal sealed class ElmTransportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal class ElmTransportReadTimeoutException(
    val partialRaw: String,
    cause: Throwable,
) : ElmTransportException("Timed out waiting for ELM response", cause)
