package com.yogi.exitarrow.game

import com.yogi.exitarrow.Puzzle
import com.yogi.exitarrow.PuzzleGenerator
import com.yogi.exitarrow.PuzzleSolver

interface PuzzleEngine {
    fun createLevel(level: Int): List<ArrowState>
    fun canExit(arrowId: String, currentArrows: List<ArrowState>): Boolean
    fun findHint(currentArrows: List<ArrowState>): String?
}

class GamePuzzleEngine : PuzzleEngine {
    var currentPuzzle: Puzzle? = null
        private set

    fun loadPuzzleForLevel(level: Int, maxResponsiveGridSize: Int = 16): Puzzle {
        val puzzle = PuzzleGenerator.generate(level, maxResponsiveGridSize)
        currentPuzzle = puzzle
        return puzzle
    }

    override fun createLevel(level: Int): List<ArrowState> {
        val puzzle = currentPuzzle ?: PuzzleGenerator.generate(level)
        currentPuzzle = puzzle
        return puzzle.arrows.map { ArrowState(id = it.id.toString(), removed = !it.active) }
    }

    override fun canExit(arrowId: String, currentArrows: List<ArrowState>): Boolean {
        val puzzle = currentPuzzle ?: return false
        val intId = arrowId.toIntOrNull() ?: return false
        val arrow = puzzle.arrows.firstOrNull { it.id == intId } ?: return false
        if (!arrow.active) return false

        val removedIds = currentArrows.filter { it.removed }.mapNotNull { it.id.toIntOrNull() }.toSet()

        return PuzzleSolver.isClear(puzzle, arrow) { other ->
            !other.active || removedIds.contains(other.id)
        }
    }

    override fun findHint(currentArrows: List<ArrowState>): String? {
        val puzzle = currentPuzzle ?: return null
        val removedIds = currentArrows.filter { it.removed }.mapNotNull { it.id.toIntOrNull() }.toSet()
        val activePuzzle = puzzle.copy(
            arrows = puzzle.arrows.filter { !removedIds.contains(it.id) && it.active }.toMutableList(),
        )
        val (arrowId) = PuzzleSolver.nextHint(activePuzzle) ?: return null
        return arrowId.toString()
    }
}
