package car.mazda.obd.android.feature.monitor

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import car.mazda.obd.android.ui.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class ObdOverlayController(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var overlayView: ObdOverlayView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var activeSize: FloatingWidgetSize? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var hasDragged = false

    fun update(state: ObdMonitorState) {
        mainHandler.post {
            updateOnMain(state)
        }
    }

    fun hide() {
        mainHandler.post {
            hideOnMain()
        }
    }

    private fun updateOnMain(state: ObdMonitorState) {
        val canDrawOverlay = Settings.canDrawOverlays(context)
        ObdMonitorStateStore.update { it.copy(overlayEnabled = canDrawOverlay) }
        if (!state.isRunning || !state.floatingWidgetEnabled || !canDrawOverlay || state.isAppForeground) {
            hideOnMain()
            return
        }

        if (overlayView != null && activeSize != state.floatingWidgetSize) {
            hideOnMain(keepPosition = true)
        }

        val view = overlayView ?: ObdOverlayView(context).also {
            overlayView = it
            activeSize = state.floatingWidgetSize
            val params = layoutParams(state.floatingWidgetSize).also { params ->
                overlayLayoutParams?.let { previous ->
                    params.x = previous.x
                    params.y = previous.y
                }
                overlayLayoutParams = params
            }
            it.setOnTouchListener(::onOverlayTouch)
            windowManager.addView(it, params)
        }
        view.render(state)
    }

    private fun hideOnMain() {
        hideOnMain(keepPosition = false)
    }

    private fun hideOnMain(keepPosition: Boolean) {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        activeSize = null
        if (!keepPosition) overlayLayoutParams = null
    }

    private fun onOverlayTouch(view: android.view.View, event: MotionEvent): Boolean {
        val params = overlayLayoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY
                hasDragged = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - touchStartRawX).roundToInt()
                val deltaY = (event.rawY - touchStartRawY).roundToInt()
                if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                    hasDragged = true
                }
                params.x = (dragStartX - deltaX).coerceAtLeast(0)
                params.y = (dragStartY + deltaY).coerceAtLeast(0)
                runCatching { windowManager.updateViewLayout(view, params) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                view.performClick()
                if (!hasDragged) openApp()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                return true
            }
        }
        return false
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun layoutParams(size: FloatingWidgetSize): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            size.widthPx,
            size.heightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }
    }
}
