package com.yogi.exitarrow.game

import androidx.compose.ui.graphics.Color
import com.yogi.exitarrow.Direction
import java.util.Random

data class LightningArrow(
    val id: Int,
    val path: List<Pair<Int, Int>>,
    val direction: Direction,
    val color: Color,
    var active: Boolean = true,
) {
    val headRow: Int get() = path.last().first
    val headCol: Int get() = path.last().second

    fun occupies(r: Int, c: Int): Boolean = path.any { (pr, pc) -> (pr == r) && (pc == c) }
}

data class LightningPuzzle(
    val level: Int,
    val rows: Int,
    val cols: Int,
    val arrows: MutableList<LightningArrow>,
) {
    fun activeArrows(): List<LightningArrow> = arrows.filter { arrow -> arrow.active }
}

object GameEngine {

    val NEON_COLORS = listOf(
        Color(0xFF42E8FF),
        Color(0xFFFF73A0),
        Color(0xFFFFCF5B),
        Color(0xFF5FF0B1),
        Color(0xFF9B6EFF),
        Color(0xFFFF5678),
    )

    fun loadLevelFromJson(jsonString: String): LightningPuzzle = JsonLevelLoader.parseLevelFromJson(jsonString)

    fun generatePuzzle(level: Int): LightningPuzzle {
        val levelInfo = LevelConfig.getLevelInfo(level)
        val safeLevel = levelInfo.level
        val rows = levelInfo.gridSize
        val cols = rows
        val targetArrows = levelInfo.arrowCount
        val random = Random((safeLevel.toLong() * 9973L) + 42L)

        val arrows = mutableListOf<LightningArrow>()
        val occupied = Array(rows) { BooleanArray(cols) }
        var arrowId = 1

        val allDirections = arrayOf(
            Direction.LEFT,
            Direction.RIGHT,
            Direction.UP,
            Direction.DOWN,
        )

        var attempts = 0
        val maxAttempts = targetArrows * 10

        while ((arrows.size < targetArrows) && (attempts < maxAttempts)) {
            attempts++

            val candidates = mutableListOf<Triple<Int, Int, Direction>>()

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (occupied[r][c]) continue

                    for (dir in allDirections) {
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
                            if ((rayR !in 0 until rows) || (rayC !in 0 until cols)) break
                            if (occupied[rayR][rayC]) {
                                clearRay = false
                                break
                            }
                        }

                        if (clearRay) {
                            candidates.add(Triple(r, c, dir))
                        }
                    }
                }
            }

            if (candidates.isEmpty()) break

            val (hr, hc, dir) = candidates[random.nextInt(candidates.size)]
            val path = mutableListOf<Pair<Int, Int>>()
            path.add(Pair(hr, hc))
            occupied[hr][hc] = true

            val (br, bc) = when (dir) {
                Direction.LEFT -> Pair(hr, hc + 1)
                Direction.RIGHT -> Pair(hr, hc - 1)
                Direction.UP -> Pair(hr + 1, hc)
                Direction.DOWN -> Pair(hr - 1, hc)
            }

            if (br in 0 until rows && bc in 0 until cols && !occupied[br][bc]) {
                path.add(0, Pair(br, bc))
                occupied[br][bc] = true

                val maxSegLen = if (safeLevel >= 5) random.nextInt(3) + 2 else random.nextInt(2) + 1
                var currR = br
                var currC = bc

                var step = 2
                while (step <= maxSegLen) {
                    step++
                    val neighbors = mutableListOf<Pair<Int, Int>>()
                    val dr = intArrayOf(-1, 1, 0, 0)
                    val dc = intArrayOf(0, 0, -1, 1)

                    for (i in 0..3) {
                        val nr = currR + dr[i]
                        val nc = currC + dc[i]
                        if (nr in 0 until rows && nc in 0 until cols && !occupied[nr][nc]) {
                            neighbors.add(Pair(nr, nc))
                        }
                    }

                    if (neighbors.isEmpty()) break
                    val next = neighbors[random.nextInt(neighbors.size)]
                    currR = next.first
                    currC = next.second
                    path.add(0, Pair(currR, currC))
                    occupied[currR][currC] = true
                }
            }

            if (path.isNotEmpty()) {
                val color = NEON_COLORS[arrows.size % NEON_COLORS.size]
                arrows.add(
                    LightningArrow(
                        id = arrowId++,
                        path = path,
                        direction = dir,
                        color = color,
                    ),
                )
            }
        }

        arrows.reverse()

        return LightningPuzzle(
            level = safeLevel,
            rows = rows,
            cols = cols,
            arrows = arrows,
        )
    }

    fun isArrowClear(puzzle: LightningPuzzle, arrow: LightningArrow): Boolean {
        if (!arrow.active) return false

        val headR = arrow.headRow
        val headC = arrow.headCol
        val dir = arrow.direction

        var r = headR
        var c = headC

        while (true) {
            when (dir) {
                Direction.LEFT -> c--
                Direction.RIGHT -> c++
                Direction.UP -> r--
                Direction.DOWN -> r++
            }

            if (r !in 0 until puzzle.rows || c !in 0 until puzzle.cols) {
                return true
            }

            val blockingArrow = puzzle.arrows.firstOrNull { it.active && it !== arrow && it.occupies(r, c) }
            if (blockingArrow != null) {
                return false
            }
        }
    }
}
