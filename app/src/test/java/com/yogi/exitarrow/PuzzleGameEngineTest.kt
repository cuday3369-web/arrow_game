package com.yogi.exitarrow

import org.junit.Assert.*
import org.junit.Test

class PuzzleGameEngineTest {

    @Test
    fun testUnobstructedArrowExit() {
        val arrow = Arrow(
            id = 1,
            path = listOf(Pair(2, 2), Pair(2, 1)),
            direction = Direction.LEFT,
            color = 0xFF0000
        )
        val puzzle = Puzzle("Test", 5, 5, mutableListOf(arrow))

        assertTrue("Arrow with clear path to board edge should be clear", PuzzleSolver.isClear(puzzle, arrow))
        assertEquals("Blockers list should be empty", 0, PuzzleSolver.blockers(puzzle, arrow).size)
    }

    @Test
    fun testArrowBlockedImmediately() {
        val arrow1 = Arrow(
            id = 1,
            path = listOf(Pair(2, 2), Pair(2, 1)),
            direction = Direction.LEFT,
            color = 0xFF0000
        )
        val blocker = Arrow(
            id = 2,
            path = listOf(Pair(2, 0)),
            direction = Direction.UP,
            color = 0x00FF00
        )
        val puzzle = Puzzle("Test", 5, 5, mutableListOf(arrow1, blocker))

        assertFalse("Arrow blocked by adjacent arrow should not be clear", PuzzleSolver.isClear(puzzle, arrow1))
        assertEquals("Blockers should contain blocking arrow", 1, PuzzleSolver.blockers(puzzle, arrow1).size)
        assertEquals("Blocker ID should match", 2, PuzzleSolver.blockers(puzzle, arrow1)[0].id)
    }

    @Test
    fun testArrowBlockedFartherAlongTrajectory() {
        val arrow1 = Arrow(
            id = 1,
            path = listOf(Pair(2, 3)),
            direction = Direction.LEFT,
            color = 0xFF0000
        )
        val blocker = Arrow(
            id = 2,
            path = listOf(Pair(2, 0)),
            direction = Direction.DOWN,
            color = 0x00FF00
        )
        val puzzle = Puzzle("Test", 5, 5, mutableListOf(arrow1, blocker))

        assertFalse("Arrow blocked farther along trajectory should not be clear", PuzzleSolver.isClear(puzzle, arrow1))
    }

    @Test
    fun testAllFourExitDirections() {
        val leftArrow = Arrow(1, listOf(Pair(2, 2)), Direction.LEFT, 0)
        val rightArrow = Arrow(2, listOf(Pair(0, 0)), Direction.RIGHT, 0)
        val upArrow = Arrow(3, listOf(Pair(4, 4)), Direction.UP, 0)
        val downArrow = Arrow(4, listOf(Pair(1, 3)), Direction.DOWN, 0)

        val puzzle = Puzzle("4-Way", 5, 5, mutableListOf(leftArrow, rightArrow, upArrow, downArrow))

        assertTrue("LEFT arrow should be clear", PuzzleSolver.isClear(puzzle, leftArrow))
        assertTrue("RIGHT arrow should be clear", PuzzleSolver.isClear(puzzle, rightArrow))
        assertTrue("UP arrow should be clear", PuzzleSolver.isClear(puzzle, upArrow))
        assertTrue("DOWN arrow should be clear", PuzzleSolver.isClear(puzzle, downArrow))
    }

    @Test
    fun testMultiBendSnakingArrowOccupancy() {
        val snakingPath = listOf(Pair(0, 0), Pair(1, 0), Pair(1, 1), Pair(2, 1))
        val arrow = Arrow(1, snakingPath, Direction.DOWN, 0)

        assertTrue("Arrow should occupy (0,0)", arrow.occupies(0, 0))
        assertTrue("Arrow should occupy (1,0)", arrow.occupies(1, 0))
        assertTrue("Arrow should occupy (1,1)", arrow.occupies(1, 1))
        assertTrue("Arrow should occupy (2,1)", arrow.occupies(2, 1))
        assertFalse("Arrow should not occupy (2,2)", arrow.occupies(2, 2))
    }

    @Test
    fun testSolverSolvabilityAndDependencyGraph() {
        val blocker = Arrow(1, listOf(Pair(0, 2)), Direction.UP, 0)
        val blocked = Arrow(2, listOf(Pair(2, 2)), Direction.UP, 0)

        val puzzle = Puzzle("Dependency", 5, 5, mutableListOf(blocker, blocked))

        val order = PuzzleSolver.solveOrder(puzzle)
        assertNotNull("Solvable sequence should exist", order)
        assertEquals("First arrow in sequence must be the blocker", 1, order!![0])
        assertEquals("Second arrow in sequence must be the blocked arrow", 2, order[1])
    }

    @Test
    fun testUnsolvableCycleRejection() {
        // Arrow 1 at (2,1) points RIGHT toward Arrow 2 at (2,3)
        val arrow1 = Arrow(1, listOf(Pair(2, 1)), Direction.RIGHT, 0)
        // Arrow 2 at (2,3) points LEFT toward Arrow 1 at (2,1)
        val arrow2 = Arrow(2, listOf(Pair(2, 3)), Direction.LEFT, 0)

        val puzzle = Puzzle("Cycle", 5, 5, mutableListOf(arrow1, arrow2))

        val order = PuzzleSolver.solveOrder(puzzle)
        assertNull("Cyclic deadlock puzzle must return null solver order", order)
    }

    @Test
    fun testVictoryCondition() {
        val arrow = Arrow(1, listOf(Pair(0, 0)), Direction.LEFT, 0)
        val puzzle = Puzzle("Victory", 5, 5, mutableListOf(arrow))

        assertFalse("Puzzle should not be solved initially", PuzzleSolver.isSolved(puzzle))
        arrow.active = false
        assertTrue("Puzzle should be solved when all arrows are inactive", PuzzleSolver.isSolved(puzzle))
    }

    @Test
    fun testGeneratorSolvabilityGuaranteeAcrossLevels() {
        for (lvl in 1..100) {
            val generatedPuzzle = PuzzleGenerator.generate(lvl)
            val solveSequence = PuzzleSolver.solveOrder(generatedPuzzle)

            assertNotNull("Generated level $lvl must be 100% solvable", solveSequence)
            assertTrue("Generated level $lvl must contain arrows", generatedPuzzle.arrows.isNotEmpty())
        }
    }

    @Test
    fun testNextHint() {
        val blocker = Arrow(1, listOf(Pair(0, 2)), Direction.UP, 0)
        val blocked = Arrow(2, listOf(Pair(2, 2)), Direction.UP, 0)
        val puzzle = Puzzle("Hint", 5, 5, mutableListOf(blocker, blocked))

        val hint = PuzzleSolver.nextHint(puzzle)
        assertNotNull("Hint should be provided for solvable puzzle", hint)
        assertEquals("Hint should point to first clear arrow", 1, hint!!.arrowId)
    }

    @Test
    fun testArrowHeadDirectionMatchesPathLastSegment() {
        for (lvl in 1..10) {
            val puzzle = PuzzleGenerator.generate(lvl)
            for (arrow in puzzle.arrows) {
                if (arrow.path.size >= 2) {
                    val p0 = arrow.path[arrow.path.size - 2]
                    val head = arrow.path.last()
                    val expectedDir = when {
                        head.first < p0.first -> Direction.UP
                        head.first > p0.first -> Direction.DOWN
                        head.second < p0.second -> Direction.LEFT
                        head.second > p0.second -> Direction.RIGHT
                        else -> arrow.direction
                    }
                    assertEquals(
                        "Arrow head direction in level $lvl must match path's last segment direction",
                        expectedDir,
                        arrow.direction
                    )
                }
            }
        }
    }
}
