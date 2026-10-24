
package com.yogi.exitarrow

import android.content.Context
import androidx.core.content.edit

class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences(
        "exit_arrow_progress",
        Context.MODE_PRIVATE,
    )

    fun loadProgress() = GameProgress(
        unlockedLevel = prefs.getInt("unlocked_level", 1).coerceIn(1, 100),
        totalStars = prefs.getInt("total_stars", 0).coerceAtLeast(0),
        soundEnabled = prefs.getBoolean("sound_enabled", true),
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true),
        reducedMotion = prefs.getBoolean("reduced_motion", false)
    )

    fun saveProgress(progress: GameProgress) {
        prefs.edit {
            putInt("unlocked_level", progress.unlockedLevel.coerceAtLeast(1))
            putInt("total_stars", progress.totalStars.coerceAtLeast(0))
            putBoolean("sound_enabled", progress.soundEnabled)
            putBoolean("vibration_enabled", progress.vibrationEnabled)
            putBoolean("reduced_motion", progress.reducedMotion)
        }
    }

    fun unlockLevel(level: Int) {
        val current = prefs.getInt("unlocked_level", 1)
        prefs.edit()
            .putInt("unlocked_level", maxOf(current, level.coerceIn(1, 100)))
            .apply()
    }

    fun addStars(stars: Int) {
        val current = prefs.getInt("total_stars", 0)
        prefs.edit()
            .putInt("total_stars", (current + stars).coerceAtLeast(0))
            .apply()
    }

    fun spendStars(cost: Int): Boolean {
        if (cost <= 0) return false

        val current = prefs.getInt("total_stars", 0)
        if (current < cost) return false

        prefs.edit()
            .putInt("total_stars", current - cost)
            .apply()

        return true
    }

    fun saveBestStars(level: Int, stars: Int) {
        val key = "best_stars_$level"
        val old = prefs.getInt(key, 0)
        if (stars > old) prefs.edit().putInt(key, stars).apply()
    }

    fun bestStars(level: Int): Int =
        prefs.getInt("best_stars_$level", 0)
}
