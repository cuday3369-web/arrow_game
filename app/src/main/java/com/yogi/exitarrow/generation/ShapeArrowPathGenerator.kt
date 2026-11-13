package com.yogi.exitarrow.generation

import com.yogi.exitarrow.Direction
import com.yogi.exitarrow.shape.ShapeMask
import kotlin.random.Random

/** Creates simple, self-avoiding, orthogonal paths that stay inside a silhouette mask. */
object ShapeArrowPathGenerator {
    data class HeadCandidate(
        val row: Int,
        val col: Int,
        val direction: Direction
    )

    fun findAvailableHeads(mask: ShapeMask, occupied: Array<BooleanArray>): List<HeadCandidate> {
        val result = mutableListOf<HeadCandidate>()
        for (row in 0 until mask.rows) {
            for (col in 0 until mask.cols) {
                if (!mask.contains(row, col) || occupied[row][col]) continue
                for (direction in Direction.values()) {
                    val (dr, dc) = delta(direction)
                    val tailRow = row - dr
                    val tailCol = col - dc
                    if (!mask.contains(tailRow, tailCol) || occupied[tailRow][tailCol]) continue
                    if (isRayClear(row, col, direction, mask.rows, mask.cols, occupied)) {
                        result += HeadCandidate(row, col, direction)
                    }
                }
            }
        }
        return result
    }

    /**
     * Returns points in the app's existing convention: tail first, arrowhead last.
     * The final path segment always points in [candidate.direction].
     */
    fun createPath(
        mask: ShapeMask,
        occupied: Array<BooleanArray>,
        candidate: HeadCandidate,
        random: Random,
        maxPathCells: Int = 6
    ): List<Pair<Int, Int>>? {
        val (dr, dc) = delta(candidate.direction)
        val previous = (candidate.row - dr) to (candidate.col - dc)
        if (!mask.contains(previous.first, previous.second) || occupied[previous.first][previous.second]) {
            return null
        }

        // Build backwards from the head so the last segment has the exact exit direction.
        val backwards = mutableListOf(candidate.row to candidate.col, previous)
        val local = backwards.toMutableSet()
        val desiredLength = random.nextInt(2, maxPathCells.coerceAtLeast(2) + 1)

        while (backwards.size < desiredLength) {
            val (currentRow, currentCol) = backwards.last()
            val neighbors = listOf(
                currentRow - 1 to currentCol,
                currentRow + 1 to currentCol,
                currentRow to currentCol - 1,
                currentRow to currentCol + 1
            ).filter { (r, c) ->
                mask.contains(r, c) && !occupied[r][c] && (r to c) !in local
            }.shuffled(random)

            val next = neighbors.firstOrNull() ?: break
            backwards += next
            local += next
        }

        return backwards.asReversed().takeIf { it.size >= 2 }
    }

    fun isRayClear(
        headRow: Int,
        headCol: Int,
        direction: Direction,
        rows: Int,
        cols: Int,
        occupied: Array<BooleanArray>
    ): Boolean {
        val (dr, dc) = delta(direction)
        var row = headRow + dr
        var col = headCol + dc
        while (row in 0 until rows && col in 0 until cols) {
            if (occupied[row][col]) return false
            row += dr
            col += dc
        }
        return true
    }

    fun delta(direction: Direction): Pair<Int, Int> = when (direction) {
        Direction.LEFT -> 0 to -1
        Direction.RIGHT -> 0 to 1
        Direction.UP -> -1 to 0
        Direction.DOWN -> 1 to 0
    }
}
