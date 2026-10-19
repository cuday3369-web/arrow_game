package com.yogi.exitarrow

object PuzzleSolver {

    fun blockers(
        puzzle: Puzzle,
        arrow: Arrow,
        isAnimating: ((Arrow) -> Boolean)? = null
    ): List<Arrow> {
        if (!arrow.active) return emptyList()

        val blockersList = mutableListOf<Arrow>()
        val (hr, hc) = arrow.path.last()

        var r = hr
        var c = hc

        while (true) {
            when (arrow.direction) {
                Direction.LEFT -> c--
                Direction.RIGHT -> c++
                Direction.UP -> r--
                Direction.DOWN -> r++
            }

            if (r !in 0 until puzzle.rows || c !in 0 until puzzle.cols) {
                break
            }

            // Check if cell (r, c) in front of head is occupied by ANY active arrow that is not currently animating out
            val blockingArrow = puzzle.arrows.firstOrNull { other ->
                other.active &&
                        other != arrow &&
                        (isAnimating == null || !isAnimating(other)) &&
                        other.occupies(r, c)
            }

            if (blockingArrow != null && !blockersList.contains(blockingArrow)) {
                blockersList.add(blockingArrow)
            }
        }

        return blockersList
    }

    fun isClear(
        puzzle: Puzzle,
        arrow: Arrow,
        isAnimating: ((Arrow) -> Boolean)? = null
    ): Boolean =
        arrow.active && blockers(puzzle, arrow, isAnimating).isEmpty()

    fun availableMoves(puzzle: Puzzle): List<Arrow> =
        puzzle.activeArrows().filter { isClear(puzzle, it) }

    /**
     * Simulates removals without modifying the live puzzle.
     * A deterministic valid order proves the puzzle is solvable.
     */
    fun solveOrder(puzzle: Puzzle): List<Int>? {
        val activeMap = puzzle.arrows.associate { it.id to true }.toMutableMap()
        val result = mutableListOf<Int>()

        fun isClearInSimulation(arrow: Arrow): Boolean {
            val (hr, hc) = arrow.path.last()
            var r = hr
            var c = hc

            while (true) {
                when (arrow.direction) {
                    Direction.LEFT -> c--
                    Direction.RIGHT -> c++
                    Direction.UP -> r--
                    Direction.DOWN -> r++
                }

                if (r !in 0 until puzzle.rows || c !in 0 until puzzle.cols) {
                    return true
                }

                if (puzzle.arrows.any { other ->
                        activeMap[other.id] == true && other.occupies(r, c)
                    }) {
                    return false
                }
            }
        }

        while (activeMap.values.any { it }) {
            val next = puzzle.arrows.firstOrNull {
                activeMap[it.id] == true && isClearInSimulation(it)
            } ?: return null

            activeMap[next.id] = false
            result.add(next.id)
        }

        return result
    }

    fun nextHint(puzzle: Puzzle): HintResult? {
        val order = solveOrder(puzzle) ?: return null
        val id = order.firstOrNull { candidateId ->
            puzzle.arrows.any { it.id == candidateId && it.active }
        } ?: return null

        val arrow = puzzle.arrows.firstOrNull { it.id == id } ?: return null

        return HintResult(
            arrowId = arrow.id,
            message = "Try the ${arrow.direction.name.lowercase()} arrow."
        )
    }

    fun isSolved(puzzle: Puzzle): Boolean =
        puzzle.arrows.none { it.active }
}
