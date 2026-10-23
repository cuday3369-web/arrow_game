package com.yogi.exitarrow.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

import androidx.core.content.edit

class GameSaveRepository(context: Context) {

    private val prefs = context.getSharedPreferences(
        "exit_arrow_progress",
        Context.MODE_PRIVATE,
    )

    fun saveProgress(
        currentLevel: Int,
        unlockedLevel: Int,
        stars: Int,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        reducedMotion: Boolean,
    ) {
        prefs.edit {
            putInt("current_level", currentLevel)
            putInt("unlocked_level", maxOf(unlockedLevel, 1))
            putInt("total_stars", maxOf(stars, 0))
            putBoolean("sound_enabled", soundEnabled)
            putBoolean("vibration_enabled", vibrationEnabled)
            putBoolean("reduced_motion", reducedMotion)
        }
    }

    fun saveSnapshot(state: GameState) {
        val json = JSONObject().apply {
            put("level", state.level)
            put("phase", state.phase.name)
            put("moves", state.moves)
            put("lives", state.lives)
            put("hints", state.hintsRemaining)
            put("elapsed", state.elapsedMillis)

            put("arrows", JSONArray().apply {
                state.arrows.forEach { (id, removed) ->
                    put(JSONObject().apply {
                        put("id", id)
                        put("removed", removed)
                    })
                }
            })

            put("rewardedLevels", JSONArray().apply {
                state.rewardedLevels.forEach { put(it) }
            })
        }

        prefs.edit { putString("snapshot", json.toString()) }
    }

    fun loadSnapshot(): GameState? {
        val raw = prefs.getString("snapshot", null) ?: return null

        return try {
            val json = JSONObject(raw)
            val arrowsJson = json.getJSONArray("arrows")
            val arrows = (0 until arrowsJson.length()).map { i ->
                val arrow = arrowsJson.getJSONObject(i)
                ArrowState(
                    id = arrow.getString("id"),
                    removed = arrow.getBoolean("removed")
                )
            }

            val rewards = json.getJSONArray("rewardedLevels")
            val rewardedLevels = (0 until rewards.length())
                .mapTo(mutableSetOf()) { rewards.getInt(it) }

            GameState(
                level = json.getInt("level"),
                phase = GamePhase.valueOf(json.getString("phase")),
                arrows = arrows,
                moves = json.getInt("moves"),
                lives = json.getInt("lives"),
                hintsRemaining = json.getInt("hints"),
                elapsedMillis = json.getLong("elapsed"),
                rewardedLevels = rewardedLevels
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getCurrentLevel() = prefs.getInt("current_level", 1).coerceAtLeast(1)
    fun getUnlockedLevel() = prefs.getInt("unlocked_level", 1).coerceAtLeast(1)
    fun getStars() = prefs.getInt("total_stars", 0).coerceAtLeast(0)

    fun isSoundEnabled() = prefs.getBoolean("sound_enabled", true)
    fun isVibrationEnabled() = prefs.getBoolean("vibration_enabled", true)
    fun isReducedMotion() = prefs.getBoolean("reduced_motion", false)

    fun unlockLevel(level: Int) {
        val current = getUnlockedLevel()
        prefs.edit { putInt("unlocked_level", maxOf(current, level, 1)) }
    }

    fun addStars(stars: Int) {
        val current = getStars()
        prefs.edit().putInt("total_stars", (current + stars).coerceAtLeast(0)).apply()
    }

    fun saveBestMoves(level: Int, moves: Int) {
        val key = "best_moves_$level"
        val old = prefs.getInt(key, Int.MAX_VALUE)
        if (moves < old) prefs.edit().putInt(key, moves).apply()
    }

    fun bestMoves(level: Int): Int =
        prefs.getInt("best_moves_$level", -1)

    fun saveBestStars(level: Int, stars: Int) {
        val key = "best_stars_$level"
        val old = prefs.getInt(key, 0)
        if (stars > old) prefs.edit().putInt(key, stars).apply()
    }

    fun bestStars(level: Int): Int =
        prefs.getInt("best_stars_$level", 0)

    fun clearSnapshot() {
        prefs.edit().remove("snapshot").apply()
    }
}
