package com.isanabanana.aim

/** Position of one pointer in virtual game coordinates. */
data class GamePointerPosition(
    val pointerId: Int,
    val x: Float,
    val y: Float
)

/** Touch data converted to virtual game coordinates. Pointer IDs remain stable for each finger. */
data class GameTouchEvent(
    val action: Action,
    val x: Float,
    val y: Float,
    val pointerId: Int = 0,
    val pointerCount: Int = 1,
    /** Atomic snapshot of all currently accepted pointers for this Android input frame. */
    val pointerPositions: List<GamePointerPosition> = emptyList()
) {
    enum class Action { DOWN, MOVE, UP, CANCEL }
}