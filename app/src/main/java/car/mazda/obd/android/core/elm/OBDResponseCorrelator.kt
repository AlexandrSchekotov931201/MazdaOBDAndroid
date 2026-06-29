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
            it.pid.equals(request.responsePid, ignoreCase = true)
        }
        val matchingSource = preferredEcu?.let { source ->
            matchingPid.filter { it.canId.equals(source, ignoreCase = true) }
        } ?: matchingPid

        if (matchingSource.isNotEmpty()) return OBDResponse.Data(matchingSource)

        val actualSources = response.data.map { "${it.canId}:${it.pid}" }
        return OBDResponse.NoData.Mismatched(
            raw = actualSources.joinToString(),
            expectedPid = request.responsePid,
            actualSources = actualSources,
        )
    }
}
