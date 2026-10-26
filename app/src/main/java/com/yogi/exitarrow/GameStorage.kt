package com.yogi.exitarrow

import android.content.Context

class GameStorage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "exit_arrow_progress",
        Context.MODE_PRIVATE
    )

    init {
        initializeWelcomeBonus()
    }

    /**
     * Give 100 stars only when this installation has no saved star balance.
     * Existing players retain their current balance.
     */
    private fun initializeWelcomeBonus() {
        synchronized(this) {
            if (!prefs.getBoolean("welcome_bonus_claimed", false)) {
                val editor = prefs.edit()

                if (!prefs.contains("total_stars")) {
                    editor.putInt("total_stars", 100)
                }

                editor.putBoolean("welcome_bonus_claimed", true)
                editor.apply()
            }
        }
    }

    fun loadProgress() = GameProgress(
        unlockedLevel = prefs.getInt("unlocked_level", 1)
            .coerceIn(1, 100),
        totalStars = prefs.getInt("total_stars", 100)
            .coerceAtLeast(0),
        soundEnabled = prefs.getBoolean("sound_enabled", true),
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true),
        reducedMotion = prefs.getBoolean("reduced_motion", false)
    )

    fun saveProgress(progress: GameProgress) {
        prefs.edit()
            .putInt(
                "unlocked_level",
                progress.unlockedLevel.coerceIn(1, 100)
            )
            .putInt(
                "total_stars",
                progress.totalStars.coerceAtLeast(0)
            )
            .putBoolean("sound_enabled", progress.soundEnabled)
            .putBoolean("vibration_enabled", progress.vibrationEnabled)
            .putBoolean("reduced_motion", progress.reducedMotion)
            .apply()
    }

    fun unlockLevel(level: Int) {
        val current = prefs.getInt("unlocked_level", 1)
        prefs.edit()
            .putInt(
                "unlocked_level",
                maxOf(current, level.coerceIn(1, 100))
            )
            .apply()
    }

    fun addStars(stars: Int) {
        if (stars <= 0) return

        synchronized(this) {
            val current = prefs.getInt("total_stars", 100)
            prefs.edit()
                .putInt("total_stars", current + stars)
                .commit()
        }
    }

    fun spendStars(cost: Int): Boolean {
        if (cost <= 0) return false

        synchronized(this) {
            val current = prefs.getInt("total_stars", 100)
            if (current < cost) return false

            return prefs.edit()
                .putInt("total_stars", current - cost)
                .commit()
        }
    }

    fun saveBestMoves(level: Int, moves: Int) {
        val key = "best_moves_$level"
        val old = prefs.getInt(key, Int.MAX_VALUE)

        if (moves < old) {
            prefs.edit().putInt(key, moves).apply()
        }
    }

    fun bestMoves(level: Int): Int =
        prefs.getInt("best_moves_$level", -1)

    fun saveBestStars(level: Int, stars: Int) {
        val key = "best_stars_$level"
        val old = prefs.getInt(key, 0)

        if (stars > old) {
            prefs.edit().putInt(key, stars).apply()
        }
    }

    fun bestStars(level: Int): Int =
        prefs.getInt("best_stars_$level", 0)
}
