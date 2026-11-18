package com.isanabanana.aim

import kotlin.math.exp

/**
 * Basic 2D rigid-body motion.
 *
 * Positive Y points down, matching Android Canvas coordinates.
 * Attach a Collider2D to participate in collision detection and response.
 */
class PhysicsBody2D(
    var bodyType: BodyType = BodyType.DYNAMIC,
    mass: Float = 1f,
    gravityScale: Float = 1f,
    linearDrag: Float = 0f,
    restitution: Float = 0f,
    restitutionVelocityThreshold: Float = 1f,
    friction: Float = 0f,
    var useGravity: Boolean = true,
    velocityX: Float = 0f,
    velocityY: Float = 0f
) : Component() {

    enum class BodyType { STATIC, KINEMATIC, DYNAMIC }

    var mass: Float = mass
        set(value) {
            require(value.isFinite() && value > 0f) { "Mass must be finite and positive." }
            field = value
        }

    var gravityScale: Float = gravityScale
        set(value) {
            require(value.isFinite() && value >= 0f) { "Gravity scale must be finite and non-negative." }
            field = value
        }

    var linearDrag: Float = linearDrag
        set(value) {
            require(value.isFinite() && value >= 0f) { "Linear drag must be finite and non-negative." }
            field = value
        }

    var restitution: Float = restitution
        set(value) {
            require(value.isFinite() && value in 0f..1f) { "Restitution must be between 0 and 1." }
            field = value
        }

    var restitutionVelocityThreshold: Float = restitutionVelocityThreshold
        set(value) {
            require(value.isFinite() && value >= 0f) {
                "Restitution velocity threshold must be finite and non-negative."
            }
            field = value
        }

    var friction: Float = friction
        set(value) {
            require(value.isFinite() && value >= 0f) { "Friction must be finite and non-negative." }
            field = value
        }

    var velocityX: Float = velocityX
        set(value) {
            require(value.isFinite()) { "Velocity X must be finite." }
            field = value
        }

    var velocityY: Float = velocityY
        set(value) {
            require(value.isFinite()) { "Velocity Y must be finite." }
            field = value
        }

    private var forceX = 0f
    private var forceY = 0f

    init {
        // Validate constructor values as well; initial property assignment does not call setters.
        require(mass.isFinite() && mass > 0f) { "Mass must be finite and positive." }
        require(gravityScale.isFinite() && gravityScale >= 0f) {
            "Gravity scale must be finite and non-negative."
        }
        require(linearDrag.isFinite() && linearDrag >= 0f) {
            "Linear drag must be finite and non-negative."
        }
        require(restitution.isFinite() && restitution in 0f..1f) {
            "Restitution must be between 0 and 1."
        }
        require(restitutionVelocityThreshold.isFinite() && restitutionVelocityThreshold >= 0f) {
            "Restitution velocity threshold must be finite and non-negative."
        }
        require(friction.isFinite() && friction >= 0f) {
            "Friction must be finite and non-negative."
        }
        require(velocityX.isFinite() && velocityY.isFinite()) { "Initial velocity must be finite." }
    }

    override fun update(deltaSeconds: Float) {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f) {
            "Physics delta time must be finite and non-negative."
        }
        val delta = deltaSeconds
        if (delta == 0f) return
        if (bodyType == BodyType.STATIC) {
            clearForces()
            return
        }

        var nextVelocityX = velocityX
        var nextVelocityY = velocityY
        if (bodyType == BodyType.DYNAMIC) {
            if (useGravity) {
                nextVelocityY += DEFAULT_GRAVITY_Y * gravityScale * delta
            }
            nextVelocityX += forceX / mass * delta
            nextVelocityY += forceY / mass * delta
            if (linearDrag > 0f) {
                val damping = exp(-linearDrag * delta)
                nextVelocityX *= damping
                nextVelocityY *= damping
            }
        }

        val nextX = gameObject.transform.x + nextVelocityX * delta
        val nextY = gameObject.transform.y + nextVelocityY * delta
        require(nextVelocityX.isFinite() && nextVelocityY.isFinite()) {
            "Physics integration produced a non-finite velocity."
        }
        require(nextX.isFinite() && nextY.isFinite()) {
            "Physics integration produced a non-finite position."
        }

        // Commit only after every result is validated, so a failed step cannot half-update a body.
        velocityX = nextVelocityX
        velocityY = nextVelocityY
        gameObject.transform.x = nextX
        gameObject.transform.y = nextY
        clearForces()
    }

    /** Adds a continuous force; it is consumed by the next physics update. */
    fun applyForce(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite()) { "Applied force must be finite." }
        if (bodyType != BodyType.DYNAMIC) return
        val nextX = forceX + x
        val nextY = forceY + y
        require(nextX.isFinite() && nextY.isFinite()) { "Accumulated force must remain finite." }
        forceX = nextX
        forceY = nextY
    }

    /** Changes velocity immediately, scaled by inverse mass. */
    fun applyImpulse(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite()) { "Applied impulse must be finite." }
        if (bodyType != BodyType.DYNAMIC) return
        val nextX = velocityX + x / mass
        val nextY = velocityY + y / mass
        require(nextX.isFinite() && nextY.isFinite()) { "Impulse must not produce a non-finite velocity." }
        velocityX = nextX
        velocityY = nextY
    }

    fun setVelocity(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite()) { "Velocity must be finite." }
        velocityX = x
        velocityY = y
    }

    fun stop() {
        velocityX = 0f
        velocityY = 0f
        clearForces()
    }

    private fun clearForces() {
        forceX = 0f
        forceY = 0f
    }

    internal val inverseMass: Float
        get() = if (bodyType == BodyType.DYNAMIC) 1f / mass else 0f

    companion object {
        /** Default downward gravity in virtual world units per second squared. */
        const val DEFAULT_GRAVITY_Y = 980f
    }
}
