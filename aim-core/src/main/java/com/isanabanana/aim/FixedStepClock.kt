package com.isanabanana.aim

/**
 * Accumulates render-frame time and advances simulation in deterministic fixed steps.
 *
 * Excessive frame time and catch-up work are capped to avoid a spiral of death
 * after the app is backgrounded or the device stalls.
 */
internal class FixedStepClock(
    val stepSeconds: Float = 1f / 60f,
    private val maxFrameSeconds: Float = 0.25f,
    private val maxStepsPerFrame: Int = 5
) {
    private var accumulator = 0f

    init {
        require(stepSeconds.isFinite() && stepSeconds > 0f) {
            "Fixed step must be finite and positive."
        }
        require(maxFrameSeconds.isFinite() && maxFrameSeconds >= stepSeconds) {
            "Maximum frame time must be finite and at least one fixed step."
        }
        require(maxStepsPerFrame > 0) { "Maximum steps per frame must be positive." }
    }

    /** Runs zero or more fixed updates and returns the number of steps executed. */
    fun advance(frameDeltaSeconds: Float, update: (Float) -> Unit): Int {
        if (!frameDeltaSeconds.isFinite() || frameDeltaSeconds <= 0f) return 0
        accumulator += frameDeltaSeconds.coerceAtMost(maxFrameSeconds)
        var steps = 0
        while (accumulator + STEP_EPSILON >= stepSeconds && steps < maxStepsPerFrame) {
            update(stepSeconds)
            accumulator = (accumulator - stepSeconds).coerceAtLeast(0f)
            steps++
        }
        // Discard whole-step backlog after the per-frame work cap, preserving the fractional remainder.
        if (steps == maxStepsPerFrame && accumulator >= stepSeconds) {
            accumulator %= stepSeconds
        }
        return steps
    }

    fun reset() {
        accumulator = 0f
    }

    private companion object {
        const val STEP_EPSILON = 0.000001f
    }
}
