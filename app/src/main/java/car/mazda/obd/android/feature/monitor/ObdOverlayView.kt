package car.mazda.obd.android.feature.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.View
import car.mazda.obd.android.feature.warmup.EngineWarmupGuidance
import car.mazda.obd.android.feature.warmup.EngineWarmupStage
import kotlin.math.min

class ObdOverlayView(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 12, 16, 22)
    }
    private val arcBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(52, 64, 84)
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }
    private val arcValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 245, 245)
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 68, 68)
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(203, 213, 225)
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var state = ObdMonitorState()

    fun render(nextState: ObdMonitorState) {
        state = nextState
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val minDim = min(width.toFloat(), height.toFloat())
        val radius = minDim * 0.11f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)

        val horizontalPadding = width * 0.06f
        val tachSize = min(height * 0.72f, width * 0.35f)
        arcBasePaint.strokeWidth = tachSize * 0.09f
        arcValuePaint.strokeWidth = tachSize * 0.045f
        redPaint.strokeWidth = tachSize * 0.09f

        val left = horizontalPadding
        val top = (height - tachSize) / 2f
        val rect = RectF(left, top, left + tachSize, top + tachSize)
        val progress = (state.rpm / MAX_RPM.toFloat()).coerceIn(0f, 1f)

        canvas.drawArc(rect, START_ANGLE, SWEEP_ANGLE, false, arcBasePaint)
        canvas.drawArc(rect, RED_START_ANGLE, RED_SWEEP_ANGLE, false, redPaint)
        canvas.drawArc(rect, START_ANGLE, SWEEP_ANGLE * progress, false, arcValuePaint)

        textPaint.textSize = tachSize * 0.23f
        canvas.drawText(state.rpm.toString(), rect.centerX(), rect.centerY() + tachSize * 0.05f, textPaint)
        resetLabelPaint()
        labelPaint.textSize = tachSize * 0.10f
        canvas.drawText("RPM", rect.centerX(), rect.centerY() + tachSize * 0.21f, labelPaint)

        val textLeft = left + tachSize + width * 0.06f
        val textRight = width - horizontalPadding
        val textWidth = (textRight - textLeft).coerceAtLeast(0f)
        drawConnectionChip(
            canvas = canvas,
            status = state.connectionStatus,
            left = textLeft,
            top = height * 0.22f,
            width = textWidth,
            height = minDim * 0.20f,
            minDim = minDim,
        )

        val coolant = state.coolantTemp?.let { "${it}C" } ?: "--"
        drawBadge(
            canvas = canvas,
            text = "Coolant: $coolant",
            left = textLeft,
            top = height * 0.52f,
            width = textWidth,
            height = minDim * 0.20f,
            statusColors = state.coolantTemp.coolantStatusColors(),
            minDim = minDim,
        )
        labelPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawConnectionChip(
        canvas: Canvas,
        status: MonitorConnectionStatus,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        minDim: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        val radius = height * 0.24f
        val rect = RectF(left, top, left + width, top + height)

        badgePaint.color = Color.rgb(233, 235, 242)
        canvas.drawRoundRect(rect, radius, radius, badgePaint)

        val dotRadius = height * 0.13f
        val dotCenterX = left + height * 0.40f
        val dotCenterY = top + height * 0.50f
        badgePaint.color = status.indicatorColor()
        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, badgePaint)

        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = minDim * 0.085f
        labelPaint.color = Color.rgb(62, 66, 74)
        labelPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val textX = left + height * 0.68f
        val baseline = top + height * 0.63f
        canvas.drawFittedText(
            text = status.label,
            x = textX,
            y = baseline,
            maxWidth = width - (textX - left) - height * 0.24f,
            paint = labelPaint,
        )

        labelPaint.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun resetLabelPaint() {
        labelPaint.color = Color.rgb(203, 213, 225)
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun drawBadge(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        statusColors: StatusColors,
        minDim: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        val radius = height * 0.24f
        val rect = RectF(left, top, left + width, top + height)

        badgePaint.color = statusColors.container
        canvas.drawRoundRect(rect, radius, radius, badgePaint)

        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = minDim * 0.085f
        labelPaint.color = statusColors.content
        labelPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val horizontalInset = height * 0.34f
        val baseline = top + height * 0.63f
        canvas.drawFittedText(
            text = text,
            x = left + horizontalInset,
            y = baseline,
            maxWidth = width - horizontalInset * 2f,
            paint = labelPaint,
        )

        labelPaint.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun Canvas.drawFittedText(
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
    ) {
        if (maxWidth <= 0f) return
        val fitted = TextUtils.ellipsize(
            text,
            android.text.TextPaint(paint),
            maxWidth,
            TextUtils.TruncateAt.END,
        ).toString()
        drawText(fitted, x, y, paint)
    }

    private fun Int?.coolantStatusColors(): StatusColors =
        when (this?.let(EngineWarmupGuidance::stageFor)) {
            null -> StatusColors(
                container = Color.rgb(236, 239, 241),
                content = Color.rgb(38, 50, 56),
            )
            EngineWarmupStage.VeryGentle,
            EngineWarmupStage.Gentle -> StatusColors(
                container = Color.rgb(255, 243, 224),
                content = Color.rgb(93, 58, 0),
            )
            EngineWarmupStage.NormalCity,
            EngineWarmupStage.FullyWarm -> StatusColors(
                container = Color.rgb(232, 245, 233),
                content = Color.rgb(27, 94, 32),
            )
            EngineWarmupStage.Critical -> StatusColors(
                container = Color.rgb(255, 235, 238),
                content = Color.rgb(183, 28, 28),
            )
        }

    private fun MonitorConnectionStatus.indicatorColor(): Int = when (this) {
        MonitorConnectionStatus.Ready -> Color.rgb(46, 125, 50)
        MonitorConnectionStatus.Connecting -> Color.rgb(21, 101, 192)
        MonitorConnectionStatus.Reconnecting -> Color.rgb(239, 108, 0)
        MonitorConnectionStatus.Offline -> Color.rgb(198, 40, 40)
    }

    private data class StatusColors(
        val container: Int,
        val content: Int,
    )

    private companion object {
        const val MAX_RPM = 8000
        const val START_ANGLE = 200f
        const val SWEEP_ANGLE = 220f
        const val RED_START_ANGLE = 378.75f
        const val RED_SWEEP_ANGLE = 41.25f
    }
}
