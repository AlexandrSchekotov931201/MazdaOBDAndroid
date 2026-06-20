package car.mazda.obd.android.elm.exception

sealed class OBDException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Не подключены к Wi-Fi / нет сети / нет маршрута (скорее всего не в сети ELM) */
class NetworkUnavailableException(
    message: String = "Network unavailable",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Wi-Fi есть, но адаптер/хост недоступен (таймаут/connection refused) */
class AdapterUnreachableException(
    message: String = "Adapter unreachable",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Потеря соединения в процессе работы (read/write, socket closed) */
class LostConnectionException(
    message: String = "Lost connection",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Ошибка протокола/парсинга ответа ELM/OBD */
class ProtocolException(
    message: String = "Protocol error",
    cause: Throwable? = null
) : OBDException(message, cause)

/** Любая другая непредвиденная ошибка */
class UnknownObdException(
    message: String = "Unknown error",
    cause: Throwable? = null
) : OBDException(message, cause)
