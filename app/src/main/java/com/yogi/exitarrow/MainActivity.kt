package com.yogi.exitarrow

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import com.yogi.exitarrow.game.GameController
import com.yogi.exitarrow.game.GamePuzzleEngine
import com.yogi.exitarrow.game.GameSaveRepository
import com.yogi.exitarrow.game.GameState
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var frameRoot: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var board: PuzzleBoardView
    private lateinit var mapContainer: FrameLayout
    private lateinit var homeMapView: CandyCrushLevelMapView
    private lateinit var contentContainer: LinearLayout
    private lateinit var exitLevelBtn: TextView
    private lateinit var levelLabel: TextView
    private lateinit var timerLabel: TextView
    private lateinit var statsLabel: TextView
    private lateinit var movesStatLabel: TextView
    private lateinit var livesStatLabel: TextView
    private lateinit var arrowsStatLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var starsLabel: TextView
    private lateinit var hintButton: Button
    private lateinit var pauseButton: Button
    private lateinit var confettiOverlay: ConfettiView
    private lateinit var colorfulBackground: ColorfulBackgroundView

    private lateinit var storage: GameStorage
    private lateinit var api: ApiClient
    private lateinit var gameController: GameController
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var puzzleEngine: GamePuzzleEngine

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
    private var hintProcessing = false

    private val elapsedSeconds: Int
        get() = if (::gameController.isInitialized) (gameController.state.elapsedMillis / 1000L).toInt() else 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = GameStorage(this)
        saveRepository = GameSaveRepository(applicationContext)
        puzzleEngine = GamePuzzleEngine()

        progress = storage.loadProgress()
        level = progress.unlockedLevel

        api = ApiClient("http://10.171.52.13:8000")

        gameController = GameController(
            engine = puzzleEngine,
            saves = saveRepository,
            onStateChanged = { state ->
                runOnUiThread {
                    syncWithGameState(state)
                }
            },
        )

        buildUI()
        board.onArrowTapped = ::onArrowTapped
        openLevelMapHome()
    }

    fun openLevelMapHome() {
        finishingLevel = false
        loading = false
        paused = true
        if (::exitLevelBtn.isInitialized) {
            exitLevelBtn.visibility = View.INVISIBLE
        }
        if (::homeMapView.isInitialized) {
            homeMapView.invalidate()
        }
        if (::mapContainer.isInitialized) {
            mapContainer.visibility = View.VISIBLE
        }
        if (::contentContainer.isInitialized) {
            contentContainer.visibility = View.GONE
        }
    }

    fun openGameScreen(targetLevel: Int) {
        if (::exitLevelBtn.isInitialized) {
            exitLevelBtn.visibility = View.VISIBLE
        }
        if (::mapContainer.isInitialized) {
            mapContainer.visibility = View.GONE
        }
        if (::contentContainer.isInitialized) {
            contentContainer.visibility = View.VISIBLE
        }
        loadLevel(targetLevel)
    }

    private fun syncWithGameState(state: GameState) {
        if (!::statsLabel.isInitialized) return

        moves = state.moves
        lives = state.lives
        level = state.level

        // Do not overwrite hintsRemaining here.
        // MainActivity owns the free-hint count for the current level.

        updateTimerText()
        updateUI()
    }

    private fun updateTimerText() {
        if (!::timerLabel.isInitialized) return
        val totalSecs = if (::gameController.isInitialized) {
            gameController.state.elapsedMillis / 1000L
        } else 0L
        val mins = (totalSecs / 60).toInt()
        val secs = (totalSecs % 60).toInt()
        timerLabel.text = getString(R.string.timer_format, mins, secs)
    }

    private fun buildUI() {
        frameRoot = FrameLayout(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 24), GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 10))
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

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
            root.setPadding(
                GameTheme.dp(this, 8), maxOf(topInset, GameTheme.dp(this, 14)),
                GameTheme.dp(this, 8), maxOf(bottomInset, GameTheme.dp(this, 8))
            )
            insets
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        homeMapView = CandyCrushLevelMapView(
            this,
            totalLevels = 100,
            unlockedLevel = progress.unlockedLevel,
            currentLevel = level,
            getBestStars = { levelNumber -> storage.bestStars(levelNumber) },
            onLevelSelected = { selectedLevel ->
                openGameScreen(selectedLevel)
            },
            onLockedLevelTapped = { lockedLevel ->
                GameAudio.playBlocked(progress.soundEnabled)
                Toast.makeText(
                    this,
                    "🔒 Complete Level ${lockedLevel - 1} to unlock Level $lockedLevel!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        mapContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 10, 31))
            addView(homeMapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        val screenHost = FrameLayout(this)
        screenHost.addView(mapContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        screenHost.addView(contentContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        confettiOverlay = ConfettiView(this)
        colorfulBackground = ColorfulBackgroundView(this)
        frameRoot.addView(colorfulBackground, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frameRoot.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frameRoot.addView(confettiOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(frameRoot)

        fun neonPanel(stroke: Int, fill: Int = Color.argb(220, 3, 17, 42), radius: Int = 18) =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = GameTheme.dp(this@MainActivity, radius).toFloat()
                setStroke(GameTheme.dp(this@MainActivity, 1), stroke)
            }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        val topMenuBtn = GameTheme.text(this, "☰ MENU", 14.5f, GameTheme.CYAN, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(GameTheme.dp(this@MainActivity, 9), GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 9), GameTheme.dp(this@MainActivity, 8))
            background = neonPanel(GameTheme.CYAN, Color.argb(220, 3, 20, 48), 12)
            setOnClickListener { GameEffects.tap(this, progress.reducedMotion); showMenuDialog() }
        }
        val brand = GameTheme.text(this, "EXIT ARROW", 22f, GameTheme.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setShadowLayer(GameTheme.dp(this@MainActivity, 8).toFloat(), 0f, 0f, GameTheme.CYAN)
        }
        if (!progress.reducedMotion) {
            GameEffects.openingLogoBounceDropIn(brand)
            GameEffects.gentleIdleWiggle(brand)
        }
        starsLabel = GameTheme.text(this, "★ ${progress.totalStars}", 15.5f, GameTheme.GOLD, true).apply {
            gravity = Gravity.CENTER
            setPadding(GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 8))
            background = neonPanel(GameTheme.GOLD, Color.argb(225, 4, 18, 42), 12)
        }
        exitLevelBtn = GameTheme.text(this, "EXIT ✕", 13.5f, GameTheme.RED, true).apply {
            gravity = Gravity.CENTER
            setPadding(GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 8), GameTheme.dp(this@MainActivity, 8))
            background = neonPanel(GameTheme.RED, Color.argb(225, 35, 5, 28), 12)
            setOnClickListener { GameEffects.tap(this, progress.reducedMotion); openLevelMapHome() }
        }
        header.addView(topMenuBtn)
        header.addView(space(GameTheme.dp(this, 5)), LinearLayout.LayoutParams(GameTheme.dp(this, 5), 1))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(starsLabel)
        header.addView(space(GameTheme.dp(this, 5)), LinearLayout.LayoutParams(GameTheme.dp(this, 5), 1))
        header.addView(exitLevelBtn)
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(screenHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        levelLabel = GameTheme.text(this, "LEVEL $level", 25f, GameTheme.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(GameTheme.dp(this@MainActivity, 20), GameTheme.dp(this@MainActivity, 5), GameTheme.dp(this@MainActivity, 20), GameTheme.dp(this@MainActivity, 5))
            background = neonPanel(Color.rgb(0, 115, 255), Color.argb(210, 4, 20, 49), 22)
            setShadowLayer(GameTheme.dp(this@MainActivity, 6).toFloat(), 0f, 0f, Color.rgb(0, 110, 255))
            setOnClickListener { GameEffects.tap(this, progress.reducedMotion); openLevelMapHome() }
        }
        val levelParams = LinearLayout.LayoutParams(-1, -2)
        levelParams.setMargins(GameTheme.dp(this, 4), GameTheme.dp(this, 8), GameTheme.dp(this, 4), GameTheme.dp(this, 8))
        contentContainer.addView(levelLabel, levelParams)

        val statsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = neonPanel(Color.rgb(0, 82, 190), Color.argb(225, 2, 17, 40), 18)
            setPadding(GameTheme.dp(this@MainActivity, 3), GameTheme.dp(this@MainActivity, 7), GameTheme.dp(this@MainActivity, 3), GameTheme.dp(this@MainActivity, 7))
        }
        fun makeStat(icon: String, initial: String, label: String, color: Int): Pair<LinearLayout, TextView> {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(GameTheme.dp(this@MainActivity, 2), GameTheme.dp(this@MainActivity, 1), GameTheme.dp(this@MainActivity, 2), GameTheme.dp(this@MainActivity, 1))
            }
            val value = GameTheme.text(this, "$icon $initial", 16f, color, true).apply { gravity = Gravity.CENTER; maxLines = 1 }
            val caption = GameTheme.text(this, label, 11f, GameTheme.WHITE, false).apply { gravity = Gravity.CENTER; maxLines = 1 }
            cell.addView(value, LinearLayout.LayoutParams(-1, -2))
            cell.addView(caption, LinearLayout.LayoutParams(-1, -2))
            return Pair(cell, value)
        }
        val timeStat = makeStat("⏱", "00:00", "TIME", GameTheme.GOLD)
        timerLabel = timeStat.second
        val livesStat = makeStat("♥", "♥ ♥ ♥", "LIVES", GameTheme.RED)
        livesStatLabel = livesStat.second
        val arrowsStat = makeStat("➜", "0", "ARROWS LEFT", GameTheme.PURPLE)
        arrowsStatLabel = arrowsStat.second
        statsLabel = GameTheme.text(this, "", 1f, GameTheme.MUTED).apply { visibility = View.GONE }
        val statParams = LinearLayout.LayoutParams(0, -2, 1f)
        statsPanel.addView(timeStat.first, statParams)
        statsPanel.addView(livesStat.first, statParams)
        statsPanel.addView(arrowsStat.first, statParams)
        contentContainer.addView(statsPanel, LinearLayout.LayoutParams(-1, -2))

        board = PuzzleBoardView(this)
        val boardParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        boardParams.topMargin = GameTheme.dp(this, 8)
        boardParams.bottomMargin = GameTheme.dp(this, 4)
        contentContainer.addView(board, boardParams)

        statusLabel = GameTheme.text(this, "Find a clear arrow.", 13f, GameTheme.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(GameTheme.dp(this@MainActivity, 4), GameTheme.dp(this@MainActivity, 3), GameTheme.dp(this@MainActivity, 4), GameTheme.dp(this@MainActivity, 3))
            maxLines = 2
        }
        contentContainer.addView(statusLabel, LinearLayout.LayoutParams(-1, -2))

        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(GameTheme.dp(this@MainActivity, 7), GameTheme.dp(this@MainActivity, 7), GameTheme.dp(this@MainActivity, 7), GameTheme.dp(this@MainActivity, 7))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 4, 16, 39))
                cornerRadius = GameTheme.dp(this@MainActivity, 22).toFloat()
                setStroke(GameTheme.dp(this@MainActivity, 1), Color.rgb(24, 54, 103))
            }
        }
        pauseButton = boxButton("Ⅱ  PAUSE", GameTheme.CYAN).apply { setOnClickListener { togglePause() } }
        hintButton = boxButton("💡 HINT (FREE)", GameTheme.GOLD).apply { setOnClickListener { showHint() } }
        if (!progress.reducedMotion) GameEffects.organicBreathingGlow(hintButton, 0.92f, 1.0f, 1800L)

        val buttonParams = LinearLayout.LayoutParams(0, GameTheme.dp(this, 54), 1f)
        bottomContainer.addView(pauseButton, buttonParams)
        bottomContainer.addView(space(GameTheme.dp(this, 8)), LinearLayout.LayoutParams(GameTheme.dp(this, 8), 1))
        bottomContainer.addView(hintButton, buttonParams)
        val bottomParams = LinearLayout.LayoutParams(-1, -2)
        bottomParams.topMargin = GameTheme.dp(this, 5)
        bottomParams.bottomMargin = GameTheme.dp(this, 3)
        contentContainer.addView(bottomContainer, bottomParams)
    }

    private fun boxButton(label: String, color: Int): Button = Button(this).apply {
        text = label
        setTextColor(if (color == GameTheme.GOLD) Color.rgb(20, 22, 34) else GameTheme.WHITE)
        textSize = 13.5f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minHeight = GameTheme.dp(this@MainActivity, 48)
        minWidth = 0
        setPadding(GameTheme.dp(this@MainActivity, 2), 0, GameTheme.dp(this@MainActivity, 2), 0)
        background = GradientDrawable().apply {
            setColor(when (color) {
                GameTheme.RED -> Color.rgb(235, 15, 85)
                GameTheme.CYAN -> Color.rgb(0, 174, 240)
                GameTheme.GOLD -> Color.rgb(255, 170, 22)
                else -> color
            })
            cornerRadius = GameTheme.dp(this@MainActivity, 17).toFloat()
            setStroke(GameTheme.dp(this@MainActivity, 1), when (color) {
                GameTheme.RED -> Color.rgb(255, 91, 151)
                GameTheme.CYAN -> Color.rgb(63, 244, 255)
                GameTheme.GOLD -> Color.rgb(255, 222, 77)
                else -> GameTheme.WHITE
            })
        }
        stateListAnimator = null
        setOnTouchListener { v, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                // Visual/audio feedback only. Do not invoke performClick() here.
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
        hintProcessing = false
        level = target.coerceAtLeast(1)
        hintsRemaining = 1

        updateTimerText()

        confettiOverlay.stop()
        board.cancelAnimations()
        board.clearHint()

        statusLabel.text = getString(R.string.preparing_level, level)
        updateUI()

        val boardW = if (board.width > 0) board.width else resources.displayMetrics.widthPixels
        val boardH = if (board.height > 0) board.height else (resources.displayMetrics.heightPixels * 0.55).toInt()
        val minCellPx = (28f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val maxResponsiveGridSize = minOf((boardW / minCellPx), (boardH / minCellPx)).coerceIn(6, 24)

        executor.execute {
            val puzzle = puzzleEngine.loadPuzzleForLevel(level, maxResponsiveGridSize)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                board.puzzle = puzzle
                totalLevelArrows = puzzle.arrows.size
                awarded50PercentHeart = false
                awarded75PercentHeart = false

                gameController.start(level)

                loading = false
                val encouragementPhrases = arrayOf(
                    "Find a clear arrow to start!",
                    "Ready? Clear the path!",
                    "You got this! Tap a free arrow!",
                    "Look closely & clear the board!",
                    "Unravel the arrows!"
                )
                statusLabel.text = encouragementPhrases[(level - 1) % encouragementPhrases.size]
                statusLabel.setTextColor(GameTheme.CYAN)

                updateUI()
                if (!progress.reducedMotion) {
                    GameEffects.boardEntranceBounce(board)
                    GameEffects.inertialBounce(levelLabel)
                }
            }
        }
    }

    private fun onArrowTapped(arrow: Arrow) {
        if (loading || paused || gameOver || finishingLevel) return
        if (!arrow.active) return

        if (!board.isClear(arrow)) {
            gameController.tapArrow(arrow.id.toString())
            board.animateBlockedArrow(arrow)
            if (!progress.reducedMotion) {
                GameEffects.shake(board)
            }
            statusLabel.text = getString(R.string.blocked_hint)
            statusLabel.setTextColor(GameTheme.RED)
            GameAudio.playBlocked(progress.soundEnabled)
            GameEffects.vibrate(this, progress.vibrationEnabled, 60)
            updateUI()

            if (lives <= 0) showGameOver()
            return
        }

        gameController.tapArrow(arrow.id.toString())
        arrow.active = false

        GameAudio.playArrowExit(progress.soundEnabled)

        val remainingActive = board.puzzle.arrows.count { it.active }

        if (remainingActive == 0) {
            statusLabel.text = getString(R.string.level_completed_title, level)
            statusLabel.setTextColor(GameTheme.GOLD)
            GameAudio.playVictory(progress.soundEnabled)
            completeLevel()
        } else {
            statusLabel.text = getString(R.string.nice_move)
            statusLabel.setTextColor(GameTheme.CYAN)
        }

        updateUI()

        board.animateArrowOut(arrow) {
            if (isFinishing || isDestroyed) return@animateArrowOut
            updateUI()
        }
    }

    private fun showHint() {
        if (hintProcessing) return
        if (loading || paused || gameOver || finishingLevel) return

        hintProcessing = true

        fun findHintArrow(): Arrow? {
            val hint = PuzzleSolver.nextHint(board.puzzle) ?: return null
            return board.puzzle.arrows.firstOrNull {
                it.id == hint.arrowId && it.active
            }
        }

        fun displayHint(arrow: Arrow, message: String) {
            hintsUsed++
            board.showHint(arrow)

            statusLabel.text = message
            statusLabel.setTextColor(GameTheme.GOLD)

            if (!progress.reducedMotion) {
                GameEffects.starSparkleBurst(hintButton)
            }

            GameAudio.playHint(progress.soundEnabled)
            GameEffects.vibrate(this, progress.vibrationEnabled, 25)
            updateUI()
        }

        // FREE HINT: no purchase dialog.
        if (hintsRemaining > 0) {
            val arrow = findHintArrow()

            if (arrow == null) {
                statusLabel.text = getString(R.string.no_solution_found)
                statusLabel.setTextColor(GameTheme.RED)
                hintProcessing = false
                return
            }

            hintsRemaining--
            displayHint(arrow, "💡 Free hint: clear arrow #${arrow.id}!")
            hintProcessing = false
            return
        }

        // PAID HINT: only this branch opens a dialog.
        if (progress.totalStars < 5) {
            Toast.makeText(
                this,
                "You need 5 stars for an extra hint.",
                Toast.LENGTH_SHORT
            ).show()
            hintProcessing = false
            return
        }

        // Do not charge for a hint that cannot be found.
        if (findHintArrow() == null) {
            statusLabel.text = getString(R.string.no_solution_found)
            statusLabel.setTextColor(GameTheme.RED)
            hintProcessing = false
            return
        }

        val purchaseDialog = AlertDialog.Builder(this)
            .setTitle("💡 Buy Extra Hint?")
            .setMessage("Exchange exactly 5 stars for one extra hint?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("BUY HINT (★ 5)", null)
            .create()

        purchaseDialog.setOnDismissListener {
            hintProcessing = false
        }

        purchaseDialog.setOnShowListener {
            purchaseDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    if (loading || paused || gameOver || finishingLevel) {
                        purchaseDialog.dismiss()
                        return@setOnClickListener
                    }

                    // Re-check immediately before charging.
                    val arrow = findHintArrow()

                    if (arrow == null) {
                        purchaseDialog.dismiss()
                        statusLabel.text = getString(R.string.no_solution_found)
                        statusLabel.setTextColor(GameTheme.RED)
                        return@setOnClickListener
                    }

                    if (progress.totalStars < 5) {
                        purchaseDialog.dismiss()
                        Toast.makeText(
                            this,
                            "Not enough stars.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    // One charge of exactly 5 stars from storage.
                    if (!storage.spendStars(5)) {
                        progress = storage.loadProgress()
                        updateUI()
                        purchaseDialog.dismiss()
                        Toast.makeText(
                            this,
                            "Not enough stars for an extra hint.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    progress = storage.loadProgress()

                    // Close purchase dialog before displaying the hint.
                    purchaseDialog.dismiss()

                    displayHint(
                        arrow,
                        "💡 Extra hint: clear arrow #${arrow.id}!"
                    )
                }
        }

        purchaseDialog.show()
    }

    private fun togglePause() {
        if (loading || finishingLevel || gameOver) return

        if (paused) {
            paused = false
            gameController.resume()
            pauseButton.text = getString(R.string.pause_button_label)
            statusLabel.text = getString(R.string.game_resumed_prompt)
            updateUI()
            return
        }

        paused = true
        gameController.pause()

        val cyan = Color.rgb(0, 225, 255)
        val navy = Color.rgb(3, 16, 39)
        val panelColor = Color.rgb(5, 28, 59)
        val white = Color.rgb(239, 247, 255)
        val muted = Color.rgb(151, 207, 235)

        fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()

        fun roundedBackground(
            fill: Int,
            stroke: Int,
            radius: Int = 18
        ) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(2), stroke)
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(navy, cyan, 24)
            clipChildren = false
        }

        val headingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(this).apply {
            text = "GAME PAUSED"
            textSize = 24f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setShadowLayer(dp(8).toFloat(), 0f, 0f, cyan)
        }

        headingRow.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = roundedBackground(panelColor, cyan, 50)
            contentDescription = "Resume game"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                paused = false
                gameController.resume()
                pauseButton.text = getString(R.string.pause_button_label)
                statusLabel.text = getString(R.string.game_resumed_prompt)
                updateUI()
            }
        }

        headingRow.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        outer.addView(
            headingRow,
            LinearLayout.LayoutParams(-1, -2)
        )

        val subtitle = TextView(this).apply {
            text = "PAUSED · PROGRESS SAVED"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }

        outer.addView(subtitle)

        val optionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addPauseOption(
            icon: String,
            title: String,
            description: String,
            onClick: () -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBackground(panelColor, Color.rgb(20, 67, 115), 18)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }

            val iconView = TextView(this).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(cyan)
                background = roundedBackground(
                    Color.rgb(4, 43, 83),
                    Color.rgb(0, 106, 190),
                    14
                )
            }

            row.addView(
                iconView,
                LinearLayout.LayoutParams(dp(44), dp(44))
            )

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(6), 0)
            }

            val titleView = TextView(this).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            val descriptionView = TextView(this).apply {
                text = description
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(2), 0, 0)
            }

            textColumn.addView(titleView)
            textColumn.addView(descriptionView)

            row.addView(
                textColumn,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            optionsContainer.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        addPauseOption(
            "▶",
            "Resume Game",
            "Continue playing level $level"
        ) {
            paused = false
            gameController.resume()
            pauseButton.text = getString(R.string.pause_button_label)
            statusLabel.text = getString(R.string.game_resumed_prompt)
            updateUI()
        }

        addPauseOption(
            "↺",
            "Restart Level",
            "Restart level $level from beginning"
        ) {
            loadLevel(level)
        }

        addPauseOption(
            "🗺️",
            "Level Map",
            "Return to level map screen"
        ) {
            openLevelMapHome()
        }

        outer.addView(optionsContainer)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(
                outer,
                FrameLayout.LayoutParams(-1, -2)
            )
        }

        val width = minOf(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            dp(420)
        )

        val cardHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            addView(
                scrollView,
                FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            )
            setOnClickListener {
                dialog.dismiss()
                paused = false
                gameController.resume()
                pauseButton.text = getString(R.string.pause_button_label)
                statusLabel.text = getString(R.string.game_resumed_prompt)
                updateUI()
            }
        }
        outer.setOnClickListener { }

        dialog.setContentView(cardHolder)

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }

    private fun completeLevel() {
        if (finishingLevel || gameOver || isFinishing || isDestroyed) return

        finishingLevel = true
        paused = true
        gameController.completeLevel()

        val completedLevel = level

        // Stars are determined by remaining hearts.
        val stars = lives.coerceIn(1, 3)

        val oldStars = progress.totalStars

        storage.saveBestMoves(completedLevel, moves)
        storage.saveBestStars(completedLevel, stars)

        // Add full reward for this completion, including replays.
        storage.addStars(stars)

        // Unlock next sequential level.
        storage.unlockLevel(completedLevel + 1)

        progress = storage.loadProgress()

        starsLabel.text = getString(R.string.stars_count_format, progress.totalStars)
        if (!progress.reducedMotion) {
            GameEffects.numberTicker(starsLabel, oldStars, progress.totalStars, prefix = "★ ")
            GameEffects.starSparkleBurst(starsLabel)
        }

        confettiOverlay.start()
        GameAudio.playVictory(progress.soundEnabled)
        GameEffects.vibrate(this, progress.vibrationEnabled, 80)

        showResponsiveLevelComplete(
            completedLevel = completedLevel,
            stars = stars,
            timeTaken = String.format(
                java.util.Locale.US,
                "%02d:%02d",
                elapsedSeconds / 60,
                elapsedSeconds % 60
            ),
            arrowsCleared = totalLevelArrows,
            onNextLevel = {
                loadLevel(completedLevel + 1)
            },
            onLevelMap = {
                openLevelMapHome()
            },
            onReplay = {
                loadLevel(completedLevel)
            }
        )
    }

    private fun showResponsiveLevelComplete(
        completedLevel: Int,
        stars: Int,
        timeTaken: String,
        arrowsCleared: Int,
        onNextLevel: () -> Unit,
        onLevelMap: () -> Unit,
        onReplay: () -> Unit
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        fun rounded(
            color: Int,
            radiusDp: Int,
            strokeColor: Int = Color.TRANSPARENT
        ) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(2), strokeColor)
            }
        }

        // Full-window root. Draw behind system bars so the overlay
        // covers the app window, including notched-screen areas.
        val screenRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            fitsSystemWindows = false
            @Suppress("DEPRECATION")
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        // Semi-transparent white mask.
        val overlay = View(this).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            isClickable = true
            isFocusable = true
        }

        screenRoot.addView(
            overlay,
            FrameLayout.LayoutParams(-1, -1)
        )

        // Panel is exactly 80% width and 70% height of current display dimensions (+5% top and bottom).
        val metrics = resources.displayMetrics
        val panelWidth = (metrics.widthPixels * 0.80f).toInt()
        val panelHeight = (metrics.heightPixels * 0.70f).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(
                Color.rgb(4, 20, 48),
                22,
                Color.rgb(0, 210, 255)
            )
            elevation = dp(12).toFloat()
        }

        val title = TextView(this).apply {
            text = getString(R.string.level_completed_title, completedLevel)
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.CYAN)
        }

        panel.addView(
            title,
            LinearLayout.LayoutParams(-1, 0, 1.0f)
        )

        val levelText = TextView(this).apply {
            text = getString(R.string.level_label_format, completedLevel)
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 210, 55))
        }

        panel.addView(
            levelText,
            LinearLayout.LayoutParams(-1, 0, 0.8f)
        )

        // Reuse the existing drawable assets.
        val banner = ImageView(this).apply {
            setImageResource(R.drawable.banner)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Level completed banner"
        }

        panel.addView(
            banner,
            LinearLayout.LayoutParams(-1, 0, 1.7f)
        )

        val starRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val starSize = dp(44)
        val starMargin = dp(6)

        for (i in 1..3) {
            val starImg = ImageView(this).apply {
                setImageResource(R.drawable.star1)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                alpha = if (i <= stars) 1.0f else 0.25f
            }
            starRow.addView(
                starImg,
                LinearLayout.LayoutParams(starSize, starSize).apply {
                    leftMargin = starMargin
                    rightMargin = starMargin
                }
            )
        }

        panel.addView(
            starRow,
            LinearLayout.LayoutParams(-1, 0, 1.1f)
        )

        val stats = TextView(this).apply {
            text = "TIME     $timeTaken\n" +
                    "ARROWS   $arrowsCleared"
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(5).toFloat(), 1f)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(7, 38, 78), 14)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        panel.addView(
            stats,
            LinearLayout.LayoutParams(-1, 0, 1.2f)
        )

        fun addActionButton(
            label: String,
            color: Int,
            action: () -> Unit
        ) {
            val button = Button(this).apply {
                text = label
                textSize = 20f
                isAllCaps = false
                typeface = Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )
                setTextColor(Color.rgb(0, 20, 40))
                background = rounded(color, 14)

                // Keep the label and symbol comfortably inside the button.
                gravity = Gravity.CENTER
                minHeight = dp(48)
                minimumHeight = dp(48)
                setPadding(dp(8), dp(5), dp(8), dp(5))
                includeFontPadding = true

                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }

            panel.addView(
                button,
                LinearLayout.LayoutParams(
                    -1,
                    0,
                    1f
                ).apply {
                    topMargin = dp(5)
                    bottomMargin = dp(5)
                }
            )
        }

        addActionButton("▶  NEXT LEVEL", Color.rgb(255, 205, 55), onNextLevel)
        addActionButton("◎  LEVEL MAP", Color.rgb(45, 210, 225), onLevelMap)
        addActionButton("↻  REPLAY LEVEL", Color.WHITE, onReplay)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(
                panel,
                FrameLayout.LayoutParams(panelWidth, panelHeight)
            )
        }

        screenRoot.addView(
            scrollView,
            FrameLayout.LayoutParams(
                panelWidth,
                panelHeight,
                Gravity.CENTER
            )
        )

        dialog.setContentView(screenRoot)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, -1)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
            }
        }

        dialog.show()

        // Reapply full-window dimensions after show().
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
                if (gameController.spendStars(10)) {
                    gameController.grantBonusLife()
                    val oldStars = progress.totalStars
                    progress = storage.loadProgress()
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGameOver() {
        gameOver = true
        paused = true

        val cyan = Color.rgb(0, 225, 255)
        val navy = Color.rgb(3, 16, 39)
        val panelColor = Color.rgb(5, 28, 59)
        val white = Color.rgb(239, 247, 255)
        val muted = Color.rgb(151, 207, 235)

        fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()

        fun roundedBackground(fill: Int, stroke: Int, radius: Int = 18) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(2), stroke)
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(navy, Color.rgb(255, 75, 65), 24)
            clipChildren = false
        }

        val heading = TextView(this).apply {
            text = "💔 OUT OF HEARTS!"
            textSize = 22f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.rgb(255, 75, 65))
        }

        outer.addView(heading, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = "LEVEL $level FAILED · STARS: ★ ${progress.totalStars}"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 205, 55))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }

        outer.addView(subtitle)

        val optionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addOption(icon: String, title: String, description: String, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBackground(panelColor, Color.rgb(20, 67, 115), 18)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }

            val iconView = TextView(this).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(cyan)
                background = roundedBackground(Color.rgb(4, 43, 83), Color.rgb(0, 106, 190), 14)
            }

            row.addView(iconView, LinearLayout.LayoutParams(dp(44), dp(44)))

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(6), 0)
            }

            val titleView = TextView(this).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            val descriptionView = TextView(this).apply {
                text = description
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(2), 0, 0)
            }

            textColumn.addView(titleView)
            textColumn.addView(descriptionView)

            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

            optionsContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }

        if (progress.totalStars >= 10) {
            addOption("❤️", "Buy 1 Heart (★ 10)", "Exchange 10 stars to get 1 heart & continue") {
                if (gameController.spendStars(10)) {
                    gameController.grantBonusLife()
                    progress = storage.loadProgress()
                    gameOver = false
                    paused = false
                    updateUI()
                    GameAudio.playVictory(progress.soundEnabled)
                    Toast.makeText(this, "❤️ Purchased 1 Heart!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        addOption("↺", "Restart Level", "Restart level $level from beginning") {
            loadLevel(level)
        }

        addOption("🗺️", "Level Map", "Return to level map screen") {
            openLevelMapHome()
        }

        outer.addView(optionsContainer)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(outer, FrameLayout.LayoutParams(-1, -2))
        }

        val width = minOf((resources.displayMetrics.widthPixels * 0.88f).toInt(), dp(420))

        val cardHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            addView(scrollView, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

        dialog.setContentView(cardHolder)
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }

    private fun showMenuDialog() {
        val cyan = Color.rgb(0, 225, 255)
        val navy = Color.rgb(3, 16, 39)
        val panelColor = Color.rgb(5, 28, 59)
        val white = Color.rgb(239, 247, 255)
        val muted = Color.rgb(151, 207, 235)

        fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()

        fun roundedBackground(
            fill: Int,
            stroke: Int,
            radius: Int = 18
        ) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(2), stroke)
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(navy, cyan, 24)
            clipChildren = false
        }

        val headingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(this).apply {
            text = "MENU"
            textSize = 24f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setShadowLayer(dp(8).toFloat(), 0f, 0f, cyan)
        }

        headingRow.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = roundedBackground(panelColor, cyan, 50)
            contentDescription = "Close menu"
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }

        headingRow.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        outer.addView(
            headingRow,
            LinearLayout.LayoutParams(-1, -2)
        )

        val subtitle = TextView(this).apply {
            text = "GAME OPTIONS"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }

        outer.addView(subtitle)

        val optionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addMenuOption(
            icon: String,
            title: String,
            description: String,
            onClick: () -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBackground(panelColor, Color.rgb(20, 67, 115), 18)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }

            val iconView = TextView(this).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(cyan)
                background = roundedBackground(
                    Color.rgb(4, 43, 83),
                    Color.rgb(0, 106, 190),
                    14
                )
            }

            row.addView(
                iconView,
                LinearLayout.LayoutParams(dp(44), dp(44))
            )

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(6), 0)
            }

            val titleView = TextView(this).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            val descriptionView = TextView(this).apply {
                text = description
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(2), 0, 0)
            }

            textColumn.addView(titleView)
            textColumn.addView(descriptionView)

            row.addView(
                textColumn,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            optionsContainer.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        addMenuOption(
            "🗺️",
            "Level Map",
            "Select and play unlocked levels"
        ) {
            openLevelMapHome()
        }

        addMenuOption(
            "⚙️",
            "Settings",
            "Audio, vibration & animation preferences"
        ) {
            showSettingsDialog()
        }

        addMenuOption(
            "↺",
            "Restart Level",
            "Restart current puzzle"
        ) {
            loadLevel(level)
        }

        outer.addView(optionsContainer)

        val doneButton = TextView(this).apply {
            text = "CLOSE"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(0, 22, 45))
            gravity = Gravity.CENTER
            background = roundedBackground(cyan, cyan, 18)
            isClickable = true
            isFocusable = true
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { dialog.dismiss() }
        }

        outer.addView(
            doneButton,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
            }
        )

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(
                outer,
                FrameLayout.LayoutParams(-1, -2)
            )
        }

        val width = minOf(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            dp(420)
        )

        val cardHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            addView(
                scrollView,
                FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            )
            setOnClickListener { dialog.dismiss() }
        }
        outer.setOnClickListener { }

        dialog.setContentView(cardHolder)

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }

    private fun showCandyCrushLevelMapDialog() {
        finishingLevel = false
        loading = false
        paused = true

        var dialog: AlertDialog? = null
        val totalMapLevels = 100

        val mapView = CandyCrushLevelMapView(
            this,
            totalLevels = totalMapLevels,
            unlockedLevel = progress.unlockedLevel,
            currentLevel = level,
            getBestStars = { levelNumber ->
                storage.bestStars(levelNumber)
            },
            onLevelSelected = { selectedLevel ->
                dialog?.dismiss()
                loadLevel(selectedLevel)
            },
            onLockedLevelTapped = { lockedLevel ->
                GameAudio.playBlocked(progress.soundEnabled)

                Toast.makeText(
                    this,
                    "🔒 Complete Level ${lockedLevel - 1} to unlock Level $lockedLevel!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(3, 10, 31))
            isFillViewport = true
            addView(mapView)
        }

        dialog = AlertDialog.Builder(
            this,
            android.R.style.Theme_DeviceDefault_Dialog
        )
            .setView(scroll)
            .setNegativeButton("CLOSE") { _, _ ->
                if (board.puzzle.arrows.isEmpty()) {
                    loadLevel(level)
                }
            }
            .create()

        dialog.show()

        val metrics = resources.displayMetrics
        dialog.window?.setLayout(
            (metrics.widthPixels * 0.94f).toInt(),
            (metrics.heightPixels * 0.88f).toInt()
        )
        dialog.window?.setBackgroundDrawable(
            Color.rgb(3, 10, 31).toDrawable()
        )
    }

    private fun showSettingsDialog() {
        val cyan = Color.rgb(0, 225, 255)
        val navy = Color.rgb(3, 16, 39)
        val panelColor = Color.rgb(5, 28, 59)
        val white = Color.rgb(239, 247, 255)
        val muted = Color.rgb(151, 207, 235)

        fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()

        fun roundedBackground(
            fill: Int,
            stroke: Int,
            radius: Int = 18
        ) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(2), stroke)
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(navy, cyan, 24)
            clipChildren = false
        }

        val headingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setShadowLayer(dp(8).toFloat(), 0f, 0f, cyan)
        }

        headingRow.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = roundedBackground(panelColor, cyan, 50)
            contentDescription = "Close settings"
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }

        headingRow.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        outer.addView(
            headingRow,
            LinearLayout.LayoutParams(-1, -2)
        )

        val subtitle = TextView(this).apply {
            text = getString(R.string.settings_subtitle)
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }

        outer.addView(subtitle)

        val optionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addSetting(
            icon: String,
            title: String,
            description: String,
            initiallyEnabled: Boolean,
            onChanged: (Boolean) -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBackground(panelColor, Color.rgb(20, 67, 115), 18)
            }

            val iconView = TextView(this).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(cyan)
                background = roundedBackground(
                    Color.rgb(4, 43, 83),
                    Color.rgb(0, 106, 190),
                    14
                )
            }

            row.addView(
                iconView,
                LinearLayout.LayoutParams(dp(44), dp(44))
            )

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
            }

            val titleView = TextView(this).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            val descriptionView = TextView(this).apply {
                text = description
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(2), 0, 0)
            }

            textColumn.addView(titleView)
            textColumn.addView(descriptionView)

            row.addView(
                textColumn,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val toggle = Switch(this).apply {
                isChecked = initiallyEnabled
                text = ""
                contentDescription = title
                thumbTintList = android.content.res.ColorStateList.valueOf(
                    if (initiallyEnabled) Color.WHITE else muted
                )
                trackTintList = android.content.res.ColorStateList.valueOf(
                    if (initiallyEnabled) cyan
                    else Color.rgb(65, 82, 104)
                )
                setOnCheckedChangeListener { _, checked ->
                    thumbTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (checked) Color.WHITE else muted
                        )
                    trackTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (checked) cyan
                            else Color.rgb(65, 82, 104)
                        )
                    onChanged(checked)
                }
            }

            row.addView(toggle)

            row.setOnClickListener {
                toggle.isChecked = !toggle.isChecked
            }

            optionsContainer.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        addSetting(
            "🔊",
            "Sound Effects",
            "Play game sounds",
            progress.soundEnabled
        ) { enabled ->
            progress = progress.copy(soundEnabled = enabled)
            storage.saveProgress(progress)
            gameController.setSoundEnabled(enabled)
        }

        addSetting(
            "📳",
            "Vibration",
            "Vibrate on game actions",
            progress.vibrationEnabled
        ) { enabled ->
            progress = progress.copy(vibrationEnabled = enabled)
            storage.saveProgress(progress)
            gameController.setVibrationEnabled(enabled)
        }

        addSetting(
            "✨",
            "Reduced Motion",
            "Reduce decorative animations",
            progress.reducedMotion
        ) { enabled ->
            progress = progress.copy(reducedMotion = enabled)
            storage.saveProgress(progress)
            gameController.setReducedMotion(enabled)
        }

        outer.addView(optionsContainer)

        val doneButton = TextView(this).apply {
            text = getString(R.string.settings_done_btn)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(0, 22, 45))
            gravity = Gravity.CENTER
            background = roundedBackground(cyan, cyan, 18)
            isClickable = true
            isFocusable = true
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { dialog.dismiss() }
        }

        outer.addView(
            doneButton,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
            }
        )

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(
                outer,
                FrameLayout.LayoutParams(-1, -2)
            )
        }

        val width = minOf(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            dp(420)
        )

        val cardHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            addView(
                scrollView,
                FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            )
            setOnClickListener { dialog.dismiss() }
        }
        outer.setOnClickListener { }

        dialog.setContentView(cardHolder)

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }

    private fun updateUI() {
        if (!::statsLabel.isInitialized) return

        levelLabel.text = getString(R.string.level_label_format, level)
        val remaining = if (::board.isInitialized) {
            board.puzzle.arrows.count { it.active }
        } else {
            0
        }
        if (::movesStatLabel.isInitialized) movesStatLabel.text = getString(R.string.moves_stat_format, moves)
        if (::livesStatLabel.isInitialized) livesStatLabel.text = getString(R.string.lives_stat_format, "♥ ".repeat(lives.coerceIn(0, 3)).trim() + if (lives <= 0) "♡" else "")
        if (::arrowsStatLabel.isInitialized) arrowsStatLabel.text = getString(R.string.arrows_stat_format, remaining)
        updateTimerText()

        pauseButton.text = if (paused) "▶ RESUME" else "Ⅱ PAUSE"
        pauseButton.isEnabled = !loading && !finishingLevel && !gameOver
        hintButton.text = if (hintsRemaining > 0) "💡 HINT (FREE)" else "💡 HINT (★ 5)"
        hintButton.textSize = 13.5f
        hintButton.isEnabled = !loading && !paused && !gameOver && !finishingLevel
        starsLabel.text = getString(R.string.stars_count_format, progress.totalStars)
    }

    override fun onStop() {
        if (::gameController.isInitialized) {
            gameController.onAppBackgrounded()
        }
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::mapContainer.isInitialized && ::root.isInitialized && root.visibility == View.VISIBLE) {
            openLevelMapHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::gameController.isInitialized) {
            gameController.close()
        }
        if (::board.isInitialized) board.cancelAnimations()
        GameAudio.release()
        executor.shutdownNow()
        super.onDestroy()
    }
}