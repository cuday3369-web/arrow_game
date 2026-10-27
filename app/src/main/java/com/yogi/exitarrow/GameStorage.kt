package com.yogi.exitarrow

import android.content.Context

/** Result of recording one successful level completion. */
data class LevelCompletionUpdate(
    val firstCompletion: Boolean,
    val starsAwarded: Int,
    val bestStars: Int,
    val unlockedLevel: Int,
    val totalStars: Int
)

class GameStorage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "exit_arrow_progress",
        Context.MODE_PRIVATE
    )

    init {
        initializeWelcomeBonus()
    }

    /** Give 100 welcome stars only when no existing balance has been saved. */
    private fun initializeWelcomeBonus() {
        synchronized(this) {
            if (!prefs.getBoolean("welcome_bonus_claimed", false)) {
                val editor = prefs.edit()

                if (!prefs.contains("total_stars")) {
                    editor.putInt("total_stars", 100)
                }

                editor.putBoolean("welcome_bonus_claimed", true)
                editor.commit()
            }
        }
    }

    fun loadProgress(): GameProgress = GameProgress(
        unlockedLevel = prefs.getInt("unlocked_level", 1).coerceIn(1, 100),
        totalStars = prefs.getInt("total_stars", 100).coerceAtLeast(0),
        soundEnabled = prefs.getBoolean("sound_enabled", true),
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true),
        reducedMotion = prefs.getBoolean("reduced_motion", false)
    )

    /** Save settings without allowing stale progress to lower unlocks or stars. */
    fun saveProgress(progress: GameProgress) {
        synchronized(this) {
            val savedUnlocked = prefs.getInt("unlocked_level", 1)
                .coerceIn(1, 100)

            val savedStars = prefs.getInt(
                "total_stars",
                progress.totalStars
            ).coerceAtLeast(0)

            prefs.edit()
                .putInt(
                    "unlocked_level",
                    maxOf(
                        savedUnlocked,
                        progress.unlockedLevel.coerceIn(1, 100)
                    )
                )
                .putInt("total_stars", savedStars)
                .putBoolean("sound_enabled", progress.soundEnabled)
                .putBoolean("vibration_enabled", progress.vibrationEnabled)
                .putBoolean("reduced_motion", progress.reducedMotion)
                .commit()
        }
    }

    /** Unlock monotonically: previously unlocked levels cannot be relocked. */
    fun unlockLevel(level: Int) {
        synchronized(this) {
            val current = prefs.getInt("unlocked_level", 1)
                .coerceIn(1, 100)

            val updated = maxOf(
                current,
                level.coerceIn(1, 100)
            )

            prefs.edit()
                .putInt("unlocked_level", updated)
                .commit()
        }
    }

    /**
     * Records a level win atomically.
     *
     * First completion:
     * - Saves completion status.
     * - Awards the earned stars once.
     * - Updates best stars.
     * - Unlocks the next level.
     *
     * Replay:
     * - May improve best stars.
     * - Does not award duplicate completion stars.
     * - Never reduces the unlocked level.
     */
    fun recordLevelCompletion(
        level: Int,
        stars: Int
    ): LevelCompletionUpdate {

        val completedLevel = level.coerceIn(1, 100)
        val earnedStars = stars.coerceIn(0, 3)

        synchronized(this) {
            val completionKey = "level_completed_$completedLevel"
            val wasCompleted = prefs.getBoolean(completionKey, false)
            val firstCompletion = !wasCompleted

            val oldBestStars = prefs.getInt(
                "best_stars_$completedLevel",
                0
            ).coerceIn(0, 3)

            val bestStars = maxOf(oldBestStars, earnedStars)

            val oldTotalStars = prefs.getInt(
                "total_stars",
                100
            ).coerceAtLeast(0)

            val starsAwarded = if (firstCompletion) {
                earnedStars
            } else {
                0
            }

            val newTotalStars = oldTotalStars + starsAwarded

            val oldUnlocked = prefs.getInt(
                "unlocked_level",
                1
            ).coerceIn(1, 100)

            val nextLevel = (completedLevel + 1).coerceAtMost(100)

            // Always preserve the highest unlocked level.
            val newUnlocked = maxOf(oldUnlocked, nextLevel)

            val saved = prefs.edit()
                .putBoolean(completionKey, true)
                .putInt("best_stars_$completedLevel", bestStars)
                .putInt("total_stars", newTotalStars)
                .putInt("unlocked_level", newUnlocked)
                .commit()

            if (!saved) {
                return LevelCompletionUpdate(
                    firstCompletion = false,
                    starsAwarded = 0,
                    bestStars = prefs.getInt(
                        "best_stars_$completedLevel",
                        0
                    ),
                    unlockedLevel = prefs.getInt(
                        "unlocked_level",
                        1
                    ).coerceIn(1, 100),
                    totalStars = prefs.getInt(
                        "total_stars",
                        100
                    ).coerceAtLeast(0)
                )
            }

            return LevelCompletionUpdate(
                firstCompletion = firstCompletion,
                starsAwarded = starsAwarded,
                bestStars = bestStars,
                unlockedLevel = newUnlocked,
                totalStars = newTotalStars
            )
        }
    }

    fun isLevelCompleted(level: Int): Boolean =
        prefs.getBoolean(
            "level_completed_${level.coerceIn(1, 100)}",
            false
        )

    fun addStars(stars: Int) {
        if (stars <= 0) return

        synchronized(this) {
            val current = prefs.getInt(
                "total_stars",
                100
            ).coerceAtLeast(0)

            prefs.edit()
                .putInt("total_stars", current + stars)
                .commit()
        }
    }

    fun spendStars(cost: Int): Boolean {
        if (cost <= 0) return false

        synchronized(this) {
            val current = prefs.getInt(
                "total_stars",
                100
            ).coerceAtLeast(0)

            if (current < cost) return false

            return prefs.edit()
                .putInt("total_stars", current - cost)
                .commit()
        }
    }

    fun saveBestMoves(level: Int, moves: Int) {
        val key = "best_moves_${level.coerceIn(1, 100)}"
        val old = prefs.getInt(key, Int.MAX_VALUE)

        if (moves < old) {
            prefs.edit()
                .putInt(key, moves)
                .apply()
        }
    }

    fun bestMoves(level: Int): Int =
        prefs.getInt(
            "best_moves_${level.coerceIn(1, 100)}",
            -1
        )

    fun saveBestStars(level: Int, stars: Int) {
        val key = "best_stars_${level.coerceIn(1, 100)}"
        val old = prefs.getInt(key, 0)
        val newStars = stars.coerceIn(0, 3)

        if (newStars > old) {
            prefs.edit()
                .putInt(key, newStars)
                .commit()
        }
    }

    fun bestStars(level: Int): Int =
        prefs.getInt(
            "best_stars_${level.coerceIn(1, 100)}",
            0
        ).coerceIn(0, 3)
}