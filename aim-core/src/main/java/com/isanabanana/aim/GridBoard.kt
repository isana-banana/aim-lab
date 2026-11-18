package com.isanabanana.aim

/** Integer coordinate of one cell in a 2D grid. Column grows right; row grows down. */
data class GridCell(val column: Int, val row: Int)

/**
 * Reusable rectangular grid model.
 *
 * originX/originY describe the center of the top-left cell.
 * The board stores data only; rendering and game rules belong to the game.
 */
class GridBoard<T>(
    val columns: Int,
    val rows: Int,
    val cellWidth: Float,
    val cellHeight: Float = cellWidth,
    val originX: Float = 0f,
    val originY: Float = 0f
) {
    private val cells: MutableList<MutableList<T?>> =
        MutableList(rows) { MutableList<T?>(columns) { null } }

    init {
        require(columns > 0 && rows > 0) { "Grid dimensions must be positive." }
        require(cellWidth > 0f && cellHeight > 0f) { "Cell dimensions must be positive." }
    }

    val size: Int get() = columns * rows

    operator fun get(column: Int, row: Int): T? =
        if (contains(column, row)) cells[row][column] else null

    operator fun get(cell: GridCell): T? = get(cell.column, cell.row)

    fun set(column: Int, row: Int, value: T?): Boolean {
        if (!contains(column, row)) return false
        cells[row][column] = value
        return true
    }

    fun set(cell: GridCell, value: T?): Boolean = set(cell.column, cell.row, value)

    fun contains(column: Int, row: Int): Boolean =
        column in 0 until columns && row in 0 until rows

    fun contains(cell: GridCell): Boolean = contains(cell.column, cell.row)

    fun cellToWorld(cell: GridCell): Pair<Float, Float> {
        require(contains(cell)) { "Cell is outside this grid." }
        return Pair(
            originX + cell.column * cellWidth,
            originY + cell.row * cellHeight
        )
    }

    fun worldToCell(x: Float, y: Float): GridCell? {
        val column = kotlin.math.floor((x - originX + cellWidth / 2f) / cellWidth).toInt()
        val row = kotlin.math.floor((y - originY + cellHeight / 2f) / cellHeight).toInt()
        val cell = GridCell(column, row)
        if (!contains(cell)) return null

        val center = cellToWorld(cell)
        val insideX = kotlin.math.abs(x - center.first) <= cellWidth / 2f
        val insideY = kotlin.math.abs(y - center.second) <= cellHeight / 2f
        return if (insideX && insideY) cell else null
    }

    fun swap(first: GridCell, second: GridCell): Boolean {
        if (!contains(first) || !contains(second)) return false
        val value = this[first]
        set(first, this[second])
        set(second, value)
        return true
    }

    fun clear() {
        for (row in 0 until rows) {
            for (column in 0 until columns) cells[row][column] = null
        }
    }

    fun neighbors4(cell: GridCell): List<GridCell> {
        if (!contains(cell)) return emptyList()
        return listOf(
            GridCell(cell.column, cell.row - 1),
            GridCell(cell.column + 1, cell.row),
            GridCell(cell.column, cell.row + 1),
            GridCell(cell.column - 1, cell.row)
        ).filter(::contains)
    }

    /** Returns all cells belonging to horizontal or vertical runs of minLength or longer. */
    fun findMatches(
        minLength: Int = 3,
        sameKind: (T, T) -> Boolean
    ): Set<GridCell> {
        require(minLength >= 2) { "Minimum match length must be at least 2." }
        val matched = linkedSetOf<GridCell>()

        // Horizontal runs.
        for (row in 0 until rows) {
            var start = 0
            while (start < columns) {
                val first = cells[row][start]
                if (first == null) {
                    start++
                    continue
                }
                var end = start + 1
                while (end < columns) {
                    val next = cells[row][end] ?: break
                    if (!sameKind(first, next)) break
                    end++
                }
                if (end - start >= minLength) {
                    for (column in start until end) matched += GridCell(column, row)
                }
                start = end
            }
        }

        // Vertical runs.
        for (column in 0 until columns) {
            var start = 0
            while (start < rows) {
                val first = cells[start][column]
                if (first == null) {
                    start++
                    continue
                }
                var end = start + 1
                while (end < rows) {
                    val next = cells[end][column] ?: break
                    if (!sameKind(first, next)) break
                    end++
                }
                if (end - start >= minLength) {
                    for (row in start until end) matched += GridCell(column, row)
                }
                start = end
            }
        }
        return matched
    }

    fun forEach(action: (GridCell, T?) -> Unit) {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                action(GridCell(column, row), cells[row][column])
            }
        }
    }
}
