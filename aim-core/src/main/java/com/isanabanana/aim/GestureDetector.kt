package com.isanabanana.aim

import kotlin.math.hypot

/**
 * Optional gesture recognizer for a GameObject.
 *
 * Attach to a full-screen input object, or provide [hitTest] to limit which DOWN
 * events it accepts. Callbacks run on the game/UI thread.
 *
 * Pointer snapshots are applied atomically, avoiding intermediate pinch centers
 * when Android reports multiple fingers in one MOVE frame. Large discontinuous
 * pointer jumps reset the gesture baseline instead of producing camera movement.
 */
class GestureDetector(
    var touchSlop: Float = 8f,
    var tapMaxDuration: Float = 0.28f,
    var longPressDuration: Float = 0.55f,
    var maxPointerStep: Float = 96f,
    var hitTest: ((Float, Float) -> Boolean)? = null
) : Component() {

    private data class PointerState(
        var startX: Float,
        var startY: Float,
        var x: Float,
        var y: Float,
        var elapsed: Float = 0f,
        var dragging: Boolean = false,
        var longPressed: Boolean = false
    )

    private val pointers = linkedMapOf<Int, PointerState>()
    private var multiTouchOccurred = false
    private var previousPinchDistance = 0f
    private var previousCenterX = 0f
    private var previousCenterY = 0f

    var onTap: ((x: Float, y: Float) -> Unit)? = null
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null
    var onDragStart: ((x: Float, y: Float) -> Unit)? = null
    var onDrag: ((deltaX: Float, deltaY: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onPan: ((deltaX: Float, deltaY: Float) -> Unit)? = null
    var onPinch: ((scaleFactor: Float, centerX: Float, centerY: Float) -> Unit)? = null

    init {
        require(touchSlop >= 0f)
        require(tapMaxDuration >= 0f)
        require(longPressDuration >= 0f)
        require(maxPointerStep > touchSlop)
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (hitTest?.invoke(event.x, event.y) == false) return false
                pointers[event.pointerId] = PointerState(event.x, event.y, event.x, event.y)
                if (pointers.size > 1) multiTouchOccurred = true
                resetMultiTouchBaseline()
                return true
            }
            GameTouchEvent.Action.MOVE -> {
                val state = pointers[event.pointerId] ?: return false
                val previousX = state.x
                val previousY = state.y
                val snapshot = event.pointerPositions
                var discontinuity = false
                if (snapshot.isNotEmpty()) {
                    for (position in snapshot) {
                        val tracked = pointers[position.pointerId] ?: continue
                        if (hypot(position.x - tracked.x, position.y - tracked.y) > maxPointerStep) {
                            discontinuity = true
                        }
                    }
                    for (position in snapshot) {
                        pointers[position.pointerId]?.let {
                            it.x = position.x
                            it.y = position.y
                        }
                    }
                } else {
                    if (hypot(event.x - state.x, event.y - state.y) > maxPointerStep) {
                        discontinuity = true
                    }
                    state.x = event.x
                    state.y = event.y
                }

                if (discontinuity) {
                    // A sudden coordinate relocation is a new anchor, not a swipe.
                    pointers.values.forEach {
                        it.startX = it.x
                        it.startY = it.y
                        it.dragging = false
                        it.longPressed = false
                    }
                    resetMultiTouchBaseline()
                    return true
                }

                if (pointers.size >= 2) {
                    dispatchMultiTouch()
                } else {
                    val current = pointers.values.first()
                    val dxFromStart = current.x - current.startX
                    val dyFromStart = current.y - current.startY
                    if (!current.dragging && hypot(dxFromStart, dyFromStart) > touchSlop) {
                        current.dragging = true
                        onDragStart?.invoke(current.startX, current.startY)
                    }
                    if (current.dragging) {
                        onDrag?.invoke(current.x - previousX, current.y - previousY)
                    }
                }
                return true
            }
            GameTouchEvent.Action.UP -> {
                val state = pointers.remove(event.pointerId) ?: return false
                if (state.dragging) onDragEnd?.invoke()
                else if (!multiTouchOccurred && !state.longPressed && state.elapsed <= tapMaxDuration &&
                    hypot(event.x - state.startX, event.y - state.startY) <= touchSlop
                ) onTap?.invoke(event.x, event.y)
                if (pointers.isEmpty()) multiTouchOccurred = false
                resetMultiTouchBaseline()
                return true
            }
            GameTouchEvent.Action.CANCEL -> {
                if (pointers.remove(event.pointerId) == null) return false
                if (pointers.isEmpty()) {
                    onDragEnd?.invoke()
                    multiTouchOccurred = false
                }
                resetMultiTouchBaseline()
                return true
            }
        }
    }

    override fun update(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        for (state in pointers.values) {
            state.elapsed += deltaSeconds
            if (pointers.size == 1 && !state.dragging && !state.longPressed &&
                state.elapsed >= longPressDuration &&
                hypot(state.x - state.startX, state.y - state.startY) <= touchSlop
            ) {
                state.longPressed = true
                onLongPress?.invoke(state.x, state.y)
            }
        }
    }

    override fun onDisable() {
        pointers.clear()
        multiTouchOccurred = false
        resetMultiTouchBaseline()
    }

    override fun onDestroy() {
        pointers.clear()
        multiTouchOccurred = false
        resetMultiTouchBaseline()
    }

    private fun resetMultiTouchBaseline() {
        if (pointers.size < 2) {
            previousPinchDistance = 0f
            previousCenterX = 0f
            previousCenterY = 0f
        } else {
            val pair = pointers.values.take(2)
            previousPinchDistance = distance(pair[0], pair[1])
            previousCenterX = (pair[0].x + pair[1].x) / 2f
            previousCenterY = (pair[0].y + pair[1].y) / 2f
        }
    }

    private fun dispatchMultiTouch() {
        val pair = pointers.values.take(2)
        val first = pair[0]
        val second = pair[1]
        val distance = distance(first, second)
        val centerX = (first.x + second.x) / 2f
        val centerY = (first.y + second.y) / 2f
        if (previousPinchDistance > 0f && distance > 0f) {
            onPinch?.invoke(distance / previousPinchDistance, centerX, centerY)
        }
        if (previousPinchDistance > 0f) {
            onPan?.invoke(centerX - previousCenterX, centerY - previousCenterY)
        }
        previousPinchDistance = distance
        previousCenterX = centerX
        previousCenterY = centerY
    }

    private fun distance(a: PointerState, b: PointerState): Float =
        hypot(a.x - b.x, a.y - b.y)
}