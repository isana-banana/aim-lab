package com.isanabanana.aim

import android.graphics.RectF
import kotlin.math.min

/** Fits a fixed logical game world into any view while preserving its aspect ratio. */
class VirtualViewport(
    val worldWidth: Float,
    val worldHeight: Float
) {
    init {
        require(worldWidth > 0f && worldHeight > 0f) { "Virtual resolution must be positive." }
    }

    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var initialized = false

    val destination: RectF = RectF()
    val isInitialized: Boolean get() = initialized

    fun update(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        // Fill the entire game view. The host game chooses its own layout-safe controls.
        scaleX = viewWidth / worldWidth
        scaleY = viewHeight / worldHeight
        offsetX = 0f
        offsetY = 0f
        destination.set(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        initialized = true
    }

    /** True only for points inside the rendered game area, excluding letterbox margins. */
    fun containsScreenPoint(screenX: Float, screenY: Float): Boolean =
        initialized && destination.contains(screenX, screenY)

    fun toWorldX(screenX: Float): Float = (screenX - offsetX) / scaleX
    fun toWorldY(screenY: Float): Float = (screenY - offsetY) / scaleY

    fun applyTo(canvas: android.graphics.Canvas) {
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleX, scaleY)
    }
}
