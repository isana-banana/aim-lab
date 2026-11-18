package com.isanabanana.aim

import android.graphics.Canvas

/**
 * 2D camera that transforms world rendering into the virtual viewport.
 * Camera coordinates use the same virtual-world units as GameObject transforms.
 */
class Camera2D(
    positionX: Float? = null,
    positionY: Float? = null,
    zoom: Float = 1f,
    var rotation: Float = 0f
) {
    private var customX: Float? = positionX
    private var customY: Float? = positionY

    var x: Float
        get() = customX ?: viewportWidth / 2f
        set(value) { customX = value }

    var y: Float
        get() = customY ?: viewportHeight / 2f
        set(value) { customY = value }

    var zoom: Float = zoom
        set(value) {
            require(value.isFinite() && value > 0f) { "Camera zoom must be finite and greater than zero." }
            field = value
        }

    var viewportWidth: Float = 360f
        private set
    var viewportHeight: Float = 640f
        private set

    init {
        require(zoom.isFinite() && zoom > 0f) { "Camera zoom must be finite and greater than zero." }
    }

    fun setViewport(width: Float, height: Float) {
        require(width > 0f && height > 0f) { "Camera viewport dimensions must be positive." }
        viewportWidth = width
        viewportHeight = height
    }

    /** Applies camera transform. Caller must restore the Canvas save count. */
    fun apply(canvas: Canvas) {
        canvas.translate(viewportWidth / 2f, viewportHeight / 2f)
        canvas.scale(zoom, zoom)
        canvas.rotate(-rotation)
        canvas.translate(-x, -y)
    }

    /**
     * Converts a point in virtual screen coordinates into world coordinates.
     * This is the inverse of [apply], including camera zoom and rotation.
     */
    fun screenToWorldX(screenX: Float, screenY: Float): Float {
        val dx = (screenX - viewportWidth / 2f) / zoom
        val dy = (screenY - viewportHeight / 2f) / zoom
        val radians = Math.toRadians(rotation.toDouble())
        return x + (dx * kotlin.math.cos(radians) - dy * kotlin.math.sin(radians)).toFloat()
    }

    fun screenToWorldY(screenX: Float, screenY: Float): Float {
        val dx = (screenX - viewportWidth / 2f) / zoom
        val dy = (screenY - viewportHeight / 2f) / zoom
        val radians = Math.toRadians(rotation.toDouble())
        return y + (dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)).toFloat()
    }

    /** Converts a touch event to world space; screen-space UI should use the original event. */
    fun screenToWorld(event: GameTouchEvent): GameTouchEvent =
        event.copy(
            x = screenToWorldX(event.x, event.y),
            y = screenToWorldY(event.x, event.y)
        )

    fun moveTo(worldX: Float, worldY: Float) {
        x = worldX
        y = worldY
    }

    fun moveBy(deltaX: Float, deltaY: Float) {
        x += deltaX
        y += deltaY
    }
}
