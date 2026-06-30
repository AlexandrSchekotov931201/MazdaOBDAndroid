package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse

internal object OBDResponseCorrelator {
    fun correlate(
        response: OBDResponse,
        request: OBDRequest,
        preferredEcu: String?,
    ): OBDResponse {
        if (response !is OBDResponse.Data) return response

        val matchingPid = response.data.filter {
            it.pid.equals(request.responsePidHex, ignoreCase = true)
        }
        val matchingSource = preferredEcu?.let { source ->
            matchingPid.filter { it.canId.equals(source, ignoreCase = true) }
        }.orEmpty()

        if (matchingSource.isNotEmpty()) return OBDResponse.Data(matchingSource)
        if (matchingPid.isNotEmpty()) return OBDResponse.Data(matchingPid)

        val actualSources = response.data.map { "${it.canId}:${it.pid}" }
        return OBDResponse.NoData.Mismatched(
            raw = actualSources.joinToString(),
            expectedPid = request.responsePidHex,
            actualSources = actualSources,
        )
    }
}
