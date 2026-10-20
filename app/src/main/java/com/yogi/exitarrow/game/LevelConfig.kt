package com.yogi.exitarrow.game

data class LevelInfo(
    val level: Int,
    val gridSize: Int,
    val arrowCount: Int,
    val difficulty: String,
    val themeName: String,
)

object LevelConfig {

    private val THEME_NAMES = listOf(
        "Neon Circuit",
        "Quantum Mesh",
        "Winding Grid",
        "Cyber Lattice",
        "Cosmic Maze",
        "Infinity Web",
        "Plasma Matrix",
        "Starlight Node",
    )

    fun getLevelInfo(level: Int): LevelInfo {
        val safeLevel = maxOf(1, level)

        val gridSize = when {
            safeLevel <= 3 -> 8
            safeLevel <= 7 -> 10
            safeLevel <= 15 -> 12
            safeLevel <= 30 -> 16
            safeLevel <= 50 -> 20
            safeLevel <= 85 -> 24
            safeLevel <= 100 -> 28
            else -> minOf(36, 28 + ((safeLevel - 100) / 25))
        }

        val arrowCount = when {
            safeLevel == 1 -> 12
            safeLevel == 2 -> 18
            safeLevel == 3 -> 24
            safeLevel <= 10 -> 24 + ((safeLevel - 3) * 6)
            safeLevel <= 30 -> 66 + ((safeLevel - 10) * 8)
            safeLevel <= 85 -> 226 + ((safeLevel - 30) * 10)
            safeLevel <= 100 -> 776 + ((safeLevel - 85) * 12)
            else -> 956 + ((safeLevel - 100) * 15)
        }

        val difficulty = when {
            safeLevel <= 3 -> "Beginner"
            safeLevel <= 10 -> "Easy"
            safeLevel <= 25 -> "Medium"
            safeLevel <= 50 -> "Hard"
            safeLevel <= 85 -> "Super Hard"
            safeLevel <= 150 -> "Expert"
            safeLevel <= 300 -> "Master"
            else -> "Grandmaster 1000+"
        }

        val themeName = THEME_NAMES[(safeLevel - 1) % THEME_NAMES.size]

        return LevelInfo(
            level = safeLevel,
            gridSize = gridSize,
            arrowCount = arrowCount,
            difficulty = difficulty,
            themeName = themeName,
        )
    }
}
