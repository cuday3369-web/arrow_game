package com.yogi.exitarrow.generation

import com.yogi.exitarrow.Puzzle
import com.yogi.exitarrow.PuzzleSolver

/** Difficulty scoring based on the puzzle that was actually generated, not level number alone. */
object AdaptiveDifficulty {
    data class Metrics(
        val score: Int,
        val arrowCount: Int,
        val dependencyDepth: Int,
        val blockedArrowRatio: Float,
        val initialMoveRatio: Float,
        val averageBends: Float,
        val label: String
    )

    fun analyze(puzzle: Puzzle): Metrics {
        val arrows = puzzle.arrows
        if (arrows.isEmpty()) {
            return Metrics(0, 0, 0, 0f, 0f, 0f, "Beginner")
        }

        val dependencies = arrows.associate { arrow ->
            arrow.id to PuzzleSolver.blockers(puzzle, arrow).map { it.id }
        }
        val blockedCount = dependencies.values.count { it.isNotEmpty() }
        val initialMoves = PuzzleSolver.availableMoves(puzzle).size
        val depth = longestDependencyDepth(dependencies)
        val bendCounts = arrows.map { arrow ->
            val points = arrow.path
            if (points.size < 3) 0 else (1 until points.lastIndex).count { index ->
                val before = points[index - 1]
                val current = points[index]
                val after = points[index + 1]
                (before.first == current.first) != (current.first == after.first)
            }
        }
        val arrowScore = (arrows.size / (puzzle.rows * puzzle.cols * 0.22f)).coerceIn(0f, 1f)
        val depthScore = (depth / maxOf(1f, arrows.size * 0.55f)).coerceIn(0f, 1f)
        val blockedRatio = blockedCount.toFloat() / arrows.size
        val lowMobilityScore = 1f - (initialMoves.toFloat() / arrows.size).coerceIn(0f, 1f)
        val averageBends = bendCounts.average().toFloat()
        val bendScore = (averageBends / 3.5f).coerceIn(0f, 1f)

        val score = (
                arrowScore * 20f +
                        depthScore * 25f +
                        blockedRatio * 20f +
                        lowMobilityScore * 20f +
                        bendScore * 15f
                ).toInt().coerceIn(0, 100)

        return Metrics(
            score = score,
            arrowCount = arrows.size,
            dependencyDepth = depth,
            blockedArrowRatio = blockedRatio,
            initialMoveRatio = initialMoves.toFloat() / arrows.size,
            averageBends = averageBends,
            label = labelForScore(score)
        )
    }

    fun labelForScore(score: Int): String = when (score.coerceIn(0, 100)) {
        in 0..19 -> "Beginner"
        in 20..39 -> "Easy"
        in 40..59 -> "Medium"
        in 60..79 -> "Hard"
        else -> "Expert"
    }

    fun targetLabelForLevel(level: Int): String = when (level.coerceAtLeast(1)) {
        in 1..10 -> "Beginner"
        in 11..25 -> "Easy"
        in 26..50 -> "Medium"
        in 51..100 -> "Hard"
        else -> "Expert"
    }

    private fun longestDependencyDepth(graph: Map<Int, List<Int>>): Int {
        val memo = HashMap<Int, Int>()
        val visiting = HashSet<Int>()

        fun depth(node: Int): Int {
            memo[node]?.let { return it }
            if (!visiting.add(node)) return 0
            val value = 1 + (graph[node].orEmpty().maxOfOrNull { depth(it) } ?: 0)
            visiting.remove(node)
            memo[node] = value
            return value
        }

        return graph.keys.maxOfOrNull(::depth)?.minus(1)?.coerceAtLeast(0) ?: 0
    }
}
