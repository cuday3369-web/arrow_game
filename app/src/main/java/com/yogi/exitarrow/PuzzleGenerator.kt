package com.yogi.exitarrow

import kotlin.math.max
import java.util.Random

object PuzzleGenerator {

    private val ALL_DIRECTIONS = arrayOf(
        Direction.LEFT,
        Direction.RIGHT,
        Direction.UP,
        Direction.DOWN
    )

    fun difficultyForLevel(level: Int): String {
        val safeLevel = maxOf(1, level)
        return when (safeLevel % 5) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            4 -> "Super Hard"
            0 -> "Unbeatable"
            else -> "Easy"
        }
    }

    fun generate(level: Int): Puzzle {
        val safeLevel = max(1, level)
        // Grid size increases with level: Level 1 = 6x6, Level 2 = 7x7, Level 3 = 8x8, up to 12x12
        val size = (6 + (safeLevel - 1) / 2).coerceIn(6, 12)
        val random = Random(safeLevel.toLong() * 7919L + 37L)

        val rem = if (safeLevel % 5 == 0) 5 else (safeLevel % 5)
        val difficulty = difficultyForLevel(safeLevel)

        var arrows: MutableList<Arrow>? = null
        val maxAttempts = 100

        for (_attempt in 0 until maxAttempts) {
            val generatedArrows = mutableListOf<Arrow>()
            val occupied = Array(size) { BooleanArray(size) }
            val targetArrowCount = 2 * size + size * rem

            var arrowId = 1
            var failCounter = 0

            while (generatedArrows.size < targetArrowCount && failCounter < 100) {
                failCounter++

                // Find candidate head positions (hr, hc) and exit directions
                val candidateHeads = mutableListOf<Triple<Int, Int, Direction>>()

                for (r in 0 until size) {
                    for (c in 0 until size) {
                        if (occupied[r][c]) continue

                        for (dir in ALL_DIRECTIONS) {
                            // Check that the cell directly behind (r, c) for direction 'dir' is free
                            val (behindR, behindC) = when (dir) {
                                Direction.LEFT -> Pair(r, c + 1)
                                Direction.RIGHT -> Pair(r, c - 1)
                                Direction.UP -> Pair(r + 1, c)
                                Direction.DOWN -> Pair(r - 1, c)
                            }

                            if (behindR !in 0 until size || behindC !in 0 until size || occupied[behindR][behindC]) {
                                continue
                            }

                            // Check if exit ray in 'dir' to board edge is clear of already occupied cells
                            var rayR = r
                            var rayC = c
                            var clearRay = true

                            while (true) {
                                when (dir) {
                                    Direction.LEFT -> rayC--
                                    Direction.RIGHT -> rayC++
                                    Direction.UP -> rayR--
                                    Direction.DOWN -> rayR++
                                }
                                if (rayR !in 0 until size || rayC !in 0 until size) break
                                if (occupied[rayR][rayC]) {
                                    clearRay = false
                                    break
                                }
                            }

                            if (clearRay) {
                                candidateHeads.add(Triple(r, c, dir))
                            }
                        }
                    }
                }

                if (candidateHeads.isEmpty()) break

                val chosenHead = candidateHeads[random.nextInt(candidateHeads.size)]
                val hr = chosenHead.first
                val hc = chosenHead.second
                val dir = chosenHead.third

                val (behindR, behindC) = when (dir) {
                    Direction.LEFT -> Pair(hr, hc + 1)
                    Direction.RIGHT -> Pair(hr, hc - 1)
                    Direction.UP -> Pair(hr + 1, hc)
                    Direction.DOWN -> Pair(hr - 1, hc)
                }

                // Grow snaking body backwards from head
                val snakingPath = mutableListOf<Pair<Int, Int>>()
                snakingPath.add(Pair(hr, hc))

                val tempOccupied = Array(size) { occupied[it].clone() }
                tempOccupied[hr][hc] = true

                // Also mark exit ray in front of head as forbidden so tail doesn't wrap in front of head
                var forbidR = hr
                var forbidC = hc
                while (true) {
                    when (dir) {
                        Direction.LEFT -> forbidC--
                        Direction.RIGHT -> forbidC++
                        Direction.UP -> forbidR--
                        Direction.DOWN -> forbidR++
                    }
                    if (forbidR !in 0 until size || forbidC !in 0 until size) break
                    tempOccupied[forbidR][forbidC] = true
                }

                // Step 1: Force body cell behind head so line segment entering head matches direction
                snakingPath.add(0, Pair(behindR, behindC))
                tempOccupied[behindR][behindC] = true

                val targetLength = random.nextInt(4) + 3 // Length 3 to 6 cells (L, U, Z, S shapes)
                var currR = behindR
                var currC = behindC

                for (step in 2 until targetLength) {
                    val neighbors = mutableListOf<Pair<Int, Int>>()
                    val dr = intArrayOf(-1, 1, 0, 0)
                    val dc = intArrayOf(0, 0, -1, 1)

                    for (i in 0..3) {
                        val nr = currR + dr[i]
                        val nc = currC + dc[i]
                        if (nr in 0 until size && nc in 0 until size && !tempOccupied[nr][nc]) {
                            neighbors.add(Pair(nr, nc))
                        }
                    }

                    if (neighbors.isEmpty()) break

                    val nextCell = neighbors[random.nextInt(neighbors.size)]
                    currR = nextCell.first
                    currC = nextCell.second
                    snakingPath.add(0, Pair(currR, currC)) // Append to tail (front of list)
                    tempOccupied[currR][currC] = true
                }

                if (snakingPath.size >= 2) {
                    snakingPath.forEach { (pr, pc) -> occupied[pr][pc] = true }

                    val color = GameTheme.ARROW_COLORS[
                        generatedArrows.size % GameTheme.ARROW_COLORS.size
                    ]

                    generatedArrows.add(
                        Arrow(
                            id = arrowId++,
                            path = snakingPath,
                            direction = dir,
                            color = color
                        )
                    )
                }
            }

            if (generatedArrows.size >= 4) {
                // Reverse generated list so the last placed arrow (which has a clear exit) is played first
                generatedArrows.reverse()

                val testPuzzle = Puzzle(
                    title = "Level $safeLevel",
                    rows = size,
                    cols = size,
                    arrows = generatedArrows,
                    level = safeLevel,
                    difficulty = difficulty,
                    seed = safeLevel.toLong()
                )

                if (PuzzleSolver.solveOrder(testPuzzle) != null) {
                    arrows = generatedArrows
                    break
                }
            }
        }

        // Fallback simple 2-cell arrows if complex generation exceeded attempts
        if (arrows == null) {
            val fallbackArrows = mutableListOf<Arrow>()
            val occupied = Array(size) { BooleanArray(size) }
            var id = 1
            for (r in 0 until size) {
                for (c in 0 until size) {
                    if (occupied[r][c]) continue
                    for (dir in ALL_DIRECTIONS) {
                        val (br, bc) = when (dir) {
                            Direction.LEFT -> Pair(r, c + 1)
                            Direction.RIGHT -> Pair(r, c - 1)
                            Direction.UP -> Pair(r + 1, c)
                            Direction.DOWN -> Pair(r - 1, c)
                        }
                        if (br in 0 until size && bc in 0 until size && !occupied[br][bc]) {
                            val path = listOf(Pair(br, bc), Pair(r, c))
                            path.forEach { (pr, pc) -> occupied[pr][pc] = true }
                            fallbackArrows.add(
                                Arrow(
                                    id = id++,
                                    path = path,
                                    direction = dir,
                                    color = GameTheme.ARROW_COLORS[id % GameTheme.ARROW_COLORS.size]
                                )
                            )
                            break
                        }
                    }
                }
            }
            arrows = fallbackArrows
        }

        val puzzle = Puzzle(
            title = "Level $safeLevel",
            rows = size,
            cols = size,
            arrows = arrows,
            level = safeLevel,
            difficulty = difficulty,
            seed = safeLevel.toLong()
        )

        return puzzle
    }
}
