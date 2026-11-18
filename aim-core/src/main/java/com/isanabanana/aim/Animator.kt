package com.isanabanana.aim

import kotlin.math.cos
import kotlin.math.PI

/** Built-in transform animation helper for a GameObject. */
class Animator : Component() {
    enum class Easing {
        LINEAR,
        EASE_IN_OUT
    }

    private var elapsed = 0f
    private var duration = 0f
    private var easing = Easing.LINEAR
    private var active = false
    private var onFinished: (() -> Unit)? = null
    private val start = FloatArray(5)
    private val target = FloatArray(5)

    /**
     * Animates selected transform properties. Pass null to leave a property unchanged.
     * Rotation is expressed in degrees.
     */
    fun animateTo(
        x: Float? = null,
        y: Float? = null,
        scaleX: Float? = null,
        scaleY: Float? = null,
        rotation: Float? = null,
        durationSeconds: Float,
        easing: Easing = Easing.EASE_IN_OUT,
        onFinished: (() -> Unit)? = null
    ) {
        require(durationSeconds > 0f) { "Animation duration must be positive." }
        val transform = gameObject.transform
        start[0] = transform.x
        start[1] = transform.y
        start[2] = transform.scaleX
        start[3] = transform.scaleY
        start[4] = transform.rotation
        target[0] = x ?: start[0]
        target[1] = y ?: start[1]
        target[2] = scaleX ?: start[2]
        target[3] = scaleY ?: start[3]
        target[4] = rotation ?: start[4]
        elapsed = 0f
        duration = durationSeconds
        this.easing = easing
        this.onFinished = onFinished
        active = true
    }

    fun cancel() {
        active = false
        onFinished = null
    }

    val isRunning: Boolean get() = active

    override fun update(deltaSeconds: Float) {
        if (!active) return
        elapsed = (elapsed + deltaSeconds.coerceAtLeast(0f)).coerceAtMost(duration)
        var progress = (elapsed / duration).coerceIn(0f, 1f)
        if (easing == Easing.EASE_IN_OUT) {
            progress = ((1f - cos(progress * PI.toFloat())) / 2f)
        }

        val values = FloatArray(5) { index ->
            start[index] + (target[index] - start[index]) * progress
        }
        gameObject.transform.apply {
            x = values[0]
            y = values[1]
            scaleX = values[2]
            scaleY = values[3]
            rotation = values[4]
        }

        if (elapsed >= duration) {
            active = false
            val callback = onFinished
            onFinished = null
            callback?.invoke()
        }
    }

    override fun onDestroy() {
        cancel()
    }
}
