package com.isanabanana.aim

import android.graphics.Canvas

/** A self-contained game screen or level. */
open class GameScene {
    private val objectList = mutableListOf<GameObject>()
    private val pendingAdditions = mutableListOf<GameObject>()
    private val pendingRemovals = mutableSetOf<GameObject>()
    private val pendingRecycles = mutableSetOf<GameObject>()
    private val pointerCaptures = mutableMapOf<Int, Pair<GameObject, GameTouchEvent>>()
    private val collisionSystem = CollisionSystem()
    private val renderOrder = mutableListOf<GameObject>()
    val camera = Camera2D()
    private var started = false
    private var paused = false
    private var destroyed = false
    private var textInputFocusListener: ((Boolean) -> Unit)? = null

    val gameObjects: List<GameObject> get() = objectList.filter { it.isActive }

    fun add(gameObject: GameObject): GameObject {
        check(!destroyed) { "Cannot add objects to a destroyed scene." }
        check(!objectList.contains(gameObject) && !pendingAdditions.contains(gameObject)) {
            "This GameObject is already part of the scene."
        }
        check(gameObject !in pendingRemovals && gameObject !in pendingRecycles) {
            "This GameObject is pending removal or recycling."
        }
        if (!started) objectList += gameObject else pendingAdditions += gameObject
        return gameObject
    }

    /** Removes an object permanently and calls its component destruction callbacks. */
    fun remove(gameObject: GameObject): Boolean {
        if (destroyed) return false
        val exists = objectList.contains(gameObject) || pendingAdditions.contains(gameObject)
        if (!exists || !pendingRemovals.add(gameObject)) return false
        cancelCapturesFor(gameObject)
        clearTextFieldFocus(gameObject)
        pendingRecycles.remove(gameObject)
        gameObject.deactivate()
        if (!started) flushChanges()
        return true
    }

    /** Detaches an object without destroying its components, allowing it to be added to a scene again later. */
    internal fun canRecycle(gameObject: GameObject): Boolean =
        !destroyed && gameObject in objectList &&
            gameObject !in pendingRemovals && gameObject !in pendingRecycles

    fun recycle(gameObject: GameObject): Boolean {
        if (!canRecycle(gameObject) || !pendingRecycles.add(gameObject)) return false
        cancelCapturesFor(gameObject)
        clearTextFieldFocus(gameObject)
        gameObject.deactivate()
        if (!started) flushChanges()
        return true
    }

    fun findByName(name: String): GameObject? =
        objectList.firstOrNull { it.isActive && it.name == name }

    internal fun start() {
        if (started || destroyed) return
        onStart()
        started = true
        for (index in objectList.indices) objectList[index].start()
        flushChanges()
    }

    protected open fun onStart() = Unit

    /** Called when this scene is covered by another scene in a SceneManager stack. */
    protected open fun onPause() = Unit

    /** Called when this scene becomes the active scene again after a pop. */
    protected open fun onResume() = Unit

    internal fun pause() {
        if (!started || paused || destroyed) return
        paused = true
        cancelTouches()
        val hadFocusedField = focusedTextInput() != null
        clearTextFieldFocus()
        if (hadFocusedField) textInputFocusListener?.invoke(false)
        onPause()
    }

    internal fun resume() {
        if (!started || !paused || destroyed) return
        paused = false
        onResume()
    }

    open fun update(deltaSeconds: Float) {
        if (destroyed || paused) return
        for (index in objectList.indices) {
            val gameObject = objectList[index]
            if (gameObject.isActive && gameObject !in pendingRemovals) gameObject.update(deltaSeconds)
        }
        collisionSystem.update(objectList, pendingRemovals, deltaSeconds)
        flushChanges()
    }

    open fun draw(canvas: Canvas) {
        if (destroyed || paused) return
        renderOrder.clear()
        for (gameObject in objectList) {
            if (gameObject.isActive && gameObject !in pendingRemovals) renderOrder.add(gameObject)
        }
        renderOrder.sortWith(RENDER_ORDER_COMPARATOR)

        // Keep one reusable sorted buffer and draw in two passes. This avoids
        // rebuilding separate world/screen lists on every rendered frame.
        val worldSave = canvas.save()
        camera.apply(canvas)
        for (gameObject in renderOrder) {
            if (!gameObject.screenSpace) gameObject.draw(canvas)
        }
        canvas.restoreToCount(worldSave)
        for (gameObject in renderOrder) {
            if (gameObject.screenSpace) gameObject.draw(canvas)
        }
    }

    /** Dispatches a touch and captures its pointer until UP/CANCEL for the object that handled DOWN. */
    open fun onTouch(event: GameTouchEvent): Boolean {
        if (destroyed || paused) return false
        val previousFocusedField = focusedTextInput()
        val captured = pointerCaptures[event.pointerId]
        if (captured != null && event.action != GameTouchEvent.Action.DOWN) {
            val target = captured.first
            if (target.isActive && target !in pendingRemovals && target in objectList) {
                val targetEvent = if (target.screenSpace) event else camera.screenToWorld(event)
                target.dispatchTouch(targetEvent)
                if (event.action == GameTouchEvent.Action.UP || event.action == GameTouchEvent.Action.CANCEL) {
                    pointerCaptures.remove(event.pointerId)
                } else pointerCaptures[event.pointerId] = target to event
                flushChanges()
                notifyTextInputFocusChanged(previousFocusedField)
                return true
            }
            pointerCaptures.remove(event.pointerId)
        }

        for (index in objectList.indices.reversed()) {
            val gameObject = objectList[index]
            val objectEvent = if (gameObject.screenSpace) event else camera.screenToWorld(event)
            if (gameObject.isActive && gameObject !in pendingRemovals && gameObject.dispatchTouch(objectEvent)) {
                if (event.action == GameTouchEvent.Action.DOWN) {
                    pointerCaptures[event.pointerId] = gameObject to event
                    keepOnlyTextFieldFocus(gameObject)
                }
                flushChanges()
                notifyTextInputFocusChanged(previousFocusedField)
                return true
            }
        }
        if (event.action == GameTouchEvent.Action.DOWN) clearTextFieldFocus()
        flushChanges()
        notifyTextInputFocusChanged(previousFocusedField)
        return false
    }

    internal fun focusedTextInput(): UITextField? =
        objectList.asSequence().filter { it.isActive && it !in pendingRemovals }
            .flatMap { it.components.asSequence() }
            .filterIsInstance<UITextField>().firstOrNull { it.isFocused }

    internal fun setTextInputFocusListener(listener: ((Boolean) -> Unit)?) {
        textInputFocusListener = listener
    }

    internal fun commitText(value: String) { focusedTextInput()?.insertText(value) }
    internal fun deleteTextBackward() { focusedTextInput()?.deleteBackward() }
    internal fun submitText() { focusedTextInput()?.submit() }

    private fun clearTextFieldFocus() {
        objectList.asSequence().flatMap { it.components.asSequence() }
            .filterIsInstance<UITextField>().forEach { it.setFocused(false) }
    }

    private fun keepOnlyTextFieldFocus(target: GameObject) {
        val selected = target.components.filterIsInstance<UITextField>().firstOrNull { it.isFocused }
        objectList.asSequence().flatMap { it.components.asSequence() }
            .filterIsInstance<UITextField>()
            .filter { it !== selected }
            .forEach { it.setFocused(false) }
    }

    private fun clearTextFieldFocus(gameObject: GameObject) {
        val focused = gameObject.components.filterIsInstance<UITextField>().any { it.isFocused }
        gameObject.components.filterIsInstance<UITextField>().forEach { it.setFocused(false) }
        if (focused) textInputFocusListener?.invoke(false)
    }

    private fun notifyTextInputFocusChanged(previous: UITextField?) {
        val current = focusedTextInput()
        if (previous !== current) textInputFocusListener?.invoke(current != null)
    }

    /** Cancels every active pointer capture, for example when Android interrupts a gesture. */
    internal fun cancelTouches() {
        val captures = pointerCaptures.toMap()
        pointerCaptures.clear()
        for ((pointerId, capture) in captures) {
            val target = capture.first
            if (target.isActive && target in objectList) {
                val cancel = capture.second.copy(action = GameTouchEvent.Action.CANCEL, pointerId = pointerId)
                target.dispatchTouch(if (target.screenSpace) cancel else camera.screenToWorld(cancel))
            }
        }
    }

    private fun cancelCapturesFor(gameObject: GameObject) {
        val ids = pointerCaptures.filterValues { it.first === gameObject }.keys.toList()
        for (id in ids) {
            val previous = pointerCaptures.remove(id)?.second ?: continue
            gameObject.dispatchTouch(previous.copy(action = GameTouchEvent.Action.CANCEL, pointerId = id))
        }
    }

    private fun flushChanges() {
        if (pendingRecycles.isNotEmpty()) {
            val recycled = pendingRecycles.toList()
            pendingRecycles.clear()
            objectList.removeAll(recycled.toSet())
        }
        if (pendingRemovals.isNotEmpty()) {
            val removals = pendingRemovals.toList()
            pendingRemovals.clear()
            for (gameObject in removals) {
                if (objectList.remove(gameObject)) gameObject.destroy()
                else if (pendingAdditions.remove(gameObject)) gameObject.destroy()
            }
        }
        if (pendingAdditions.isNotEmpty()) {
            val additions = pendingAdditions.toList()
            pendingAdditions.clear()
            for (gameObject in additions) {
                if (!destroyed) {
                    objectList.add(gameObject)
                    gameObject.start()
                } else gameObject.destroy()
            }
        }
    }

    internal fun destroy() {
        if (destroyed) return
        cancelTouches()
        clearTextFieldFocus()
        textInputFocusListener?.invoke(false)
        destroyed = true
        // End active contacts while collider components are still alive.
        collisionSystem.clear()
        (objectList.asReversed() + pendingAdditions.asReversed()).distinct().forEach(GameObject::destroy)
        objectList.clear()
        pendingAdditions.clear()
        pendingRemovals.clear()
        pendingRecycles.clear()
        pointerCaptures.clear()
        renderOrder.clear()
        onDestroy()
    }

    protected open fun onDestroy() = Unit

    private companion object {
        // Shared comparator: do not allocate a new comparator/lambda chain per frame.
        val RENDER_ORDER_COMPARATOR: Comparator<GameObject> =
            compareBy<GameObject> { it.renderLayer }.thenBy { it.renderOrder }
    }
}
