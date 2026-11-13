package com.yogi.exitarrow.generation

import com.yogi.exitarrow.Arrow
import com.yogi.exitarrow.shape.ShapeMask
import kotlin.random.Random

/**
 * Builds a puzzle in reverse removal order. Every newly inserted arrow has a clear
 * ray at insertion time; arrows inserted later may block earlier arrows, creating
 * the dependency chain that is removed in the opposite order.
 */
object ReversePuzzleBuilder {
    data class BuildResult(
        val arrows: MutableList<Arrow>,
        val requestedArrowCount: Int,
        val achievedArrowCount: Int,
        val insertionOrder: List<Int>
    )

    fun build(
        mask: ShapeMask,
        requestedArrowCount: Int,
        random: Random,
        maxPathCells: Int = 6,
        dependencyBias: Float = 0.65f,
        colors: IntArray = intArrayOf(0xFF42E8FF.toInt(), 0xFF9B6EFF.toInt(), 0xFFFFCF5B.toInt(), 0xFF5FF0B1.toInt(), 0xFFFF73A0.toInt())
    ): BuildResult {
        require(colors.isNotEmpty()) { "At least one arrow color is required." }
        val occupied = Array(mask.rows) { BooleanArray(mask.cols) }
        // Cell -> IDs of existing arrows whose exit rays pass through that cell.
        // This makes dependency scoring proportional to candidate path length.
        val rayOwners = Array(mask.rows) { Array(mask.cols) { linkedSetOf<Int>() } }
        val inserted = mutableListOf<Arrow>()
        val maxByArea = (mask.area / 2).coerceAtLeast(1)
        val target = requestedArrowCount.coerceIn(1, maxByArea)
        var attempts = 0
        val attemptLimit = (target * 24).coerceAtLeast(80)

        while (inserted.size < target && attempts < attemptLimit) {
            attempts++
            val allCandidates = ShapeArrowPathGenerator.findAvailableHeads(mask, occupied)
            val contourCandidates = allCandidates.filter { mask.isContour(it.row, it.col) }
            val candidatePool = if (
                mask.formation == com.yogi.exitarrow.shape.ShapeFormation.HYBRID &&
                contourCandidates.isNotEmpty() && random.nextFloat() < 0.62f
            ) contourCandidates else allCandidates
            val candidates = candidatePool.shuffled(random)
            if (candidates.isEmpty()) break

            // Sample several valid paths and prefer ones that intersect existing
            // arrows' exit rays. This creates deliberate, solvable dependencies.
            val scoredPaths = mutableListOf<Pair<List<Pair<Int, Int>>, Int>>()
            for (candidate in candidates.take(24)) {
                repeat(2) {
                    val path = ShapeArrowPathGenerator.createPath(
                        mask = mask,
                        occupied = occupied,
                        candidate = candidate,
                        random = random,
                        maxPathCells = maxPathCells
                    ) ?: return@repeat
                    if (path.any { (r, c) -> occupied[r][c] }) return@repeat
                    scoredPaths += path to blockingScore(path, rayOwners)
                }
            }

            if (scoredPaths.isEmpty()) break
            val ordered = scoredPaths.sortedByDescending { it.second }
            val diversityWindow = (ordered.size * (1f - dependencyBias.coerceIn(0f, 1f)) * 0.65f)
                .toInt().coerceAtLeast(1)
            val bestScore = ordered.first().second
            val topCandidates = ordered.takeWhile { it.second == bestScore }.take(diversityWindow.coerceAtLeast(1))
            val placed = if (bestScore == 0) {
                ordered[random.nextInt(ordered.size)].first
            } else {
                topCandidates[random.nextInt(topCandidates.size)].first
            }

            val arrow = Arrow(
                id = inserted.size + 1,
                path = placed,
                // Arrow derives its real direction from the last segment of the path.
                direction = com.yogi.exitarrow.Direction.RIGHT,
                color = colors[inserted.size % colors.size]
            )

            placed.forEach { (r, c) -> occupied[r][c] = true }
            addRayOwners(arrow, mask.rows, mask.cols, rayOwners)
            inserted += arrow
        }

        return BuildResult(
            arrows = inserted,
            requestedArrowCount = requestedArrowCount,
            achievedArrowCount = inserted.size,
            insertionOrder = inserted.map { it.id }
        )
    }
    private fun blockingScore(
        candidatePath: List<Pair<Int, Int>>,
        rayOwners: Array<Array<LinkedHashSet<Int>>>
    ): Int {
        val blockedArrowIds = linkedSetOf<Int>()
        candidatePath.forEach { (row, col) ->
            blockedArrowIds.addAll(rayOwners[row][col])
        }
        return blockedArrowIds.size
    }

    private fun addRayOwners(
        arrow: Arrow,
        rows: Int,
        cols: Int,
        rayOwners: Array<Array<LinkedHashSet<Int>>>
    ) {
        val (dr, dc) = ShapeArrowPathGenerator.delta(arrow.direction)
        var row = arrow.headRow + dr
        var col = arrow.headCol + dc
        while (row in 0 until rows && col in 0 until cols) {
            rayOwners[row][col].add(arrow.id)
            row += dr
            col += dc
        }
    }

}
