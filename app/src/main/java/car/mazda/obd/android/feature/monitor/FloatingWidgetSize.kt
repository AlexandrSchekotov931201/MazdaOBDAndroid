package car.mazda.obd.android.feature.monitor

enum class FloatingWidgetSize(
    val label: String,
    val widthPx: Int,
    val heightPx: Int,
) {
    Small(
        label = "Small",
        widthPx = 360,
        heightPx = 220,
    ),
    Medium(
        label = "Medium",
        widthPx = 440,
        heightPx = 270,
    ),
    Large(
        label = "Large",
        widthPx = 540,
        heightPx = 330,
    ),
    ExtraLarge(
        label = "XL",
        widthPx = 660,
        heightPx = 400,
    );

    companion object {
        fun fromKey(key: String): FloatingWidgetSize =
            entries.firstOrNull { it.name == key } ?: Small
    }
}
