package com.isanabanana.aim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D collider attached to a GameObject.
 *
 * Bounds are axis-aligned in world space. Rotation affects the collider offset,
 * but does not rotate the box itself. Collision detection does not apply forces
 * or move objects; use a physics system for physical response.
 */
class Collider2D(
    val shape: Shape = Shape.BOX,
    var width: Float = 32f,
    var height: Float = 32f,
    var radius: Float = 16f,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var layer: Int = 1,
    var mask: Int = -1,
    var isTrigger: Boolean = false,
    var enabled: Boolean = true
) : Component() {

    enum class Shape { BOX, CIRCLE }

    var onCollisionEnter: ((Collider2D) -> Unit)? = null
    var onCollisionStay: ((Collider2D) -> Unit)? = null
    var onCollisionExit: ((Collider2D) -> Unit)? = null

    internal data class Bounds(
        val centerX: Float,
        val centerY: Float,
        val halfWidth: Float,
        val halfHeight: Float,
        val radius: Float
    )

    internal fun worldBounds(): Bounds {
        require(width.isFinite() && width >= 0f) { "Collider width must be finite and non-negative." }
        require(height.isFinite() && height >= 0f) { "Collider height must be finite and non-negative." }
        require(radius.isFinite() && radius >= 0f) { "Collider radius must be finite and non-negative." }
        require(offsetX.isFinite() && offsetY.isFinite()) { "Collider offsets must be finite." }

        val transform = gameObject.transform
        require(
            transform.x.isFinite() && transform.y.isFinite() &&
                transform.rotation.isFinite() &&
                transform.scaleX.isFinite() && transform.scaleY.isFinite()
        ) { "Collider transform values must be finite." }
        val scaledOffsetX = offsetX * transform.scaleX
        val scaledOffsetY = offsetY * transform.scaleY
        val radians = Math.toRadians(transform.rotation.toDouble())
        val worldOffsetX = (scaledOffsetX * cos(radians) - scaledOffsetY * sin(radians)).toFloat()
        val worldOffsetY = (scaledOffsetX * sin(radians) + scaledOffsetY * cos(radians)).toFloat()
        val scaleX = abs(transform.scaleX)
        val scaleY = abs(transform.scaleY)
        val bounds = Bounds(
            centerX = transform.x + worldOffsetX,
            centerY = transform.y + worldOffsetY,
            halfWidth = width * scaleX / 2f,
            halfHeight = height * scaleY / 2f,
            radius = radius * max(scaleX, scaleY)
        )
        require(
            bounds.centerX.isFinite() && bounds.centerY.isFinite() &&
                bounds.halfWidth.isFinite() && bounds.halfHeight.isFinite() &&
                bounds.radius.isFinite()
        ) { "Computed collider bounds must remain finite." }
        return bounds
    }

    /** Returns true when this collider overlaps another enabled collider. */
    fun overlaps(other: Collider2D): Boolean {
        if (!canTestAgainst(other)) return false
        return intersects(this, worldBounds(), other, other.worldBounds())
    }

    /** Reuses bounds already calculated by the broadphase. */
    internal fun overlaps(
        other: Collider2D,
        bounds: Bounds,
        otherBounds: Bounds
    ): Boolean {
        if (!canTestAgainst(other)) return false
        return intersects(this, bounds, other, otherBounds)
    }

    private fun canTestAgainst(other: Collider2D): Boolean =
        enabled && other.enabled && gameObject !== other.gameObject &&
            (mask and other.layer) != 0 && (other.mask and layer) != 0

    internal fun notifyEnter(other: Collider2D) { onCollisionEnter?.invoke(other) }
    internal fun notifyStay(other: Collider2D) { onCollisionStay?.invoke(other) }
    internal fun notifyExit(other: Collider2D) { onCollisionExit?.invoke(other) }

    private fun intersects(a: Collider2D, ab: Bounds, b: Collider2D, bb: Bounds): Boolean {
        if (a.shape == Shape.CIRCLE && b.shape == Shape.CIRCLE) {
            val dx = ab.centerX - bb.centerX
            val dy = ab.centerY - bb.centerY
            val sum = ab.radius + bb.radius
            return dx * dx + dy * dy <= sum * sum
        }
        if (a.shape == Shape.BOX && b.shape == Shape.BOX) {
            return abs(ab.centerX - bb.centerX) <= ab.halfWidth + bb.halfWidth &&
                abs(ab.centerY - bb.centerY) <= ab.halfHeight + bb.halfHeight
        }
        val circle = if (a.shape == Shape.CIRCLE) ab else bb
        val box = if (a.shape == Shape.BOX) ab else bb
        val nearestX = circle.centerX.coerceIn(box.centerX - box.halfWidth, box.centerX + box.halfWidth)
        val nearestY = circle.centerY.coerceIn(box.centerY - box.halfHeight, box.centerY + box.halfHeight)
        val dx = circle.centerX - nearestX
        val dy = circle.centerY - nearestY
        return dx * dx + dy * dy <= circle.radius * circle.radius
    }
}

/** Spatial-hash broadphase for collision enter, stay, and exit events. */
internal class CollisionSystem(
    private val cellSize: Float = 128f
) {
    private class Pair(val first: Collider2D, val second: Collider2D) {
        override fun equals(other: Any?): Boolean =
            other is Pair && first === other.first && second === other.second

        override fun hashCode(): Int =
            31 * System.identityHashCode(first) + System.identityHashCode(second)
    }

    private val colliders = mutableListOf<Collider2D>()
    private val grid = HashMap<Long, MutableList<Collider2D>>()
    private val activePairs = mutableSetOf<Pair>()
    private val oversizedColliders = mutableListOf<Collider2D>()
    private val currentPairs = mutableSetOf<Pair>()
    private val previousBounds = java.util.IdentityHashMap<Collider2D, Collider2D.Bounds>()
    private val currentBounds = java.util.IdentityHashMap<Collider2D, Collider2D.Bounds>()
    private val physicalPairs = mutableListOf<Pair>()
    private val bodyCache = java.util.IdentityHashMap<GameObject, PhysicsBody2D?>()

    /** Cache component lookup once per collision update, avoiding temporary filtered lists. */
    private fun bodyOf(collider: Collider2D): PhysicsBody2D? {
        val gameObject = collider.gameObject
        if (bodyCache.containsKey(gameObject)) return bodyCache[gameObject]
        var body: PhysicsBody2D? = null
        for (component in gameObject.components) {
            if (component is PhysicsBody2D) {
                body = component
                break
            }
        }
        bodyCache[gameObject] = body
        return body
    }

    init {
        require(cellSize.isFinite() && cellSize > 0f) {
            "Collision grid cell size must be finite and positive."
        }
    }

    fun update(
        objects: List<GameObject>,
        pendingRemovals: Set<GameObject> = emptySet(),
        deltaSeconds: Float = 0f
    ) {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f) {
            "Collision delta time must be finite and non-negative."
        }
        colliders.clear()
        bodyCache.clear()
        for (gameObject in objects) {
            if (!gameObject.isActive || gameObject in pendingRemovals) continue
            for (component in gameObject.components) {
                if (component is Collider2D && component.enabled) colliders.add(component)
            }
        }

        grid.values.forEach { it.clear() }
        oversizedColliders.clear()
        currentPairs.clear()
        currentBounds.clear()
        physicalPairs.clear()

        for (collider in colliders) {
            val bounds = collider.worldBounds()
            currentBounds[collider] = bounds
            val previous = previousBounds[collider] ?: bounds
            val extentX = if (collider.shape == Collider2D.Shape.CIRCLE) bounds.radius else bounds.halfWidth
            val extentY = if (collider.shape == Collider2D.Shape.CIRCLE) bounds.radius else bounds.halfHeight
            val minX = min(previous.centerX - extentX, bounds.centerX - extentX)
            val maxX = max(previous.centerX + extentX, bounds.centerX + extentX)
            val minY = min(previous.centerY - extentY, bounds.centerY - extentY)
            val maxY = max(previous.centerY + extentY, bounds.centerY + extentY)
            val minCellXValue = kotlin.math.floor(minX / cellSize)
            val maxCellXValue = kotlin.math.floor(maxX / cellSize)
            val minCellYValue = kotlin.math.floor(minY / cellSize)
            val maxCellYValue = kotlin.math.floor(maxY / cellSize)
            val intMin = Int.MIN_VALUE.toDouble()
            val intMax = Int.MAX_VALUE.toDouble()
            val outsideCellRange = minCellXValue < intMin || maxCellXValue > intMax ||
                minCellYValue < intMin || maxCellYValue > intMax

            // Very large or far-away colliders use the oversized bucket. Check in Double/Long
            // before converting to Int so extreme finite coordinates cannot overflow cell counts.
            if (outsideCellRange) {
                oversizedColliders.add(collider)
            } else {
                val minCellX = minCellXValue.toInt()
                val maxCellX = maxCellXValue.toInt()
                val minCellY = minCellYValue.toInt()
                val maxCellY = maxCellYValue.toInt()
                val cellCountX = maxCellX.toLong() - minCellX.toLong() + 1L
                val cellCountY = maxCellY.toLong() - minCellY.toLong() + 1L
                if (cellCountX > 256L / cellCountY) {
                    oversizedColliders.add(collider)
                } else {
                    for (cellY in minCellY..maxCellY) {
                        for (cellX in minCellX..maxCellX) {
                            grid.getOrPut(cellKey(cellX, cellY)) { mutableListOf() }.add(collider)
                        }
                    }
                }
            }
        }

        // Include oversized colliders in each occupied bucket so they are checked against nearby objects.
        if (oversizedColliders.isNotEmpty()) {
            for (bucket in grid.values) bucket.addAll(oversizedColliders)
            grid.getOrPut(OVERSIZED_BUCKET) { mutableListOf() }.addAll(oversizedColliders)
        }

        // Drop empty cells so objects moving through the world cannot grow the hash forever.
        val bucketIterator = grid.entries.iterator()
        while (bucketIterator.hasNext()) {
            if (bucketIterator.next().value.isEmpty()) bucketIterator.remove()
        }

        // Compare objects only when they share a spatial cell.
        for (bucket in grid.values) {
            for (i in 0 until bucket.size) {
                for (j in i + 1 until bucket.size) {
                    val first = bucket[i]
                    val second = bucket[j]
                    if (first.gameObject === second.gameObject) continue
                    // Apply the same bilateral layer/mask filter to discrete and swept contacts.
                    if ((first.mask and second.layer) == 0 ||
                        (second.mask and first.layer) == 0
                    ) continue
                    val pair = Pair(first, second)
                    if (pair in currentPairs) continue
                    val overlapsNow = first.overlaps(second, currentBounds[first]!!, currentBounds[second]!!)
                    val hit = if (!overlapsNow) sweptHit(
                        first, second,
                        previousBounds[first] ?: currentBounds[first]!!,
                        previousBounds[second] ?: currentBounds[second]!!,
                        currentBounds[first]!!, currentBounds[second]!!
                    ) else null
                    if (!overlapsNow && hit == null) continue
                    currentPairs.add(pair)

                    if (!first.isTrigger && !second.isTrigger) {
                        if (overlapsNow) physicalPairs.add(pair)
                        else if (hit != null) resolveSweptCollision(first, second, hit, deltaSeconds)
                    }
                    if (pair in activePairs) {
                        first.notifyStay(second)
                        second.notifyStay(first)
                    } else {
                        first.notifyEnter(second)
                        second.notifyEnter(first)
                    }
                }
            }
        }

        // Revisit contacts so impulses can propagate through chains of touching bodies.
        repeat(SOLVER_ITERATIONS) {
            for (pair in physicalPairs) resolveCollision(pair.first, pair.second)
        }

        for (pair in activePairs) {
            if (pair !in currentPairs) {
                pair.first.notifyExit(pair.second)
                pair.second.notifyExit(pair.first)
            }
        }
        activePairs.clear()
        activePairs.addAll(currentPairs)
        previousBounds.clear()
        for (collider in colliders) previousBounds[collider] = collider.worldBounds()
    }

    private data class SweepHit(val time: Float, val normalX: Float, val normalY: Float)

    /** Sweeps supported collider pairs using relative motion. */
    private fun sweptHit(
        first: Collider2D,
        second: Collider2D,
        previousA: Collider2D.Bounds,
        previousB: Collider2D.Bounds,
        currentA: Collider2D.Bounds,
        currentB: Collider2D.Bounds
    ): SweepHit? {
        val aBody = bodyOf(first)
        val bBody = bodyOf(second)
        if ((aBody?.inverseMass ?: 0f) + (bBody?.inverseMass ?: 0f) <= 0f) return null
        if (first.shape == Collider2D.Shape.CIRCLE && second.shape == Collider2D.Shape.CIRCLE) {
            return sweptCircleHit(previousA, previousB, currentA, currentB)
        }
        if (first.shape != second.shape) {
            val hit = if (first.shape == Collider2D.Shape.CIRCLE) {
                sweptCircleBoxHit(previousA, previousB, currentA, currentB)
            } else {
                sweptCircleBoxHit(previousB, previousA, currentB, currentA)
            } ?: return null
            return if (first.shape == Collider2D.Shape.CIRCLE) hit
            else SweepHit(hit.time, -hit.normalX, -hit.normalY)
        }
        if (first.shape != Collider2D.Shape.BOX || second.shape != Collider2D.Shape.BOX) return null

        val halfX = previousA.halfWidth + previousB.halfWidth
        val halfY = previousA.halfHeight + previousB.halfHeight
        val startX = previousB.centerX - previousA.centerX
        val startY = previousB.centerY - previousA.centerY
        val moveX = (currentA.centerX - previousA.centerX) - (currentB.centerX - previousB.centerX)
        val moveY = (currentA.centerY - previousA.centerY) - (currentB.centerY - previousB.centerY)

        fun axisTimes(start: Float, movement: Float, extent: Float): kotlin.Pair<Float, Float>? {
            if (abs(movement) < 0.000001f) {
                if (start < -extent || start > extent) return null
                return Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY
            }
            val t1 = (start - extent) / movement
            val t2 = (start + extent) / movement
            return min(t1, t2) to max(t1, t2)
        }

        val xTimes = axisTimes(startX, moveX, halfX) ?: return null
        val yTimes = axisTimes(startY, moveY, halfY) ?: return null
        val entry = max(xTimes.first, yTimes.first)
        val exit = min(xTimes.second, yTimes.second)
        if (entry > exit || exit < 0f || entry !in 0f..1f) return null
        return if (xTimes.first > yTimes.first) {
            SweepHit(entry, if (moveX >= 0f) 1f else -1f, 0f)
        } else {
            SweepHit(entry, 0f, if (moveY >= 0f) 1f else -1f)
        }
    }

    /** Exact continuous sweep for two circles using their combined radii. */
    private fun sweptCircleHit(
        previousA: Collider2D.Bounds,
        previousB: Collider2D.Bounds,
        currentA: Collider2D.Bounds,
        currentB: Collider2D.Bounds
    ): SweepHit? {
        val radius = previousA.radius + previousB.radius
        val startX = previousB.centerX - previousA.centerX
        val startY = previousB.centerY - previousA.centerY
        val moveX = (currentA.centerX - previousA.centerX) - (currentB.centerX - previousB.centerX)
        val moveY = (currentA.centerY - previousA.centerY) - (currentB.centerY - previousB.centerY)
        val c = startX * startX + startY * startY - radius * radius
        if (c <= 0f) return null
        val a = moveX * moveX + moveY * moveY
        if (a < 0.000001f) return null
        val b = -2f * (startX * moveX + startY * moveY)
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null
        val time = (-b - sqrt(discriminant)) / (2f * a)
        if (time !in 0f..1f) return null
        val contactX = startX - moveX * time
        val contactY = startY - moveY * time
        val length = sqrt(contactX * contactX + contactY * contactY)
        if (length < 0.000001f) return null
        return SweepHit(time, contactX / length, contactY / length)
    }

    /** Continuous sweep of a circle against an axis-aligned box (rounded Minkowski boundary). */
    private fun sweptCircleBoxHit(
        previousCircle: Collider2D.Bounds,
        previousBox: Collider2D.Bounds,
        currentCircle: Collider2D.Bounds,
        currentBox: Collider2D.Bounds
    ): SweepHit? {
        val radius = previousCircle.radius
        val startX = previousCircle.centerX - previousBox.centerX
        val startY = previousCircle.centerY - previousBox.centerY
        val moveX = (currentCircle.centerX - previousCircle.centerX) -
            (currentBox.centerX - previousBox.centerX)
        val moveY = (currentCircle.centerY - previousCircle.centerY) -
            (currentBox.centerY - previousBox.centerY)
        val halfX = previousBox.halfWidth
        val halfY = previousBox.halfHeight
        var bestHit: SweepHit? = null

        fun consider(hit: SweepHit) {
            if (bestHit == null || hit.time < bestHit!!.time) bestHit = hit
        }

        fun addSide(time: Float, nx: Float, ny: Float) {
            if (time !in 0f..1f) return
            val x = startX + moveX * time
            val y = startY + moveY * time
            if (nx != 0f && y in -halfY..halfY) consider(SweepHit(time, -nx, 0f))
            if (ny != 0f && x in -halfX..halfX) consider(SweepHit(time, 0f, -ny))
        }

        if (moveX > 0f) addSide((-halfX - radius - startX) / moveX, -1f, 0f)
        if (moveX < 0f) addSide((halfX + radius - startX) / moveX, 1f, 0f)
        if (moveY > 0f) addSide((-halfY - radius - startY) / moveY, 0f, -1f)
        if (moveY < 0f) addSide((halfY + radius - startY) / moveY, 0f, 1f)

        val a = moveX * moveX + moveY * moveY
        if (a > 0.000001f) {
            for (cornerXSign in -1..1 step 2) {
                val cornerX = halfX * cornerXSign
                for (cornerYSign in -1..1 step 2) {
                    val cornerY = halfY * cornerYSign
                    val dx = startX - cornerX
                    val dy = startY - cornerY
                    val b = 2f * (dx * moveX + dy * moveY)
                    val c = dx * dx + dy * dy - radius * radius
                    val discriminant = b * b - 4f * a * c
                    if (discriminant < 0f) continue
                    val time = (-b - sqrt(discriminant)) / (2f * a)
                    if (time !in 0f..1f) continue
                    val x = startX + moveX * time
                    val y = startY + moveY * time
                    if ((if (cornerX < 0f) x <= -halfX else x >= halfX) &&
                        (if (cornerY < 0f) y <= -halfY else y >= halfY)
                    ) {
                        val nx = cornerX - x
                        val ny = cornerY - y
                        val length = sqrt(nx * nx + ny * ny)
                        if (length > 0.000001f) {
                            consider(SweepHit(time, nx / length, ny / length))
                        }
                    }
                }
            }
        }
        return bestHit
    }

    /** Rewinds moving bodies to first contact and removes inward normal velocity. */
    private fun resolveSweptCollision(
        first: Collider2D,
        second: Collider2D,
        hit: SweepHit,
        deltaSeconds: Float
    ) {
        val firstBody = bodyOf(first)
        val secondBody = bodyOf(second)
        val aPrevious = previousBounds[first]
        val bPrevious = previousBounds[second]
        val aCurrent = first.worldBounds()
        val bCurrent = second.worldBounds()
        val t = hit.time.coerceIn(0f, 1f)

        if ((firstBody?.inverseMass ?: 0f) > 0f && aPrevious != null) {
            first.gameObject.transform.x -= (aCurrent.centerX - aPrevious.centerX) * (1f - t)
            first.gameObject.transform.y -= (aCurrent.centerY - aPrevious.centerY) * (1f - t)
        }
        if ((secondBody?.inverseMass ?: 0f) > 0f && bPrevious != null) {
            second.gameObject.transform.x -= (bCurrent.centerX - bPrevious.centerX) * (1f - t)
            second.gameObject.transform.y -= (bCurrent.centerY - bPrevious.centerY) * (1f - t)
        }

        val inverseFirst = firstBody?.inverseMass ?: 0f
        val inverseSecond = secondBody?.inverseMass ?: 0f
        val inverseTotal = inverseFirst + inverseSecond
        if (inverseTotal <= 0f) return
        val relativeVelocity = ((secondBody?.velocityX ?: 0f) - (firstBody?.velocityX ?: 0f)) * hit.normalX +
            ((secondBody?.velocityY ?: 0f) - (firstBody?.velocityY ?: 0f)) * hit.normalY
        if (relativeVelocity >= 0f) return
        val impactSpeed = -relativeVelocity
        val threshold = max(
            firstBody?.restitutionVelocityThreshold ?: 1f,
            secondBody?.restitutionVelocityThreshold ?: 1f
        )
        val restitution = if (impactSpeed >= threshold) {
            minOf(firstBody?.restitution ?: 0f, secondBody?.restitution ?: 0f)
        } else 0f
        val impulse = -(1f + restitution) * relativeVelocity / inverseTotal
        firstBody?.applyImpulse(-impulse * hit.normalX, -impulse * hit.normalY)
        secondBody?.applyImpulse(impulse * hit.normalX, impulse * hit.normalY)

        // Match discrete contacts: remove tangential velocity using Coulomb friction,
        // bounded by the normal impulse generated at this swept impact.
        val postNormalX = (secondBody?.velocityX ?: 0f) - (firstBody?.velocityX ?: 0f)
        val postNormalY = (secondBody?.velocityY ?: 0f) - (firstBody?.velocityY ?: 0f)
        val tangentVelocityX = postNormalX -
            (postNormalX * hit.normalX + postNormalY * hit.normalY) * hit.normalX
        val tangentVelocityY = postNormalY -
            (postNormalX * hit.normalX + postNormalY * hit.normalY) * hit.normalY
        val tangentSpeed = sqrt(tangentVelocityX * tangentVelocityX + tangentVelocityY * tangentVelocityY)
        if (tangentSpeed > 0.000001f) {
            val tangentX = tangentVelocityX / tangentSpeed
            val tangentY = tangentVelocityY / tangentSpeed
            val friction = sqrt((firstBody?.friction ?: 0f) * (secondBody?.friction ?: 0f))
            val frictionMagnitude = (tangentSpeed / inverseTotal)
                .coerceAtMost(impulse * friction)
            val frictionX = frictionMagnitude * tangentX
            val frictionY = frictionMagnitude * tangentY
            firstBody?.applyImpulse(frictionX, frictionY)
            secondBody?.applyImpulse(-frictionX, -frictionY)
        }
        val remainingSeconds = deltaSeconds * (1f - t)
        if (remainingSeconds > 0f) {
            if (inverseFirst > 0f && aPrevious != null) {
                first.gameObject.transform.x += firstBody!!.velocityX * remainingSeconds
                first.gameObject.transform.y += firstBody.velocityY * remainingSeconds
            }
            if (inverseSecond > 0f && bPrevious != null) {
                second.gameObject.transform.x += secondBody!!.velocityX * remainingSeconds
                second.gameObject.transform.y += secondBody.velocityY * remainingSeconds
            }
        }
    }

    private fun cellKey(x: Int, y: Int): Long =
        (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

    private data class ContactManifold(
        val normalX: Float,
        val normalY: Float,
        val penetration: Float
    )

    /** Computes a shape-aware contact normal pointing from the first collider to the second. */
    private fun contactManifold(first: Collider2D, second: Collider2D): ContactManifold? {
        val a = first.worldBounds()
        val b = second.worldBounds()
        if (first.shape == Collider2D.Shape.CIRCLE && second.shape == Collider2D.Shape.CIRCLE) {
            val dx = b.centerX - a.centerX
            val dy = b.centerY - a.centerY
            val distanceSquared = dx * dx + dy * dy
            val radiusSum = a.radius + b.radius
            if (distanceSquared >= radiusSum * radiusSum) return null
            val distance = sqrt(distanceSquared)
            if (distance < 0.000001f) return ContactManifold(1f, 0f, radiusSum)
            return ContactManifold(dx / distance, dy / distance, radiusSum - distance)
        }

        if (first.shape == Collider2D.Shape.BOX && second.shape == Collider2D.Shape.BOX) {
            val dx = b.centerX - a.centerX
            val dy = b.centerY - a.centerY
            val overlapX = a.halfWidth + b.halfWidth - abs(dx)
            val overlapY = a.halfHeight + b.halfHeight - abs(dy)
            if (overlapX <= 0f || overlapY <= 0f) return null
            return if (overlapX < overlapY) {
                ContactManifold(if (dx >= 0f) 1f else -1f, 0f, overlapX)
            } else {
                ContactManifold(0f, if (dy >= 0f) 1f else -1f, overlapY)
            }
        }

        val circleIsFirst = first.shape == Collider2D.Shape.CIRCLE
        val circle = if (circleIsFirst) a else b
        val box = if (circleIsFirst) b else a
        val nearestX = circle.centerX.coerceIn(box.centerX - box.halfWidth, box.centerX + box.halfWidth)
        val nearestY = circle.centerY.coerceIn(box.centerY - box.halfHeight, box.centerY + box.halfHeight)
        val fromCircleX = nearestX - circle.centerX
        val fromCircleY = nearestY - circle.centerY
        val distanceSquared = fromCircleX * fromCircleX + fromCircleY * fromCircleY
        val radiusSquared = circle.radius * circle.radius
        if (distanceSquared > radiusSquared) return null

        val circleToBoxX: Float
        val circleToBoxY: Float
        val penetration: Float
        if (distanceSquared > 0.000001f) {
            val distance = sqrt(distanceSquared)
            circleToBoxX = fromCircleX / distance
            circleToBoxY = fromCircleY / distance
            penetration = circle.radius - distance
        } else {
            val left = circle.centerX - (box.centerX - box.halfWidth)
            val right = (box.centerX + box.halfWidth) - circle.centerX
            val top = circle.centerY - (box.centerY - box.halfHeight)
            val bottom = (box.centerY + box.halfHeight) - circle.centerY
            val nearestEdge = minOf(left, right, top, bottom)
            // The contact normal points from the circle toward the box. When the
            // circle is embedded, that direction is opposite the nearest exit direction:
            // the solver moves the first body against the normal to separate it.
            when (nearestEdge) {
                left -> { circleToBoxX = 1f; circleToBoxY = 0f }
                right -> { circleToBoxX = -1f; circleToBoxY = 0f }
                top -> { circleToBoxX = 0f; circleToBoxY = 1f }
                else -> { circleToBoxX = 0f; circleToBoxY = -1f }
            }
            penetration = circle.radius + nearestEdge
        }
        return if (circleIsFirst) {
            ContactManifold(circleToBoxX, circleToBoxY, penetration)
        } else {
            ContactManifold(-circleToBoxX, -circleToBoxY, penetration)
        }
    }

    private fun resolveCollision(first: Collider2D, second: Collider2D) {
        val firstBody = bodyOf(first)
        val secondBody = bodyOf(second)
        val inverseFirst = firstBody?.inverseMass ?: 0f
        val inverseSecond = secondBody?.inverseMass ?: 0f
        val inverseTotal = inverseFirst + inverseSecond
        if (inverseTotal <= 0f) return

        val contact = contactManifold(first, second) ?: return
        val normalX = contact.normalX
        val normalY = contact.normalY
        val correction = (contact.penetration - 0.01f).coerceAtLeast(0f) * 0.8f / inverseTotal
        first.gameObject.transform.x -= normalX * correction * inverseFirst
        first.gameObject.transform.y -= normalY * correction * inverseFirst
        second.gameObject.transform.x += normalX * correction * inverseSecond
        second.gameObject.transform.y += normalY * correction * inverseSecond

        val relativeVelocity = ((secondBody?.velocityX ?: 0f) - (firstBody?.velocityX ?: 0f)) * normalX +
            ((secondBody?.velocityY ?: 0f) - (firstBody?.velocityY ?: 0f)) * normalY
        if (relativeVelocity >= 0f) return

        val impactSpeed = -relativeVelocity
        val threshold = max(firstBody?.restitutionVelocityThreshold ?: 1f, secondBody?.restitutionVelocityThreshold ?: 1f)
        val restitution = if (impactSpeed >= threshold) {
            minOf(firstBody?.restitution ?: 0f, secondBody?.restitution ?: 0f)
        } else 0f
        val impulseMagnitude = -(1f + restitution) * relativeVelocity / inverseTotal
        val impulseX = impulseMagnitude * normalX
        val impulseY = impulseMagnitude * normalY
        firstBody?.applyImpulse(-impulseX, -impulseY)
        secondBody?.applyImpulse(impulseX, impulseY)

        // Coulomb friction opposes tangential motion and is limited by the normal impulse.
        val postNormalX = (secondBody?.velocityX ?: 0f) - (firstBody?.velocityX ?: 0f)
        val postNormalY = (secondBody?.velocityY ?: 0f) - (firstBody?.velocityY ?: 0f)
        val tangentVelocityX = postNormalX - (postNormalX * normalX + postNormalY * normalY) * normalX
        val tangentVelocityY = postNormalY - (postNormalX * normalX + postNormalY * normalY) * normalY
        val tangentSpeed = sqrt(tangentVelocityX * tangentVelocityX + tangentVelocityY * tangentVelocityY)
        if (tangentSpeed > 0.000001f) {
            val tangentX = tangentVelocityX / tangentSpeed
            val tangentY = tangentVelocityY / tangentSpeed
            val friction = sqrt((firstBody?.friction ?: 0f) * (secondBody?.friction ?: 0f))
            val frictionMagnitude = (tangentSpeed / inverseTotal)
                .coerceAtMost(impulseMagnitude * friction)
            val frictionX = frictionMagnitude * tangentX
            val frictionY = frictionMagnitude * tangentY
            firstBody?.applyImpulse(frictionX, frictionY)
            secondBody?.applyImpulse(-frictionX, -frictionY)
        }
    }

    fun clear() {
        for (pair in activePairs) {
            pair.first.notifyExit(pair.second)
            pair.second.notifyExit(pair.first)
        }
        activePairs.clear()
        currentPairs.clear()
        colliders.clear()
        oversizedColliders.clear()
        grid.clear()
        previousBounds.clear()
        currentBounds.clear()
        physicalPairs.clear()
        bodyCache.clear()
    }

    private companion object {
        const val OVERSIZED_BUCKET = Long.MIN_VALUE
        const val SOLVER_ITERATIONS = 6
    }
}
