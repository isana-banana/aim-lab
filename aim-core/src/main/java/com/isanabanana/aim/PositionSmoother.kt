package com.isanabanana.aim

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Smooths incoming network positions so remote objects move naturally between updates.
 *
 * Feed server positions with [target], then call [update] every frame and apply [x]/[y]
 * to the visual object. Large corrections snap immediately to avoid long rubber-band motion.
 */
class PositionSmoother(
    startX: Float = 0f,
    startY: Float = 0f,
    val smoothTimeSeconds: Float = 0.08f,
    val snapDistance: Float = 160f
) {
    var x: Float = startX
        private set
    var y: Float = startY
        private set
    private var targetX: Float = startX
    private var targetY: Float = startY

    init {
        require(startX.isFinite() && startY.isFinite()) { "Start position must be finite." }
        require(smoothTimeSeconds.isFinite() && smoothTimeSeconds > 0f) {
            "Smooth time must be finite and greater than zero."
        }
        require(snapDistance.isFinite() && snapDistance >= 0f) {
            "Snap distance must be finite and non-negative."
        }
    }

    /** Set a new authoritative position received from the network. */
    fun target(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite()) { "Target position must be finite." }
        val dx = x - this.x
        val dy = y - this.y
        if (dx * dx + dy * dy >= snapDistance * snapDistance) {
            snap(x, y)
        } else {
            targetX = x
            targetY = y
        }
    }

    /** Instantly align current and target positions, useful after a full state resync. */
    fun snap(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite()) { "Position must be finite." }
        this.x = x
        this.y = y
        targetX = x
        targetY = y
    }

    /** Advance smoothing by frame delta seconds. */
    fun update(deltaSeconds: Float) {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) return
        val alpha = (1f - exp(-deltaSeconds / smoothTimeSeconds)).coerceIn(0f, 1f)
        x += (targetX - x) * alpha
        y += (targetY - y) * alpha
        if (sqrt((targetX - x) * (targetX - x) + (targetY - y) * (targetY - y)) < 0.01f) {
            x = targetX
            y = targetY
        }
    }
}
