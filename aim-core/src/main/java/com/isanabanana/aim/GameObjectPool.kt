package com.isanabanana.aim

/**
 * Scene-aware pool for reusable GameObjects.
 *
 * Recycled objects are detached from their scene without destroying their
 * components. Supply [reset] to restore custom component state before reuse.
 */
class GameObjectPool(
    private val create: () -> GameObject,
    private val reset: (GameObject) -> Unit = {},
    maxSize: Int = 64
) {
    private val pool = ObjectPool(
        create = create,
        maxSize = maxSize
    )

    /** Acquires an object, resets it through the pool, and adds it to [scene]. */
    fun acquire(scene: GameScene): GameObject {
        val gameObject = pool.acquire()
        return try {
            scene.add(gameObject)
        } catch (error: Throwable) {
            pool.release(gameObject)
            throw error
        }
    }

    /** Recycles an object from [scene] without destroying its components. */
    fun release(scene: GameScene, gameObject: GameObject): Boolean {
        if (!scene.canRecycle(gameObject)) return false
        // Run user code before changing scene or pool ownership so failures are retryable.
        reset(gameObject)
        if (!scene.recycle(gameObject)) return false
        return pool.release(gameObject)
    }

    fun prewarm(count: Int) = pool.prewarm(count)

    fun clear() = pool.clear()

    val availableCount: Int get() = pool.availableCount
    val activeCount: Int get() = pool.activeCount
    val totalCreated: Int get() = pool.totalCreated
}
