package com.yogi.exitarrow

import com.yogi.exitarrow.shape.ShapeFormation
import com.yogi.exitarrow.shape.ShapeLibrary
import com.yogi.exitarrow.shape.ShapeMaskRasterizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapePuzzleGeneratorTest {

    @Test
    fun generatedLevelsUseBuiltInObjectTemplates() {
        for (level in 1..100) {
            val puzzle = PuzzleGenerator.generate(level, 20)
            assertNotEquals("Level $level should use a shape template", "classic", puzzle.shapeId)
            assertTrue("Level $level should contain arrows", puzzle.arrows.size >= 4)
            assertNotNull("Level $level should be solvable", PuzzleSolver.solveOrder(puzzle))
            assertTrue("Level $level should carry its rendered silhouette mask", puzzle.shapeCells.isNotEmpty())
        }
    }

    @Test
    fun generatedArrowPathsStayInsideTheirShapeMask() {
        for (level in 1..ShapeLibrary.all.size) {
            val puzzle = PuzzleGenerator.generate(level, 20)
            val template = ShapeLibrary.templateById(puzzle.shapeId)
            assertNotNull("Template ${puzzle.shapeId} should exist", template)

            val formation = runCatching {
                ShapeFormation.valueOf(puzzle.shapeFormation)
            }.getOrDefault(ShapeFormation.HYBRID)
            val mask = ShapeMaskRasterizer.rasterize(
                template = template!!,
                rows = puzzle.rows,
                cols = puzzle.cols,
                formation = formation
            )

            puzzle.arrows.forEach { arrow ->
                assertTrue("Arrow ${arrow.id} should have a path", arrow.path.size >= 2)
                arrow.path.forEach { (row, col) ->
                    assertTrue(
                        "Arrow ${arrow.id} leaves ${puzzle.shapeId} at $row,$col",
                        mask.contains(row, col)
                    )
                }
            }
        }
    }

    @Test
    fun everyGeneratedArrowHasAnOrthogonalContinuousPath() {
        for (level in 1..100) {
            val puzzle = PuzzleGenerator.generate(level, 20)
            puzzle.arrows.forEach { arrow ->
                arrow.path.zipWithNext().forEach { (a, b) ->
                    val distance = kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)
                    assertEquals("Arrow ${arrow.id} must use adjacent grid cells", 1, distance)
                }
            }
        }
    }

    @Test
    fun levelTemplatesCycleAcrossAnimalsObjectsLettersAndNumbers() {
        val categories = (1..ShapeLibrary.all.size)
            .map { ShapeLibrary.templateForLevel(it).category }
            .toSet()
        assertTrue(categories.containsAll(ShapeLibrary.all.map { it.category }.toSet()))
    }
}
