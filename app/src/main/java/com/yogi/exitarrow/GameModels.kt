package com.yogi.exitarrow

enum class Direction {
    LEFT, RIGHT, UP, DOWN;

    companion object {
        fun fromString(value: String) = valueOf(value.trim().uppercase())
    }
}

class Arrow(
    val id: Int,
    val path: List<Pair<Int, Int>>, // Snaking cell points from tail (first) to head (last)
    direction: Direction,           // Pointing direction of arrowhead at path.last()
    val color: Int,
    var active: Boolean = true,
) {
    val direction: Direction = if (path.size >= 2) {
        val (p0Row, p0Col) = path[path.size - 2]
        val (headRow, headCol) = path.last()
        when {
            headRow < p0Row -> Direction.UP
            headRow > p0Row -> Direction.DOWN
            headCol < p0Col -> Direction.LEFT
            headCol > p0Col -> Direction.RIGHT
            else -> direction
        }
    } else {
        direction
    }
    var animProgress = 0f
    var offsetX = 0f
    var offsetY = 0f
    var alpha = 1f

    val headRow: Int get() = path.last().first
    val headCol: Int get() = path.last().second
    val tailRow: Int get() = path.first().first
    val tailCol: Int get() = path.first().second

    // Legacy compatibility getters
    val row: Int get() = headRow
    val col: Int get() = headCol

    fun occupies(r: Int, c: Int): Boolean =
        path.any { (pr, pc) -> (pr == r) && (pc == c) }

    fun resetAnimation() {
        animProgress = 0f
        offsetX = 0f
        offsetY = 0f
        alpha = 1f
    }
}

data class Puzzle(
    val title: String,
    val rows: Int,
    val cols: Int,
    val arrows: MutableList<Arrow>,
    val level: Int = 1,
    val difficulty: String = "Easy",
    val seed: Long = 0L,
) {
    init {
        require((rows > 0) && (cols > 0))
        require(arrows.all { arrow ->
            arrow.path.all { (r, c) -> r in 0 until rows && c in 0 until cols }
        })
    }

    fun activeArrows() = arrows.filter { it.active }

    fun reset() {
        arrows.forEach {
            it.active = true
            it.resetAnimation()
        }
    }
}

data class LevelResult(
    val level: Int,
    val moves: Int,
    val stars: Int,
    val bestCombo: Int,
    val hintsUsed: Int
)

data class GameProgress(
    val unlockedLevel: Int = 1,
    val totalStars: Int = 0,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val reducedMotion: Boolean = false
)

data class HintResult(val arrowId: Int, val message: String)

data class GameStats(
    val moves: Int = 0,
    val lives: Int = 3,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val hintsRemaining: Int = 3,
    val score: Int = 0
)
