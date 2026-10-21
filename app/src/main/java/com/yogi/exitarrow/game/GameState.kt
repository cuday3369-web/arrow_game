package com.yogi.exitarrow.game

enum class GamePhase {
    READY, PLAYING, PAUSED, WON, LOST
}

data class ArrowState(
    val id: String,
    val removed: Boolean = false
)

data class GameState(
    val level: Int = 1,
    val phase: GamePhase = GamePhase.READY,
    val arrows: List<ArrowState> = emptyList(),
    val moves: Int = 0,
    val lives: Int = 3,
    val hintsRemaining: Int = 3,
    val elapsedMillis: Long = 0L,
    val stars: Int = 0,
    val rewardedLevels: Set<Int> = emptySet()
) {
    val arrowsLeft: Int
        get() = arrows.count { !it.removed }

    val isComplete: Boolean
        get() = arrows.isNotEmpty() && arrowsLeft == 0
}
