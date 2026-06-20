package car.mazda.obd.android.elm.exception

sealed class OBDException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Not connected to Wi-Fi, no route, or not on the ELM adapter network. */
class NetworkUnavailableException(
    message: String = "Network unavailable",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Wi-Fi is available, but the adapter host is unreachable or refused the connection. */
class AdapterUnreachableException(
    message: String = "Adapter unreachable",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Connection was lost while reading, writing, or when the remote socket closed. */
class LostConnectionException(
    message: String = "Lost connection",
    cause: Throwable? = null
) : OBDException(message, cause)

/** ELM/OBD response protocol or parsing error. */
class ProtocolException(
    message: String = "Protocol error",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Any unexpected OBD error that does not fit a narrower category. */
class UnknownObdException(
    message: String = "Unknown error",
    cause: Throwable? = null
) : OBDException(message, cause)
