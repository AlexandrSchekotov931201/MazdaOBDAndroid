package car.mazda.obd.android.core.elm.transport

data class AdapterEndpoint(
    val host: String,
    val port: Int,
) {
    override fun toString(): String = "$host:$port"
}
