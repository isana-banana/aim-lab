package com.isanabanana.aim

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Reusable 2D particle effect supporting one-shot bursts and continuous emission.
 *
 * Attach to a GameObject positioned at the effect origin. Particles are simulated
 * and drawn locally; no child GameObjects are created.
 */
class ParticleEmitter(
    private val colors: IntArray,
    private val particleCount: Int = 14,
    private val durationSeconds: Float = 0.48f,
    private val minSpeed: Float = 45f,
    private val maxSpeed: Float = 145f,
    private val gravityY: Float = 210f,
    private val minRadius: Float = 2f,
    private val maxRadius: Float = 4.5f,
    private val emissionMode: EmissionMode = EmissionMode.BURST,
    private val emissionRate: Float = 24f,
    private val emitterDurationSeconds: Float? = null,
    private val directionAngleDegrees: Float = -90f,
    private val spreadAngleDegrees: Float = 360f,
    private val airResistance: Float = 1.2f,
    private val endScale: Float = 0.65f,
    private val maxParticles: Int = 300,
    private val onFinished: (() -> Unit)? = null
) : Component() {

    enum class EmissionMode { BURST, CONTINUOUS }

    private data class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var age: Float,
        val radius: Float,
        val color: Int
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var elapsed = 0f
    private var emissionAccumulator = 0f
    private var emitting = true
    private var finished = false

    init {
        require(colors.isNotEmpty()) { "ParticleEmitter needs at least one color." }
        require(particleCount > 0) { "Particle count must be positive." }
        require(durationSeconds > 0f) { "Particle lifetime must be positive." }
        require(maxSpeed >= minSpeed && minSpeed >= 0f) { "Invalid particle speed range." }
        require(minRadius > 0f && maxRadius >= minRadius) { "Invalid particle size range." }
        require(emissionRate > 0f) { "Emission rate must be positive." }
        require(spreadAngleDegrees in 0f..360f) { "Spread angle must be between 0 and 360 degrees." }
        require(maxParticles > 0) { "Maximum particle count must be positive." }
    }

    override fun onStart() {
        if (emissionMode == EmissionMode.BURST) {
            spawnParticles(min(particleCount, maxParticles))
            emitting = false
        }
    }

    /** Stops new particles from being emitted; existing particles finish naturally. */
    fun stopEmission() {
        emitting = false
        finishIfDone()
    }

    fun particleCountAlive(): Int = particles.size

    override fun update(deltaSeconds: Float) {
        if (finished) return
        val delta = deltaSeconds.coerceAtLeast(0f).coerceAtMost(0.1f)
        elapsed += delta

        if (emitting && emissionMode == EmissionMode.CONTINUOUS) {
            if (emitterDurationSeconds != null && elapsed >= emitterDurationSeconds) {
                emitting = false
            } else {
                emissionAccumulator += delta * emissionRate
                val requested = emissionAccumulator.toInt()
                if (requested > 0) {
                    val available = (maxParticles - particles.size).coerceAtLeast(0)
                    spawnParticles(min(requested, available))
                    emissionAccumulator -= requested
                }
            }
        }

        particles.forEach { particle ->
            particle.age += delta
            particle.x += particle.velocityX * delta
            particle.y += particle.velocityY * delta
            particle.velocityY += gravityY * delta
            val damping = (1f - airResistance * delta).coerceAtLeast(0f)
            particle.velocityX *= damping
            particle.velocityY *= damping
        }
        particles.removeAll { it.age >= durationSeconds }
        finishIfDone()
    }

    private fun spawnParticles(count: Int) {
        if (count <= 0) return
        repeat(count) {
            val halfSpread = spreadAngleDegrees / 2f
            val angleDegrees = directionAngleDegrees +
                (Random.nextFloat() * 2f - 1f) * halfSpread
            val angle = Math.toRadians(angleDegrees.toDouble()).toFloat()
            val speed = minSpeed + Random.nextFloat() * (maxSpeed - minSpeed)
            particles += Particle(
                x = 0f,
                y = 0f,
                velocityX = kotlin.math.cos(angle) * speed,
                velocityY = kotlin.math.sin(angle) * speed,
                age = 0f,
                radius = minRadius + Random.nextFloat() * (maxRadius - minRadius),
                color = colors[Random.nextInt(colors.size)]
            )
        }
    }

    private fun finishIfDone() {
        if (!finished && !emitting && particles.isEmpty()) {
            finished = true
            onFinished?.invoke()
        }
    }

    override fun draw(canvas: Canvas) {
        if (finished) return
        particles.forEach { particle ->
            val progress = (particle.age / durationSeconds).coerceIn(0f, 1f)
            paint.color = particle.color
            paint.alpha = (255f * (1f - progress)).toInt().coerceIn(0, 255)
            val scale = 1f + (endScale - 1f) * progress
            canvas.drawCircle(
                particle.x,
                particle.y,
                max(0.2f, particle.radius * scale),
                paint
            )
        }
    }

    override fun onDestroy() {
        particles.clear()
        emitting = false
        finished = true
    }
}
