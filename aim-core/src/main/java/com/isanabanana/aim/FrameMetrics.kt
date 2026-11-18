package com.isanabanana.aim

/**
 * Rolling performance snapshot for the most recent rendered frames.
 *
 * FPS is derived from the sum of valid frame intervals. Timing averages are
 * diagnostic estimates, not a substitute for Android system profiling.
 */
data class FrameMetrics(
    val sampleCount: Int,
    val framesPerSecond: Float,
    val averageFrameTimeMs: Float,
    val averageUpdateTimeMs: Float,
    val averageDrawTimeMs: Float,
    val averageSimulationSteps: Float
)

/** Fixed-size allocation-free recorder; intended to be accessed on the game/UI thread. */
internal class FrameMetricsTracker(windowSize: Int = 60) {
    private val frameDeltaNanos: LongArray
    private val updateNanos: LongArray
    private val drawNanos: LongArray
    private val simulationSteps: IntArray
    private var nextIndex = 0
    private var sampleCount = 0
    private var latestIndex = -1

    init {
        require(windowSize > 0) { "Metrics window size must be positive." }
        frameDeltaNanos = LongArray(windowSize)
        updateNanos = LongArray(windowSize)
        drawNanos = LongArray(windowSize)
        simulationSteps = IntArray(windowSize)
    }

    fun recordFrame(deltaSeconds: Float, updateDurationNanos: Long, steps: Int) {
        val index = nextIndex
        frameDeltaNanos[index] =
            if (deltaSeconds.isFinite() && deltaSeconds > 0f) {
                (deltaSeconds.toDouble() * NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_INTERVAL_NANOS).toLong()
            }
            else 0L
        updateNanos[index] = updateDurationNanos.coerceAtLeast(0L)
        drawNanos[index] = 0L
        simulationSteps[index] = steps.coerceAtLeast(0)
        latestIndex = index
        nextIndex = (nextIndex + 1) % frameDeltaNanos.size
        sampleCount = (sampleCount + 1).coerceAtMost(frameDeltaNanos.size)
    }

    /** Associates the most recent Canvas draw duration with the latest frame. */
    fun recordDraw(durationNanos: Long) {
        if (latestIndex >= 0) drawNanos[latestIndex] = durationNanos.coerceAtLeast(0L)
    }

    fun snapshot(): FrameMetrics {
        if (sampleCount == 0) return FrameMetrics(0, 0f, 0f, 0f, 0f, 0f)

        var intervalTotal = 0L
        var updateTotal = 0L
        var drawTotal = 0L
        var stepsTotal = 0L
        var validIntervals = 0
        for (i in 0 until sampleCount) {
            val interval = frameDeltaNanos[i]
            if (interval > 0L) {
                intervalTotal += interval
                validIntervals++
            }
            updateTotal += updateNanos[i]
            drawTotal += drawNanos[i]
            stepsTotal += simulationSteps[i]
        }

        val averageFrameNanos = if (validIntervals > 0) intervalTotal.toDouble() / validIntervals else 0.0
        val fps = if (intervalTotal > 0L) validIntervals * NANOS_PER_SECOND / intervalTotal.toDouble() else 0.0
        return FrameMetrics(
            sampleCount = sampleCount,
            framesPerSecond = fps.toFloat(),
            averageFrameTimeMs = (averageFrameNanos / NANOS_PER_MILLISECOND).toFloat(),
            averageUpdateTimeMs =  (updateTotal.toDouble() / sampleCount / NANOS_PER_MILLISECOND).toFloat(),
            averageDrawTimeMs = (drawTotal.toDouble() / sampleCount / NANOS_PER_MILLISECOND).toFloat(),
            averageSimulationSteps = stepsTotal.toFloat() / sampleCount
        )
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val MAX_FRAME_INTERVAL_NANOS = 60_000_000_000.0
    }
}
