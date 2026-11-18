package com.isanabanana.aim

/**
 * Lightweight vertical gravity behavior.
 *
 * This is intentionally a simple motion component; grid-based games should
 * usually use Animator to move pieces between cells instead of physics.
 */
class Gravity2D(
    var gravityY: Float = 980f,
    var terminalVelocity: Float = 1400f,
    var groundY: Float? = null,
    var bounce: Float = 0f
) : Component() {

    var velocityY: Float = 0f
    var isGrounded: Boolean = false
        private set

    init {
        require(gravityY >= 0f) { "Gravity must not be negative." }
        require(terminalVelocity > 0f) { "Terminal velocity must be positive." }
        require(bounce in 0f..1f) { "Bounce must be between 0 and 1." }
    }

    override fun update(deltaSeconds: Float) {
        if (isGrounded) return
        val delta = deltaSeconds.coerceAtLeast(0f)
        velocityY = (velocityY + gravityY * delta).coerceAtMost(terminalVelocity)

        val transform = gameObject.transform
        transform.y += velocityY * delta

        val floor = groundY
        if (floor != null && transform.y >= floor) {
            transform.y = floor
            velocityY = -velocityY * bounce
            if (velocityY > -35f) {
                velocityY = 0f
                isGrounded = true
            }
        }
    }

    fun launch(upwardVelocity: Float) {
        require(upwardVelocity >= 0f) { "Upward velocity must not be negative." }
        velocityY = -upwardVelocity
        isGrounded = false
    }

    fun reset() {
        velocityY = 0f
        isGrounded = false
    }
}
