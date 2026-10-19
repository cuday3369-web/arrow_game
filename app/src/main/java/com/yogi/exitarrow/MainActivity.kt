package com.yogi.exitarrow

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var frameRoot: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var board: PuzzleBoardView
    private lateinit var levelLabel: TextView
    private lateinit var timerLabel: TextView
    private lateinit var statsLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var starsLabel: TextView
    private lateinit var hintButton: Button
    private lateinit var pauseButton: Button
    private lateinit var confettiOverlay: ConfettiView

    private lateinit var storage: GameStorage
    private lateinit var api: ApiClient

    private val executor = Executors.newSingleThreadExecutor()

    private var progress = GameProgress()
    private var level = 1
    private var moves = 0
    private var lives = 3
    private var hintsRemaining = 3
    private var gameOver = false
    private var paused = false
    private var loading = false
    private var finishingLevel = false
    private var hintsUsed = 0
    private var totalLevelArrows = 0
    private var awarded50PercentHeart = false
    private var awarded75PercentHeart = false

    private var elapsedSeconds = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!paused && !loading && !gameOver && !finishingLevel) {
                elapsedSeconds++
                updateTimerText()
            }
            timerHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = GameStorage(this)
        progress = storage.loadProgress()
        level = progress.unlockedLevel

        api = ApiClient("http://10.171.52.13:8000")

        buildUI()
        board.onArrowTapped = ::onArrowTapped
        loadLevel(level)

        timerHandler.post(timerRunnable)
    }

    private fun updateTimerText() {
        if (!::timerLabel.isInitialized) return
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        timerLabel.text = String.format(java.util.Locale.US, "⏱ %02d:%02d", mins, secs)
    }

    private fun buildUI() {
        frameRoot = FrameLayout(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(6, 24, 6, 12)
            setBackgroundColor(Color.argb(215, 9, 14, 29))
        }

        // Apply Window Insets listener to dynamically shift content below status bar on all phone displays
        frameRoot.setOnApplyWindowInsetsListener { _, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }

            val topPad = maxOf(topInset, GameTheme.dp(this, 24))
            val bottomPad = maxOf(bottomInset, GameTheme.dp(this, 10))

            root.setPadding(
                GameTheme.dp(this, 6),
                topPad,
                GameTheme.dp(this, 6),
                bottomPad
            )
            insets
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(
            content,
            LinearLayout.LayoutParams(-1, -1)
        )

        confettiOverlay = ConfettiView(this)
        val colorfulBackground = ColorfulBackgroundView(this)

        frameRoot.addView(colorfulBackground, FrameLayout.LayoutParams(-1, -1))
        frameRoot.addView(root, FrameLayout.LayoutParams(-1, -1))
        frameRoot.addView(confettiOverlay, FrameLayout.LayoutParams(-1, -1))

        setContentView(frameRoot)

        // Top Header - Matching hand drawn layout
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val topMenuBtn = GameTheme.text(this, "≡ MENU", 14f, GameTheme.CYAN, true).apply {
            setPadding(12, 8, 12, 8)
            background = GameTheme.rounded(this@MainActivity, GameTheme.PANEL_LIGHT, 10)
            setOnClickListener { showMenuDialog() }
        }

        val brand = GameTheme.text(
            this, "EXIT ARROW", 20f, GameTheme.WHITE, true
        )
        brand.gravity = Gravity.CENTER
        brand.setOnClickListener { showSettingsDialog() }

        starsLabel = GameTheme.text(
            this, "★ ${progress.totalStars}", 15f, GameTheme.GOLD, true
        ).apply {
            setPadding(10, 6, 10, 6)
            background = GameTheme.rounded(this@MainActivity, GameTheme.PANEL_LIGHT, 8)
            setOnClickListener { buyHeartWithStars() }
        }

        val exitLevelBtn = GameTheme.text(this, "EXIT ✕", 13f, GameTheme.RED, true).apply {
            setPadding(10, 6, 10, 6)
            background = GameTheme.rounded(this@MainActivity, GameTheme.PANEL_LIGHT, 8)
            setOnClickListener { showCandyCrushLevelMapDialog() }
        }

        header.addView(topMenuBtn)
        header.addView(space(8), LinearLayout.LayoutParams(8, 1))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(starsLabel)
        header.addView(space(8), LinearLayout.LayoutParams(8, 1))
        header.addView(exitLevelBtn)
        content.addView(header)

        // Level Header
        levelLabel = GameTheme.text(
            this, "LEVEL $level", 24f, GameTheme.WHITE, true
        )
        levelLabel.gravity = Gravity.CENTER
        levelLabel.setPadding(0, 10, 0, 2)
        levelLabel.setOnClickListener { showCandyCrushLevelMapDialog() }
        content.addView(levelLabel)

        // Running Timer below Level Header
        timerLabel = GameTheme.text(
            this, "⏱ 00:00", 16f, GameTheme.GOLD, true
        )
        timerLabel.gravity = Gravity.CENTER
        timerLabel.setPadding(0, 2, 0, 6)
        content.addView(timerLabel)

        statsLabel = GameTheme.text(
            this, "", 14f, GameTheme.MUTED, true
        )
        statsLabel.gravity = Gravity.CENTER
        content.addView(statsLabel)

        // Dynamic Playground Board sizing for all phone screens
        board = PuzzleBoardView(this)
        val boardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        boardParams.topMargin = GameTheme.dp(this, 4)
        boardParams.bottomMargin = GameTheme.dp(this, 4)
        content.addView(board, boardParams)

        statusLabel = GameTheme.text(
            this, "Find a clear arrow.", 14f, GameTheme.MUTED
        )
        statusLabel.gravity = Gravity.CENTER
        statusLabel.setPadding(0, 4, 0, 6)
        content.addView(statusLabel)

        // Bottom Rectangular Container holding 3 Boxed Action Buttons
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                GameTheme.dp(this@MainActivity, 10),
                GameTheme.dp(this@MainActivity, 10),
                GameTheme.dp(this@MainActivity, 10),
                GameTheme.dp(this@MainActivity, 10)
            )
            background = GradientDrawable().apply {
                setColor(GameTheme.PANEL)
                cornerRadius = GameTheme.dp(this@MainActivity, 16).toFloat()
                setStroke(GameTheme.dp(this@MainActivity, 1), Color.rgb(40, 55, 85))
            }
        }

        val resetButton = boxButton("↺ RESET", GameTheme.RED)
        resetButton.setOnClickListener { loadLevel(level) }

        pauseButton = boxButton("Ⅱ PAUSE", GameTheme.CYAN)
        pauseButton.setOnClickListener { togglePause() }

        hintButton = boxButton("💡 HINT", GameTheme.GOLD)
        hintButton.setOnClickListener { showHint() }

        val boxParams = LinearLayout.LayoutParams(0, GameTheme.dp(this, 46), 1f)

        bottomContainer.addView(resetButton, boxParams)
        bottomContainer.addView(space(8), LinearLayout.LayoutParams(GameTheme.dp(this, 8), 1))
        bottomContainer.addView(pauseButton, boxParams)
        bottomContainer.addView(space(8), LinearLayout.LayoutParams(GameTheme.dp(this, 8), 1))
        bottomContainer.addView(hintButton, boxParams)

        val bottomContainerParams = LinearLayout.LayoutParams(-1, -2)
        bottomContainerParams.topMargin = GameTheme.dp(this, 4)
        bottomContainerParams.bottomMargin = GameTheme.dp(this, 4)
        content.addView(bottomContainer, bottomContainerParams)
    }

    private fun boxButton(label: String, color: Int): Button =
        Button(this).apply {
            text = label
            setTextColor(
                if (color == GameTheme.GOLD || color == GameTheme.WHITE) {
                    GameTheme.BACKGROUND
                } else {
                    GameTheme.WHITE
                }
            )
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GameTheme.rounded(this@MainActivity, color, 12)
            stateListAnimator = null
            setOnTouchListener { v, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    GameEffects.tap(v, progress.reducedMotion)
                    GameAudio.playTap(progress.soundEnabled)
                }
                false
            }
        }

    private fun space(width: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun loadLevel(target: Int) {
        if (loading) return

        loading = true
        paused = false
        gameOver = false
        finishingLevel = false
        level = target.coerceAtLeast(1)

        elapsedSeconds = 0
        updateTimerText()

        confettiOverlay.stop()
        board.cancelAnimations()
        board.clearHint()

        statusLabel.text = "Preparing level $level..."
        updateUI()

        val boardW = if (board.width > 0) board.width else resources.displayMetrics.widthPixels
        val boardH = if (board.height > 0) board.height else (resources.displayMetrics.heightPixels * 0.55).toInt()
        val minCellPx = (28f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val maxResponsiveGridSize = minOf((boardW / minCellPx), (boardH / minCellPx)).coerceIn(6, 24)

        executor.execute {
            val remotePuzzle = api.fetchLevel(level)
            val puzzle = remotePuzzle ?: PuzzleGenerator.generate(level, maxResponsiveGridSize)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                board.puzzle = puzzle
                totalLevelArrows = puzzle.arrows.size
                awarded50PercentHeart = false
                awarded75PercentHeart = false
                moves = 0
                lives = 3
                hintsUsed = 0
                hintsRemaining = when {
                    level <= 20 -> 3
                    level <= 50 -> 2
                    else -> 1
                }

                loading = false
                statusLabel.text = if (remotePuzzle == null) {
                    "${puzzle.difficulty} · ${puzzle.rows}×${puzzle.cols} Grid"
                } else {
                    "${puzzle.difficulty} · Find your first move."
                }

                updateUI()
                GameEffects.celebration(board, progress.reducedMotion)
            }
        }
    }

    private fun onArrowTapped(arrow: Arrow) {
        if (loading || paused || gameOver || finishingLevel) return
        if (!arrow.active || board.isArrowAnimating(arrow)) return

        if (!board.isClear(arrow)) {
            lives--
            board.animateBlockedArrow(arrow)
            statusLabel.text = "Blocked! Look for an arrow with a clear path."
            statusLabel.setTextColor(GameTheme.RED)
            GameAudio.playBlocked(progress.soundEnabled)
            GameEffects.vibrate(this, progress.vibrationEnabled, 60)
            updateUI()

            if (lives <= 0) showGameOver()
            return
        }

        arrow.active = false
        moves++

        GameAudio.playArrowExit(progress.soundEnabled)

        val remainingActive = board.puzzle.arrows.count { it.active && it != arrow }
        val cleared = totalLevelArrows - remainingActive
        val clearedRatio = if (totalLevelArrows > 0) cleared.toFloat() / totalLevelArrows.toFloat() else 0f

        if (remainingActive == 0) {
            statusLabel.text = "🎉 LEVEL $level COMPLETED!"
            statusLabel.setTextColor(GameTheme.GOLD)
            GameAudio.playVictory(progress.soundEnabled)
        } else {
            var milestoneText: String? = null

            if (!awarded50PercentHeart && clearedRatio >= 0.5f) {
                awarded50PercentHeart = true
                if (lives < 3) {
                    lives = minOf(3, lives + 1)
                    milestoneText = "❤️ +1 HEART BONUS! (50% Milestone!)"
                } else {
                    milestoneText = "⭐ 50% Milestone Reached!"
                }
            } else if (!awarded75PercentHeart && clearedRatio >= 0.75f) {
                awarded75PercentHeart = true
                if (lives < 3) {
                    lives = minOf(3, lives + 1)
                    milestoneText = "❤️ +1 HEART BONUS! (75% Milestone!)"
                } else {
                    milestoneText = "⭐ 75% Milestone Reached!"
                }
            }

            if (milestoneText != null) {
                statusLabel.text = milestoneText
                statusLabel.setTextColor(GameTheme.GREEN)
                GameAudio.playVictory(progress.soundEnabled)
            } else {
                statusLabel.text = "Nice move!"
                statusLabel.setTextColor(GameTheme.CYAN)
            }
        }

        updateUI()

        board.animateArrowOut(arrow) {
            if (isFinishing || isDestroyed) return@animateArrowOut

            if (PuzzleSolver.isSolved(board.puzzle)) {
                completeLevel()
            } else {
                statusLabel.text = "Great! Choose your next clear arrow."
                statusLabel.setTextColor(GameTheme.MUTED)
            }

            updateUI()
        }
    }

    private fun showHint() {
        if (loading || paused || gameOver || finishingLevel) return

        if (hintsRemaining <= 0) {
            statusLabel.text = "No hints left. Reset or keep trying."
            statusLabel.setTextColor(GameTheme.GOLD)
            return
        }

        val hint = PuzzleSolver.nextHint(board.puzzle)

        if (hint == null) {
            statusLabel.text = "No solution found. Reset the level."
            statusLabel.setTextColor(GameTheme.RED)
            return
        }

        val arrow = board.puzzle.arrows.firstOrNull {
            it.id == hint.arrowId && it.active &&
                    !board.isArrowAnimating(it)
        }

        if (arrow == null || !board.isClear(arrow)) {
            statusLabel.text = "Wait for the current animation to finish."
            return
        }

        hintsRemaining--
        hintsUsed++

        board.showHint(arrow)
        statusLabel.text = hint.message
        statusLabel.setTextColor(GameTheme.GOLD)

        GameAudio.playHint(progress.soundEnabled)
        GameEffects.vibrate(this, progress.vibrationEnabled, 25)
        updateUI()
    }

    private fun togglePause() {
        if (loading || finishingLevel || gameOver) return

        if (paused) {
            paused = false
            pauseButton.text = "Ⅱ PAUSE"
            statusLabel.text = "Resumed. Find your next clear arrow."
            updateUI()
            return
        }

        paused = true

        AlertDialog.Builder(this)
            .setTitle("Game paused")
            .setMessage("Take your time. Your progress is saved.")
            .setPositiveButton("Resume") { _, _ ->
                paused = false
                pauseButton.text = "Ⅱ PAUSE"
                updateUI()
            }
            .setNegativeButton("Restart level") { _, _ ->
                loadLevel(level)
            }
            .show()
    }

    private fun completeLevel() {
        if (finishingLevel || gameOver) return

        finishingLevel = true

        val stars = when {
            lives == 3 && hintsUsed == 0 -> 3
            lives >= 2 -> 2
            else -> 1
        }

        storage.saveBestMoves(level, moves)
        storage.saveBestStars(level, stars)
        storage.addStars(stars)
        storage.unlockLevel(level + 1)

        progress = storage.loadProgress()
        starsLabel.text = "★ ${progress.totalStars}"

        confettiOverlay.start()
        GameAudio.playVictory(progress.soundEnabled)
        GameEffects.celebration(board, progress.reducedMotion)
        GameEffects.vibrate(this, progress.vibrationEnabled, 100)

        val nextLevel = level + 1

        AlertDialog.Builder(this)
            .setTitle("🎉 LEVEL $level CLEARED!")
            .setMessage(
                "✨ Level $level Completed!\n" +
                        "🔓 Next Unlocked: Level $nextLevel\n\n" +
                        "⏱ Time: ${String.format(java.util.Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)}\n" +
                        "★ Stars Earned: ${"★".repeat(stars)}\n" +
                        "🎯 Moves: $moves\n\n" +
                        "Ready to start Level $nextLevel?"
            )
            .setCancelable(false)
            .setPositiveButton("▶ NEXT LEVEL ($nextLevel)") { _, _ ->
                loadLevel(nextLevel)
            }
            .setNeutralButton("🍭 LEVEL MAP") { _, _ ->
                showCandyCrushLevelMapDialog()
            }
            .setNegativeButton("↺ REPLAY") { _, _ ->
                loadLevel(level)
            }
            .show()
    }

    private fun buyHeartWithStars() {
        if (lives >= 3) {
            Toast.makeText(this, "❤️ Hearts are already full! (3/3)", Toast.LENGTH_SHORT).show()
            return
        }
        if (progress.totalStars < 10) {
            Toast.makeText(
                this,
                "★ You need at least 10 stars to buy 1 heart! (You have ★ ${progress.totalStars})",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("❤️ Exchange 10 Stars for 1 Heart?")
            .setMessage("Current Stars: ★ ${progress.totalStars}\nCurrent Hearts: $lives/3\n\nWould you like to buy 1 Heart for 10 Stars?")
            .setPositiveButton("BUY 1 HEART (★ 10)") { _, _ ->
                storage.addStars(-10)
                progress = storage.loadProgress()
                lives = minOf(3, lives + 1)
                updateUI()
                GameAudio.playVictory(progress.soundEnabled)
                Toast.makeText(
                    this,
                    "❤️ Purchased 1 Heart! Remaining Stars: ★ ${progress.totalStars}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGameOver() {
        gameOver = true
        paused = true

        val builder = AlertDialog.Builder(this)
            .setTitle("💔 Level $level Failed - Out of Hearts!")
            .setMessage(
                "⏱ Time taken: ${String.format(java.util.Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)}\n" +
                        "🎯 Moves made: $moves\n" +
                        "★ Your Stars: ★ ${progress.totalStars}\n\n" +
                        if (progress.totalStars >= 10) {
                            "You can exchange 10 stars for 1 Heart to continue, or restart the level."
                        } else {
                            "You ran out of hearts! Restart the level to try again."
                        }
            )
            .setCancelable(false)
            .setPositiveButton("↺ REPLAY LEVEL") { _, _ ->
                loadLevel(level)
            }
            .setNeutralButton("🍭 LEVEL MAP") { _, _ ->
                showCandyCrushLevelMapDialog()
            }

        if (progress.totalStars >= 10) {
            builder.setNegativeButton("❤️ BUY 1 HEART (★ 10)") { _, _ ->
                storage.addStars(-10)
                progress = storage.loadProgress()
                starsLabel.text = "★ ${progress.totalStars}"
                lives = 1
                gameOver = false
                paused = false
                updateUI()
                GameAudio.playVictory(progress.soundEnabled)
                Toast.makeText(
                    this,
                    "❤️ Purchased 1 Heart! (★ ${progress.totalStars} Stars left)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (level > 1) {
            builder.setNegativeButton("PREVIOUS LEVEL (${level - 1})") { _, _ ->
                loadLevel(level - 1)
            }
        }

        builder.show()
    }

    private fun showMenuDialog() {
        val options = arrayOf(
            "🍭 Level Map",
            "⚙ Settings"
        )

        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCandyCrushLevelMapDialog()
                    1 -> showSettingsDialog()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCandyCrushLevelMapDialog() {
        var dialog: AlertDialog? = null
        val totalMapLevels = 100

        val mapView = CandyCrushLevelMapView(
            this,
            totalLevels = totalMapLevels,
            unlockedLevel = progress.unlockedLevel,
            currentLevel = level,
            getBestStars = { lvl ->
                storage.bestStars(lvl)
            },
            onLevelSelected = { selectedLvl ->
                dialog?.dismiss()
                loadLevel(selectedLvl)
            },
            onLockedLevelTapped = { lockedLvl ->
                GameAudio.playBlocked(progress.soundEnabled)
                Toast.makeText(
                    this,
                    "🔒 Complete Level ${lockedLvl - 1} to unlock Level $lockedLvl!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(11, 16, 33))
            setPadding(0, 8, 0, 8)
            isFillViewport = true
            addView(mapView)
        }

        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("🗺️ EXIT MAP")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()

        dialog.show()

        val displayMetrics = resources.displayMetrics
        dialog.window?.setLayout(
            (displayMetrics.widthPixels * 0.94).toInt(),
            (displayMetrics.heightPixels * 0.88).toInt()
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.rgb(11, 16, 33)))

        scroll.post {
            val totalH = mapView.height
            val currentRatio = 1f - (level.toFloat() / totalMapLevels.toFloat())
            val targetY = (totalH * currentRatio).toInt().coerceIn(0, maxOf(0, totalH - scroll.height))
            scroll.scrollTo(0, targetY)
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            "Sound Effects: " + if (progress.soundEnabled) "ON" else "OFF",
            "Vibration: " + if (progress.vibrationEnabled) "ON" else "OFF",
            "Reduced Motion: " + if (progress.reducedMotion) "ON" else "OFF"
        )

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                val newProgress = when (which) {
                    0 -> progress.copy(soundEnabled = !progress.soundEnabled)
                    1 -> progress.copy(vibrationEnabled = !progress.vibrationEnabled)
                    2 -> progress.copy(reducedMotion = !progress.reducedMotion)
                    else -> progress
                }
                progress = newProgress
                storage.saveProgress(progress)
                Toast.makeText(
                    this,
                    "Settings updated",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun updateUI() {
        if (!::statsLabel.isInitialized) return

        levelLabel.text = "LEVEL $level"

        val remaining = if (::board.isInitialized) {
            board.puzzle.arrows.count { it.active }
        } else 0

        statsLabel.text =
            "Moves: $moves     Lives: ${"♥".repeat(lives.coerceAtLeast(0))}" +
                    "${"♡".repeat((3 - lives).coerceAtLeast(0))}" +
                    "     Arrows: $remaining"

        pauseButton.text = if (paused) "▶ RESUME" else "Ⅱ PAUSE"
        pauseButton.isEnabled = !loading && !finishingLevel && !gameOver

        hintButton.text = "💡 HINT ($hintsRemaining)"
        hintButton.isEnabled =
            !loading && !paused && !gameOver &&
                    !finishingLevel && hintsRemaining > 0

        starsLabel.text = "★ ${progress.totalStars}"
    }

    override fun onDestroy() {
        if (::board.isInitialized) board.cancelAnimations()
        timerHandler.removeCallbacks(timerRunnable)
        GameAudio.release()
        executor.shutdownNow()
        super.onDestroy()
    }
}
