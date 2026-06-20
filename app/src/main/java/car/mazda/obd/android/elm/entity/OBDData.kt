package car.mazda.obd.android.elm.entity

data class OBDData(
    val canId: String,
    val pid: String,
    val data: List<String>,
)