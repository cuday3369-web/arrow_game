
package com.yogi.exitarrow.generation

import com.yogi.exitarrow.Puzzle
import com.yogi.exitarrow.shape.ShapeFormation
import com.yogi.exitarrow.shape.ShapeLibrary
import com.yogi.exitarrow.shape.ShapeMaskRasterizer
import kotlin.random.Random

/**
 * Main generator for object-shaped Arrow Exit puzzles.
 *
 * Supports:
 * Animals, objects, alphabets, numbers and geometric shapes.
 */
object ObjectShapePuzzleGenerator {

    fun generate(
        level: Int,
        maxGridCap: Int = 16,
        formation: ShapeFormation = ShapeFormation.HYBRID
    ): Puzzle? {

        val safeLevel = level.coerceAtLeast(1)

        val config = com.yogi.exitarrow.PuzzleGenerator
            .configForLevel(safeLevel, maxGridCap)

        val template = ShapeLibrary.templateForLevel(safeLevel)

        val rows = config.gridSize
        val cols = config.gridSize

        val mask = ShapeMaskRasterizer.rasterize(
            template = template,
            rows = rows,
            cols = cols,
            formation = formation
        )

        if (mask.area < 8) return null

        val seed = safeLevel.toLong() * 104729L +
                template.id.hashCode().toLong() * 31L + 17L

        val requested = minOf(
            config.targetArrows,
            (mask.area / 3).coerceAtLeast(4)
        )

        val dependencyBias =
            (0.18f + safeLevel / 140f).coerceIn(0.18f, 0.92f)

        val maxPathCells = when {
            safeLevel < 10 -> 4
            safeLevel < 30 -> 5
            else -> 6
        }

        var bestPuzzle: Puzzle? = null
        var bestCount = 0

        repeat(3) { attempt ->

            val attemptMask = if (attempt == 0) {
                mask
            } else {
                ShapeMaskRasterizer.rasterize(
                    template = template,
                    rows = rows,
                    cols = cols,
                    formation = if (attempt % 2 == 0) {
                        ShapeFormation.FILLED
                    } else {
                        ShapeFormation.HYBRID
                    }
                )
            }

            val build = ReversePuzzleBuilder.build(
                mask = attemptMask,
                requestedArrowCount = requested,
                random = Random(seed + attempt * 7919L),
                maxPathCells = maxPathCells,
                dependencyBias = dependencyBias
            )

            if (build.arrows.size > bestCount) {

                val candidate = Puzzle(
                    title = "Level $safeLevel · ${template.displayName}",
                    rows = rows,
                    cols = cols,
                    arrows = build.arrows,
                    level = safeLevel,
                    difficulty =
                        AdaptiveDifficulty.targetLabelForLevel(safeLevel),
                    seed = seed,
                    shapeId = template.id,
                    shapeCategory = template.category.name,
                    shapeFormation = attemptMask.formation.name,
                    shapeCells = attemptMask.allCells().toSet()
                )

                val validation =
                    GeneratedLevelValidator.validate(candidate, attemptMask)

                if (validation.valid) {
                    bestPuzzle = candidate
                    bestCount = build.arrows.size
                }
            }

            if (bestCount >= requested) {
                val completed = bestPuzzle ?: return null

                return completed.copy(
                    difficulty =
                        AdaptiveDifficulty.analyze(completed).label
                )
            }
        }

        return bestPuzzle?.let { puzzle ->
            puzzle.copy(
                difficulty =
                    AdaptiveDifficulty.analyze(puzzle).label
            )
        }
    }
}
