package com.yogi.exitarrow.game

import android.os.Handler
import android.os.Looper

class GameController(
    private val engine: PuzzleEngine,
    private val saves: GameSaveRepository,
    private val onStateChanged: (GameState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var unlockedLevel = saves.getUnlockedLevel()
    private var totalStars = saves.getStars()
    private var soundEnabled = saves.isSoundEnabled()
    private var vibrationEnabled = saves.isVibrationEnabled()
    private var reducedMotion = saves.isReducedMotion()

    var state: GameState = GameState()
        private set

    private var lastTick = 0L
    private var active = false

    private val timer = object : Runnable {
        override fun run() {
            if (!active || (state.phase != GamePhase.PLAYING)) return

            val now = android.os.SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceAtLeast(0L)
            lastTick = now

            state = state.copy(elapsedMillis = state.elapsedMillis + delta)
            publish()

            handler.postDelayed(this, 250L)
        }
    }

    fun start(level: Int = saves.getCurrentLevel()) {
        handler.removeCallbacks(timer)
        active = false

        val safeLevel = level.coerceAtLeast(1)
        val arrows = engine.createLevel(safeLevel)

        state = GameState(
            level = safeLevel,
            phase = GamePhase.PLAYING,
            arrows = arrows,
            moves = 0,
            lives = 3,
            hintsRemaining = 1,
            elapsedMillis = 0L,
            stars = totalStars,
            rewardedLevels = state.rewardedLevels,
        )

        active = true
        lastTick = android.os.SystemClock.elapsedRealtime()
        publish()
        handler.postDelayed(timer, 250L)
        persist()
    }

    fun pause() {
        if (state.phase != GamePhase.PLAYING) return

        state = state.copy(phase = GamePhase.PAUSED)
        stopTimer()
        publish()
        persist()
    }

    fun resume() {
        if (state.phase != GamePhase.PAUSED) return

        state = state.copy(phase = GamePhase.PLAYING)
        active = true
        lastTick = android.os.SystemClock.elapsedRealtime()
        publish()
        handler.postDelayed(timer, 250L)
        persist()
    }

    fun tapArrow(arrowId: String) {
        if (state.phase != GamePhase.PLAYING) return

        val arrow = state.arrows.find { (id) -> id == arrowId } ?: return
        if (arrow.removed) return

        if (!engine.canExit(arrowId, state.arrows)) {
            val remainingLives = (state.lives - 1).coerceAtLeast(0)

            state = state.copy(
                lives = remainingLives,
                phase = if (remainingLives == 0) {
                    GamePhase.LOST
                } else {
                    GamePhase.PLAYING
                },
            )

            if (state.phase == GamePhase.LOST) stopTimer()

            publish()
            persist()
            return
        }

        // Mark the arrow as removed only after a valid exit
        state = state.copy(
            arrows = state.arrows.map {
                if (it.id == arrowId) it.copy(removed = true) else it
            }
        )

        if (state.isComplete) completeLevel()

        publish()
        persist()
    }

    fun useHint(): String? {
        if (state.phase != GamePhase.PLAYING) return null
        if (state.hintsRemaining <= 0) return null

        val validId = engine.findHint(state.arrows) ?: return null

        state = state.copy(hintsRemaining = state.hintsRemaining - 1)
        publish()
        persist()

        return validId
    }

    fun useExtraHint(): String? {
        if (state.phase != GamePhase.PLAYING) return null
        return engine.findHint(state.arrows)
    }

    fun reset() {
        start(state.level)
    }

    fun replay() {
        start(state.level)
    }

    fun nextLevel() {
        start(state.level + 1)
    }

    fun completeLevel() {
        if (state.phase == GamePhase.WON ||
            state.phase == GamePhase.LOST
        ) return

        stopTimer()

        // MainActivity is the single owner of completion rewards.
        // Do not add stars or unlock levels here.
        totalStars = saves.getStars()
        unlockedLevel = maxOf(unlockedLevel, state.level + 1)

        state = state.copy(
            phase = GamePhase.WON,
            stars = totalStars,
            rewardedLevels = state.rewardedLevels + state.level,
        )

        persist()
    }

    fun grantBonusLife(): Boolean {
        if (state.phase != GamePhase.PLAYING || state.lives >= 3) {
            return false
        }

        state = state.copy(
            lives = (state.lives + 1).coerceAtMost(3)
        )

        publish()
        persist()
        return true
    }

    fun spendStars(amount: Int): Boolean {
        if (amount <= 0) return false

        totalStars = saves.getStars()
        if (totalStars < amount) return false

        saves.addStars(-amount)
        totalStars = saves.getStars()

        state = state.copy(stars = totalStars)

        publish()
        persist()
        return true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        saveSettings()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
        saveSettings()
    }

    fun setReducedMotion(enabled: Boolean) {
        reducedMotion = enabled
        saveSettings()
    }

    fun isSoundEnabled() = soundEnabled
    fun isVibrationEnabled() = vibrationEnabled
    fun isReducedMotion() = reducedMotion

    fun onAppBackgrounded() {
        if (state.phase == GamePhase.PLAYING) pause()
        persist()
    }

    fun close() {
        stopTimer()
        persist()
    }

    fun restoreOrStart() {
        val saved = saves.loadSnapshot()

        if ((saved != null) &&
            saved.arrows.isNotEmpty() &&
            (saved.phase != GamePhase.WON) &&
            (saved.phase != GamePhase.LOST)
        ) {
            handler.removeCallbacks(timer)
            active = false

            state = saved.copy(phase = GamePhase.PAUSED)
            publish()
            persist()
        } else {
            start(saves.getCurrentLevel())
        }
    }

    private fun stopTimer() {
        active = false
        handler.removeCallbacks(timer)
    }

    private fun publish() {
        onStateChanged(state)
    }

    private fun persist() {
        saves.saveProgress(
            currentLevel = state.level,
            unlockedLevel = unlockedLevel,
            stars = totalStars,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            reducedMotion = reducedMotion
        )
        saves.saveSnapshot(state)
    }

    private fun saveSettings() {
        saves.saveProgress(
            currentLevel = state.level,
            unlockedLevel = unlockedLevel,
            stars = totalStars,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            reducedMotion = reducedMotion
        )
        publish()
    }
}
