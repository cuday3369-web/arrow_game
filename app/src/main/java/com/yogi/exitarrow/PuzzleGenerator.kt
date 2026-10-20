package com.yogi.exitarrow

import com.yogi.exitarrow.game.LevelConfig
import kotlin.math.max
import java.util.Random

object PuzzleGenerator {

    private val ALL_DIRECTIONS = arrayOf(
        Direction.LEFT,
        Direction.RIGHT,
        Direction.UP,
        Direction.DOWN
    )

    // 10 Distinct 2D Shape Stencil Masks ('X' = playable shape cell, '.' = empty exit space)
    private val MASK_HEART = arrayOf(
        "..XXXX...XXXX..",
        ".XXXXXX.XXXXXX.",
        "XXXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXXX",
        ".XXXXXXXXXXXXX.",
        "..XXXXXXXXXXX..",
        "...XXXXXXXXX...",
        "....XXXXXXX....",
        ".....XXXXX.....",
        "......XXX......",
        ".......X......."
    )

    private val MASK_S_CURVE = arrayOf(
        ".XXXXXXXXXXXX.",
        "XXXXXXXXXXXXXX",
        "XXX...........",
        "XXXXXXXXXXXX..",
        ".XXXXXXXXXXXXX",
        "...........XXX",
        "XXXXXXXXXXXXXX",
        ".XXXXXXXXXXXX."
    )

    private val MASK_STAR = arrayOf(
        ".......XX.......",
        "......XXXX......",
        ".....XXXXXX.....",
        "XXXXXXXXXXXXXXXX",
        ".XXXXXXXXXXXXXX.",
        "..XXXXXXXXXXXX..",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "..XXXX....XXXX..",
        ".XXXX......XXXX.",
        "XXXX........XXXX"
    )

    private val MASK_DIAMOND = arrayOf(
        "......XX......",
        "....XXXXXX....",
        "...XXXXXXXX...",
        "..XXXXXXXXXX..",
        ".XXXXXXXXXXXX.",
        "XXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXX",
        ".XXXXXXXXXXXX.",
        "..XXXXXXXXXX..",
        "...XXXXXXXX...",
        "....XXXXXX....",
        "......XX......"
    )

    private val MASK_CIRCLE = arrayOf(
        "....XXXXXX....",
        "..XXXXXXXXXX..",
        ".XXXXXXXXXXXX.",
        ".XXXXXXXXXXXX.",
        "XXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXX",
        ".XXXXXXXXXXXX.",
        ".XXXXXXXXXXXX.",
        "..XXXXXXXXXX..",
        "....XXXXXX...."
    )

    private val MASK_SPIRAL = arrayOf(
        "XXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXX",
        "XXX........XXX",
        "XXX..XXXXX.XXX",
        "XXX..XXXXX.XXX",
        "XXX....XXX.XXX",
        "XXXXXX.XXX.XXX",
        "XXXXXX.....XXX",
        "XXXXXXXXXXXXXX"
    )

    private val MASK_CROSS = arrayOf(
        "....XXXXXX....",
        "....XXXXXX....",
        "....XXXXXX....",
        "XXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXX",
        "....XXXXXX....",
        "....XXXXXX....",
        "....XXXXXX...."
    )

    private val MASK_TRIANGLE = arrayOf(
        ".......XX.......",
        "......XXXX......",
        ".....XXXXXX.....",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "..XXXXXXXXXXXX..",
        ".XXXXXXXXXXXXXX.",
        "XXXXXXXXXXXXXXXX"
    )

    private val MASK_ARROWHEAD = arrayOf(
        "....XX........",
        "....XXXX......",
        "....XXXXXX....",
        "XXXXXXXXXXXX..",
        "XXXXXXXXXXXXXX",
        "XXXXXXXXXXXX..",
        "....XXXXXX....",
        "....XXXX......",
        "....XX........"
    )

    private val MASK_CORRIDORS = arrayOf(
        "XXXXXXXXXXXXXX",
        "XXX..XXX..XXXX",
        "XXX..XXX..XXXX",
        "XXXXXXXXXXXXXX",
        "XXXX..XXX..XXX",
        "XXXX..XXX..XXX",
        "XXXXXXXXXXXXXX"
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

    /** Maps the level number to one of 10 shape theme descriptions. */
    fun shapeThemeForLevel(level: Int): String {
        val safeLevel = maxOf(1, level)
        return when ((safeLevel - 1) % 10) {
            0 -> "Heart Shaped Level ♥"
            1 -> "S-Curve Challenge 🐍"
            2 -> "Star Shaped Level ★"
            3 -> "Diamond Challenge ◆"
            4 -> "Circle Shaped Level ●"
            5 -> "Spiral Challenge 🌀"
            6 -> "Cross Shaped Level ✚"
            7 -> "Triangle Shaped Level ▲"
            8 -> "Arrowhead Challenge ➢"
            else -> "Winding Corridors 🔀"
        }
    }

    /** Returns the corresponding 2D grid stencil mask for the level. */
    private fun getMaskForLevel(level: Int): Array<String> {
        val safeLevel = maxOf(1, level)
        return when ((safeLevel - 1) % 10) {
            0 -> MASK_HEART
            1 -> MASK_S_CURVE
            2 -> MASK_STAR
            3 -> MASK_DIAMOND
            4 -> MASK_CIRCLE
            5 -> MASK_SPIRAL
            6 -> MASK_CROSS
            7 -> MASK_TRIANGLE
            8 -> MASK_ARROWHEAD
            else -> MASK_CORRIDORS
        }
    }

    /** Checks whether a cell is part of the stencil shape mask for the given grid dimensions. */
    private fun isCellPlayable(r: Int, c: Int, rows: Int, cols: Int, level: Int): Boolean {
        val mask = getMaskForLevel(level)
        val maskRows = mask.size
        val maskCols = mask[0].length

        val mappedR = (r * maskRows / rows).coerceIn(0, maskRows - 1)
        val mappedC = (c * maskCols / cols).coerceIn(0, maskCols - 1)

        return mask[mappedR][mappedC] == 'X'
    }

    /** Target arrow count calculation: Level 1 = ~36 (Trial Heart), Level 2 = 48, Level 3 = 62... */
    fun targetArrowCountForLevel(level: Int): Int {
        val info = LevelConfig.getLevelInfo(level)
        return info.arrowCount
    }

    /** Calculates required grid size to ensure full capacity for the target arrow count. */
    fun calculateRequiredGridSize(targetArrows: Int): Int {
        val requiredCells = (targetArrows * 1.15f).toInt()
        val calculatedSize = kotlin.math.ceil(kotlin.math.sqrt(requiredCells.toDouble())).toInt()
        return maxOf(8, calculatedSize)
    }

    fun generate(level: Int, maxGridCap: Int = 36): Puzzle {
        val safeLevel = max(1, level)
        val info = LevelConfig.getLevelInfo(safeLevel)
        val targetArrowCount = info.arrowCount
        val size = info.gridSize

        val rows = size
        val cols = size

        val random = Random(safeLevel.toLong() * 7919L + 37L)
        val difficulty = difficultyForLevel(safeLevel)
        val shapeTheme = shapeThemeForLevel(safeLevel)

        var arrows: MutableList<Arrow>? = null
        val maxAttempts = 150

        for (_attempt in 0 until maxAttempts) {
            val generatedArrows = mutableListOf<Arrow>()
            val occupied = Array(rows) { BooleanArray(cols) }

            var arrowId = 1
            var failCounter = 0

            while (generatedArrows.size < targetArrowCount && failCounter < 100) {
                failCounter++

                val candidateHeads = mutableListOf<Triple<Int, Int, Direction>>()

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if (!isCellPlayable(r, c, rows, cols, safeLevel) || occupied[r][c]) continue

                        for (dir in ALL_DIRECTIONS) {
                            val (behindR, behindC) = when (dir) {
                                Direction.LEFT -> Pair(r, c + 1)
                                Direction.RIGHT -> Pair(r, c - 1)
                                Direction.UP -> Pair(r + 1, c)
                                Direction.DOWN -> Pair(r - 1, c)
                            }

                            val hasBehind = behindR in 0 until rows && behindC in 0 until cols &&
                                    isCellPlayable(behindR, behindC, rows, cols, safeLevel) && !occupied[behindR][behindC]

                            if (!hasBehind && safeLevel < 10) {
                                continue
                            }

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
                                if (rayR !in 0 until rows || rayC !in 0 until cols) break
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

                val hasBehindCell = behindR in 0 until rows && behindC in 0 until cols &&
                        isCellPlayable(behindR, behindC, rows, cols, safeLevel) && !occupied[behindR][behindC]

                val snakingPath = mutableListOf<Pair<Int, Int>>()
                snakingPath.add(Pair(hr, hc))

                val tempOccupied = Array(rows) { occupied[it].clone() }
                tempOccupied[hr][hc] = true

                var forbidR = hr
                var forbidC = hc
                while (true) {
                    when (dir) {
                        Direction.LEFT -> forbidC--
                        Direction.RIGHT -> forbidC++
                        Direction.UP -> forbidR--
                        Direction.DOWN -> forbidR++
                    }
                    if (forbidR !in 0 until rows || forbidC !in 0 until cols) break
                    tempOccupied[forbidR][forbidC] = true
                }

                if (hasBehindCell && (safeLevel < 10 || random.nextBoolean())) {
                    snakingPath.add(0, Pair(behindR, behindC))
                    tempOccupied[behindR][behindC] = true

                    val targetLength = if (safeLevel >= 25) random.nextInt(2) + 2 else random.nextInt(3) + 2
                    var currR = behindR
                    var currC = behindC

                    for (step in 2 until targetLength) {
                        val neighbors = mutableListOf<Pair<Int, Int>>()
                        val dr = intArrayOf(-1, 1, 0, 0)
                        val dc = intArrayOf(0, 0, -1, 1)

                        for (i in 0..3) {
                            val nr = currR + dr[i]
                            val nc = currC + dc[i]
                            if (nr in 0 until rows && nc in 0 until cols &&
                                isCellPlayable(nr, nc, rows, cols, safeLevel) && !tempOccupied[nr][nc]) {
                                neighbors.add(Pair(nr, nc))
                            }
                        }

                        if (neighbors.isEmpty()) break

                        val nextCell = neighbors[random.nextInt(neighbors.size)]
                        currR = nextCell.first
                        currC = nextCell.second
                        snakingPath.add(0, Pair(currR, currC))
                        tempOccupied[currR][currC] = true
                    }
                }

                if (snakingPath.size == 1) {
                    val (hr, hc) = snakingPath[0]
                    val br = when (dir) {
                        Direction.UP -> (hr + 1).coerceAtMost(rows - 1)
                        Direction.DOWN -> (hr - 1).coerceAtLeast(0)
                        else -> hr
                    }
                    val bc = when (dir) {
                        Direction.LEFT -> (hc + 1).coerceAtMost(cols - 1)
                        Direction.RIGHT -> (hc - 1).coerceAtLeast(0)
                        else -> hc
                    }
                    if (br != hr || bc != hc) {
                        snakingPath.add(0, Pair(br, bc))
                    }
                }

                if (snakingPath.size >= 1) {
                    snakingPath.forEach { (pr, pc) -> occupied[pr][pc] = true }

                    val color = GameTheme.ARROW_COLORS[generatedArrows.size % GameTheme.ARROW_COLORS.size]
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
                generatedArrows.reverse()

                val testPuzzle = Puzzle(
                    title = "Level $safeLevel · $shapeTheme",
                    rows = rows,
                    cols = cols,
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

        if (arrows == null) {
            val fallbackArrows = mutableListOf<Arrow>()
            val occupied = Array(rows) { BooleanArray(cols) }
            var id = 1
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (occupied[r][c] || !isCellPlayable(r, c, rows, cols, safeLevel)) continue
                    var placed = false
                    for (dir in ALL_DIRECTIONS) {
                        val (br, bc) = when (dir) {
                            Direction.LEFT -> Pair(r, c + 1)
                            Direction.RIGHT -> Pair(r, c - 1)
                            Direction.UP -> Pair(r + 1, c)
                            Direction.DOWN -> Pair(r - 1, c)
                        }
                        if (br in 0 until rows && bc in 0 until cols && isCellPlayable(br, bc, rows, cols, safeLevel) && !occupied[br][bc]) {
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
                        val br = when (dir) {
                            Direction.UP -> (r + 1).coerceAtMost(rows - 1)
                            Direction.DOWN -> (r - 1).coerceAtLeast(0)
                            else -> r
                        }
                        val bc = when (dir) {
                            Direction.LEFT -> (c + 1).coerceAtMost(cols - 1)
                            Direction.RIGHT -> (c - 1).coerceAtLeast(0)
                            else -> c
                        }
                        occupied[r][c] = true
                        if (br != r || bc != c) occupied[br][bc] = true
                        fallbackArrows.add(
                            Arrow(
                                id = id++,
                                path = if (br != r || bc != c) listOf(Pair(br, bc), Pair(r, c)) else listOf(Pair(r, c)),
                                direction = dir,
                                color = GameTheme.ARROW_COLORS[id % GameTheme.ARROW_COLORS.size]
                            )
                        )
                    }
                }
            }
            arrows = fallbackArrows
        }

        return Puzzle(
            title = "Level $safeLevel · $shapeTheme",
            rows = rows,
            cols = cols,
            arrows = arrows,
            level = safeLevel,
            difficulty = difficulty,
            seed = safeLevel.toLong()
        )
    }
}
