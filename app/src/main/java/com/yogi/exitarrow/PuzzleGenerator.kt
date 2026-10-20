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
        return when {
            safeLevel <= 5 -> "Beginner"
            safeLevel <= 15 -> "Easy"
            safeLevel <= 30 -> "Medium"
            safeLevel <= 60 -> "Hard"
            safeLevel <= 100 -> "Super Hard"
            safeLevel <= 150 -> "Expert"
            else -> "Master 300+"
        }
    }

    /** Returns recognizable shape/letter/bud pattern names for each level tier. */
    fun shapeThemeForLevel(level: Int): String {
        val safeLevel = maxOf(1, level)
        return when ((safeLevel - 1) % 12) {
            0 -> "Heart ♥"
            1 -> "Flower Bud ✿"
            2 -> "Letter 'E'"
            3 -> "Letter 'X'"
            4 -> "Letter 'I'"
            5 -> "Letter 'T'"
            6 -> "Star ★"
            7 -> "Letter 'A'"
            8 -> "Letter 'R'"
            9 -> "Letter 'O'"
            10 -> "Letter 'W'"
            11 -> "Diamond ◆"
            else -> "Heart ♥"
        }
    }

    /** Strict monotonic arrow count calculation: Level 1 = 10, Level 2 = 22 (+12), Level 3 = 34 (+12)... */
    fun targetArrowCountForLevel(level: Int): Int {
        val safeLevel = maxOf(1, level)
        return 10 + (safeLevel - 1) * 12
    }

    /** Calculates required grid size to ensure full capacity for the target arrow count. */
    fun calculateRequiredGridSize(targetArrows: Int): Int {
        val requiredCells = (targetArrows * 1.15f).toInt()
        val calculatedSize = kotlin.math.ceil(kotlin.math.sqrt(requiredCells.toDouble())).toInt()
        return maxOf(6, calculatedSize)
    }

    fun generate(level: Int, maxGridCap: Int = 16): Puzzle {
        val safeLevel = max(1, level)
        val targetArrowCount = targetArrowCountForLevel(safeLevel)
        val neededGridSize = calculateRequiredGridSize(targetArrowCount)
        val size = maxOf(neededGridSize, maxGridCap.coerceAtLeast(6))

        val random = Random(safeLevel.toLong() * 7919L + 37L)
        val difficulty = difficultyForLevel(safeLevel)
        val shapeTheme = shapeThemeForLevel(safeLevel)

        var arrows: MutableList<Arrow>? = null
        val maxAttempts = 150

        for (_attempt in 0 until maxAttempts) {
            val generatedArrows = mutableListOf<Arrow>()
            val occupied = Array(size) { BooleanArray(size) }

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
                            val (behindR, behindC) = when (dir) {
                                Direction.LEFT -> Pair(r, c + 1)
                                Direction.RIGHT -> Pair(r, c - 1)
                                Direction.UP -> Pair(r + 1, c)
                                Direction.DOWN -> Pair(r - 1, c)
                            }

                            val hasBehind = behindR in 0 until size && behindC in 0 until size && !occupied[behindR][behindC]

                            // Allow 1-cell or multi-cell arrows on higher levels (level >= 15) for max arrow density
                            if (!hasBehind && safeLevel < 15) {
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

                val hasBehindCell = behindR in 0 until size && behindC in 0 until size && !occupied[behindR][behindC]

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

                if (hasBehindCell && (safeLevel < 15 || random.nextBoolean())) {
                    // Step 1: Force body cell behind head so line segment entering head matches direction
                    snakingPath.add(0, Pair(behindR, behindC))
                    tempOccupied[behindR][behindC] = true

                    val targetLength = if (safeLevel >= 30) random.nextInt(2) + 2 else random.nextInt(3) + 2
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
                    title = "Level $safeLevel · $shapeTheme",
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

        // Fallback simple arrows filling remaining capacity if complex generation exceeded attempts
        if (arrows == null) {
            val fallbackArrows = mutableListOf<Arrow>()
            val occupied = Array(size) { BooleanArray(size) }
            var id = 1
            for (r in 0 until size) {
                for (c in 0 until size) {
                    if (occupied[r][c]) continue
                    var placed = false
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
                            placed = true
                            break
                        }
                    }
                    if (!placed && !occupied[r][c]) {
                        val dir = ALL_DIRECTIONS[id % ALL_DIRECTIONS.size]
                        occupied[r][c] = true
                        fallbackArrows.add(
                            Arrow(
                                id = id++,
                                path = listOf(Pair(r, c)),
                                direction = dir,
                                color = GameTheme.ARROW_COLORS[id % GameTheme.ARROW_COLORS.size]
                            )
                        )
                    }
                }
            }
            arrows = fallbackArrows
        }

        val puzzle = Puzzle(
            title = "Level $safeLevel · $shapeTheme",
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
