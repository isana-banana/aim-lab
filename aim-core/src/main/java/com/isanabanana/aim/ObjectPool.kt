package com.isanabanana.aim

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Generic reusable-object pool.
 *
 * This pool is intentionally independent from GameObject and Android so it can
 * safely be used for particles, temporary data, effects, and other short-lived
 * objects. GameObject pooling needs scene activation/deactivation lifecycle
 * support and is handled separately.
 */
class ObjectPool<T : Any>(
    private val create: () -> T,
    private val reset: (T) -> Unit = {},
    private val onAcquire: (T) -> Unit = {},
    private val onRelease: (T) -> Unit = {},
    val maxSize: Int = 128
) {
    private val available = ArrayDeque<T>()
    private val known = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
    private val active = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())

    var totalCreated: Int = 0
        private set

    val availableCount: Int
        @Synchronized get() = available.size

    val activeCount: Int
        @Synchronized get() = active.size

    init {
        require(maxSize >= 0) { "maxSize cannot be negative." }
    }

    /** Gets a reusable object, or creates one when the pool is empty. */
    @Synchronized
    fun acquire(): T {
        val item = if (available.isEmpty()) {
            create().also {
                check(known.add(it)) { "Pool factory returned an object already owned by this pool." }
                totalCreated++
            }
        } else {
            available.removeFirst()
        }

        check(active.add(item)) { "Pool integrity error: item is already active." }
        try {
            onAcquire(item)
        } catch (error: Throwable) {
            // A failed acquire must not strand an item outside both pool queues.
            active.remove(item)
            val resetSucceeded = try {
                reset(item)
                true
            } catch (_: Throwable) {
                false
            }
            if (resetSucceeded && available.size < maxSize) {
                available.addLast(item)
            } else {
                known.remove(item)
            }
            throw error
        }
        return item
    }

    /**
     * Returns an active object to the pool.
     * Returns false for objects not acquired from this pool or already released.
     *
     * The item remains active until both cleanup callbacks succeed. If either
     * callback throws, callers can correct the problem and retry the release.
     */
    @Synchronized
    fun release(item: T): Boolean {
        if (!known.contains(item) || !active.contains(item)) return false

        // Run user cleanup before changing ownership state. A thrown callback
        // therefore cannot silently lose an item or make it appear available.
        reset(item)
        onRelease(item)
        check(active.remove(item)) { "Pool integrity error: active item disappeared during release." }

        if (available.size < maxSize) {
            available.addLast(item)
        } else {
            // The pool is full; let this object be collected instead of retaining it.
            known.remove(item)
        }
        return true
    }

    /** Creates and stores up to [count] objects ahead of time. */
    @Synchronized
    fun prewarm(count: Int) {
        require(count >= 0) { "count cannot be negative." }
        val target = minOf(count, maxSize)
        while (available.size < target) {
            val item = create()
            check(known.add(item)) { "Pool factory returned an object already owned by this pool." }
            available.addLast(item)
            totalCreated++
        }
    }

    /** Removes currently available objects. Active objects remain valid and tracked. */
    @Synchronized
    fun clear() {
        available.forEach { known.remove(it) }
        available.clear()
    }
}
