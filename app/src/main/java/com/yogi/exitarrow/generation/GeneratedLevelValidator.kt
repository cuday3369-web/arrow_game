package com.yogi.exitarrow.generation

import com.yogi.exitarrow.Direction
import com.yogi.exitarrow.Puzzle
import com.yogi.exitarrow.PuzzleSolver
import com.yogi.exitarrow.shape.ShapeMask

object GeneratedLevelValidator {
    data class Result(
        val valid: Boolean,
        val reasons: List<String>,
        val occupiedMaskCoverage: Float
    )

    fun validate(puzzle: Puzzle, mask: ShapeMask): Result {
        val reasons = mutableListOf<String>()
        val occupiedCells = HashSet<Pair<Int, Int>>()

        if (puzzle.rows != mask.rows || puzzle.cols != mask.cols) {
            reasons += "Puzzle dimensions do not match silhouette mask."
        }
        if (puzzle.arrows.size < 4) reasons += "At least four arrows are required."

        puzzle.arrows.forEach { arrow ->
            if (arrow.path.size < 2) reasons += "Arrow ${arrow.id} has fewer than two path cells."

            arrow.path.forEach { cell ->
                if (!mask.contains(cell.first, cell.second)) {
                    reasons += "Arrow ${arrow.id} leaves the selected silhouette."
                }
                if (!occupiedCells.add(cell)) {
                    reasons += "Two arrows overlap at cell ${cell.first},${cell.second}."
                }
            }

            arrow.path.zipWithNext().forEach { (a, b) ->
                val distance = kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)
                if (distance != 1) reasons += "Arrow ${arrow.id} contains a disconnected path segment."
            }

            if (arrow.path.size >= 2) {
                val previous = arrow.path[arrow.path.lastIndex - 1]
                val head = arrow.path.last()
                val expected = when {
                    head.first < previous.first -> Direction.UP
                    head.first > previous.first -> Direction.DOWN
                    head.second < previous.second -> Direction.LEFT
                    else -> Direction.RIGHT
                }
                if (arrow.direction != expected) reasons += "Arrow ${arrow.id} has an inconsistent arrowhead direction."
            }
        }

        val coverage = if (mask.area == 0) 0f else occupiedCells.size.toFloat() / mask.area
        if (mask.area < 8) reasons += "Silhouette contains too few usable grid cells."
        if (coverage < 0.15f) reasons += "Arrow paths do not cover enough of the silhouette."
        if (PuzzleSolver.solveOrder(puzzle) == null) reasons += "Puzzle has no complete removal sequence."

        return Result(reasons.isEmpty(), reasons.distinct(), coverage)
    }
}
