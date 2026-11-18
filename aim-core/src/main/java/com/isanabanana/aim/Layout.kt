package com.isanabanana.aim

/**
 * Small sequential layout helper for beginner UI.
 *
 * Coordinates are virtual-world units. A column starts at its top edge;
 * a row starts at its left edge. Each item callback receives its center point.
 */
class Layout internal constructor(
    private val direction: Direction,
    private val originX: Float,
    private val originY: Float,
    private val crossSize: Float,
    private val spacing: Float,
    private val alignment: Alignment
) {
    enum class Alignment { START, CENTER, END }
    internal enum class Direction { ROW, COLUMN }

    private var cursor = 0f

    init {
        require(crossSize > 0f) { "Layout cross size must be greater than zero." }
        require(spacing >= 0f && spacing.isFinite()) { "Layout spacing must be finite and non-negative." }
        require(originX.isFinite() && originY.isFinite())
    }

    /** Add an item and place its center automatically. Returns this layout for chaining. */
    fun add(width: Float, height: Float, place: (x: Float, y: Float) -> Unit): Layout {
        require(width > 0f && height > 0f && width.isFinite() && height.isFinite()) {
            "Layout item dimensions must be finite and greater than zero."
        }
        if (direction == Direction.COLUMN) {
            require(width <= crossSize) { "Item width cannot exceed the column width." }
            val x = alignedPosition(originX - crossSize / 2f, crossSize, width)
            place(x, originY + cursor + height / 2f)
            cursor += height + spacing
        } else {
            require(height <= crossSize) { "Item height cannot exceed the row height." }
            val y = alignedPosition(originY - crossSize / 2f, crossSize, height)
            place(originX + cursor + width / 2f, y)
            cursor += width + spacing
        }
        return this
    }

    private fun alignedPosition(start: Float, available: Float, item: Float): Float =
        when (alignment) {
            Alignment.START -> start + item / 2f
            Alignment.CENTER -> start + available / 2f
            Alignment.END -> start + available - item / 2f
        }
}

/** Create a vertical layout. [topY] is the top edge and items flow downward. */
fun Scene.column(
    centerX: Float,
    topY: Float,
    width: Float,
    spacing: Float = 12f,
    alignment: Layout.Alignment = Layout.Alignment.CENTER
): Layout = Layout(Layout.Direction.COLUMN, centerX, topY, width, spacing, alignment)

/** Create a horizontal layout. [leftX] is the left edge and items flow rightward. */
fun Scene.row(
    leftX: Float,
    centerY: Float,
    height: Float,
    spacing: Float = 12f,
    alignment: Layout.Alignment = Layout.Alignment.CENTER
): Layout = Layout(Layout.Direction.ROW, leftX, centerY, height, spacing, alignment)
