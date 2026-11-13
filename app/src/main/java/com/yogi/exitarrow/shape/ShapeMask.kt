package com.yogi.exitarrow.shape

/**
 * A logical, resolution-independent silhouette raster.
 * A true cell belongs to the selected object's usable area.
 */
class ShapeMask(
    val rows: Int,
    val cols: Int,
    val template: ShapeTemplate,
    val formation: ShapeFormation,
    private val cells: Array<BooleanArray>,
    private val contourCells: Set<Pair<Int, Int>>
) {
    init {
        require(rows > 0 && cols > 0)
        require(cells.size == rows && cells.all { it.size == cols })
    }

    val area: Int = cells.sumOf { row -> row.count { it } }
    val contourArea: Int = contourCells.size

    fun contains(row: Int, col: Int): Boolean =
        row in 0 until rows && col in 0 until cols && cells[row][col]

    fun isContour(row: Int, col: Int): Boolean =
        (row to col) in contourCells

    fun allCells(): List<Pair<Int, Int>> = buildList(area) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (cells[row][col]) add(row to col)
            }
        }
    }

    fun contourCells(): List<Pair<Int, Int>> = contourCells.toList()

    fun copyCells(): Array<BooleanArray> = Array(rows) { r -> cells[r].clone() }
}
