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
        if (!progress.reducedMotion) {
            GameEffects.openingLogoBounceDropIn(brand)
        }

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
        if (!progress.reducedMotion) {
            GameEffects.organicBreathingGlow(
                view = hintButton,
                minAlpha = 0.85f,
                maxAlpha = 1.0f,
                durationMs = 1800L
            )
        }

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
            // Generate level instantly (<10ms) without offline network timeout delay
            val puzzle = PuzzleGenerator.generate(level, maxResponsiveGridSize)

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
                statusLabel.text = "${puzzle.difficulty} · ${puzzle.rows}×${puzzle.cols} Grid"

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
            if (!progress.reducedMotion) {
                GameEffects.shake(board)
            }
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

        val oldStars = progress.totalStars
        storage.saveBestMoves(level, moves)
        storage.saveBestStars(level, stars)
        storage.addStars(stars)
        storage.unlockLevel(level + 1)

        progress = storage.loadProgress()
        if (!progress.reducedMotion) {
            GameEffects.numberTicker(starsLabel, oldStars, progress.totalStars, prefix = "★ ")
        } else {
            starsLabel.text = "★ ${progress.totalStars}"
        }

        confettiOverlay.start()
        GameAudio.playVictory(progress.soundEnabled)
        GameEffects.celebration(board, progress.reducedMotion)
        GameEffects.vibrate(this, progress.vibrationEnabled, 100)

        showLevelCompletedDialog(stars, level + 1)
    }

    private fun showLevelCompletedDialog(stars: Int, nextLevel: Int) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                GameTheme.dp(this@MainActivity, 18),
                GameTheme.dp(this@MainActivity, 20),
                GameTheme.dp(this@MainActivity, 18),
                GameTheme.dp(this@MainActivity, 20)
            )
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 35, 51)) // #1C2333 Dark Wood/Slate Card
                cornerRadius = GameTheme.dp(this@MainActivity, 20).toFloat()
                setStroke(GameTheme.dp(this@MainActivity, 3), Color.rgb(255, 200, 70)) // #FFC846 Gold border
            }
        }

        // Top Winged Ribbon Header Banner (Matching attached artwork)
        val bannerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        }

        val leftWing = GameTheme.text(this, "🪽", 28f).apply {
            rotation = -12f
        }

        val ribbonText = GameTheme.text(this, "👑 LEVEL $level COMPLETED 👑", 15f, Color.rgb(255, 213, 79), true).apply {
            setPadding(
                GameTheme.dp(this@MainActivity, 14),
                GameTheme.dp(this@MainActivity, 8),
                GameTheme.dp(this@MainActivity, 14),
                GameTheme.dp(this@MainActivity, 8)
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(130, 40, 190), Color.rgb(75, 20, 120), Color.rgb(130, 40, 190)) // Royal Purple Ribbon
            ).apply {
                cornerRadius = GameTheme.dp(this@MainActivity, 20).toFloat()
                setStroke(GameTheme.dp(this@MainActivity, 2), Color.rgb(255, 213, 79))
            }
        }

        val rightWing = GameTheme.text(this, "🪽", 28f).apply {
            rotation = 12f
            scaleX = -1f
        }

        bannerContainer.addView(leftWing)
        bannerContainer.addView(ribbonText)
        bannerContainer.addView(rightWing)

        cardLayout.addView(bannerContainer)

        val space1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, GameTheme.dp(this@MainActivity, 14))
        }
        cardLayout.addView(space1)

        // Staggered Animated Stars Row
        val starsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        for (i in 0 until 3) {
            val starChar = if (i < stars) "★" else "☆"
            val starView = GameTheme.text(this, starChar, 42f, Color.rgb(255, 213, 79), true).apply {
                setPadding(8, 0, 8, 0)
            }
            starsRow.addView(starView)

            if (!progress.reducedMotion) {
                val delayMs = 100L + i * 300L
                starView.scaleX = 0f
                starView.scaleY = 0f
                starView.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setStartDelay(delayMs)
                    .setDuration(300)
                    .withEndAction {
                        starView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }
        cardLayout.addView(starsRow)

        val space2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, GameTheme.dp(this@MainActivity, 16))
        }
        cardLayout.addView(space2)

        // Stats Box (#272D3D)
        val statsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                GameTheme.dp(this@MainActivity, 16),
                GameTheme.dp(this@MainActivity, 14),
                GameTheme.dp(this@MainActivity, 16),
                GameTheme.dp(this@MainActivity, 14)
            )
            background = GradientDrawable().apply {
                setColor(Color.rgb(39, 45, 61)) // #272D3D
                cornerRadius = GameTheme.dp(this@MainActivity, 12).toFloat()
            }
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val timeStr = String.format(java.util.Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
        val timeTxt = GameTheme.text(this, "⏱ Time: $timeStr", 14f, Color.WHITE)
        val spaceBetween = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(GameTheme.dp(this@MainActivity, 16), 1)
        }
        val movesTxt = GameTheme.text(this, "🎯 Moves: $moves", 14f, Color.WHITE)

        row1.addView(timeTxt)
        row1.addView(spaceBetween)
        row1.addView(movesTxt)
        statsBox.addView(row1)

        val spaceInBox = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, GameTheme.dp(this@MainActivity, 8))
        }
        statsBox.addView(spaceInBox)

        val unlockedTxt = GameTheme.text(this, "🔓 Unlocked: Level $nextLevel", 14f, Color.WHITE).apply {
            gravity = Gravity.CENTER
        }
        statsBox.addView(unlockedTxt)

        cardLayout.addView(statsBox, LinearLayout.LayoutParams(-1, -2))

        val space3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, GameTheme.dp(this@MainActivity, 20))
        }
        cardLayout.addView(space3)

        // Action Buttons
        val nextBtn = boxButton("▶ NEXT LEVEL ($nextLevel)", Color.rgb(255, 213, 79)).apply {
            setTextColor(Color.BLACK)
            setOnClickListener {
                dialog.dismiss()
                loadLevel(nextLevel)
            }
        }
        if (!progress.reducedMotion) {
            GameEffects.pulse(nextBtn)
        }

        val mapBtn = boxButton("🌀 LEVEL MAP", Color.rgb(77, 208, 225)).apply {
            setTextColor(Color.BLACK)
            setOnClickListener {
                dialog.dismiss()
                showCandyCrushLevelMapDialog()
            }
        }

        val replayBtn = boxButton("↻ REPLAY LEVEL", Color.WHITE).apply {
            setTextColor(Color.BLACK)
            setOnClickListener {
                dialog.dismiss()
                loadLevel(level)
            }
        }

        val btnParams = LinearLayout.LayoutParams(-1, GameTheme.dp(this, 50)).apply {
            bottomMargin = GameTheme.dp(this@MainActivity, 12)
        }

        cardLayout.addView(nextBtn, btnParams)
        cardLayout.addView(mapBtn, btnParams)
        cardLayout.addView(replayBtn, LinearLayout.LayoutParams(-1, GameTheme.dp(this, 50)))

        // Wrap card in a container frame explicitly sized MATCH_PARENT to prevent particle clipping
        val dialogFrame = FrameLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val dialogConfetti = ConfettiView(this)
        val starCelebration = StarCelebrationView(this)

        val cardParams = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Add views in Z-order: Floating Stars (back), Card (middle), Confetti (front)
        dialogFrame.addView(starCelebration, FrameLayout.LayoutParams(-1, -1))
        dialogFrame.addView(cardLayout, cardParams)
        dialogFrame.addView(dialogConfetti, FrameLayout.LayoutParams(-1, -1))

        dialog.setContentView(dialogFrame)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.argb(200, 9, 14, 29)))

        dialog.show()

        // Fire particle and bounce animations
        if (!progress.reducedMotion) {
            dialogConfetti.start()
            starCelebration.start()
            GameEffects.inertialBounce(cardLayout)
            GameEffects.pulse(nextBtn)
        }
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
                val oldStars = progress.totalStars
                storage.addStars(-10)
                progress = storage.loadProgress()
                lives = minOf(3, lives + 1)
                updateUI()
                if (!progress.reducedMotion) {
                    GameEffects.numberTicker(starsLabel, oldStars, progress.totalStars, prefix = "★ ")
                }
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
