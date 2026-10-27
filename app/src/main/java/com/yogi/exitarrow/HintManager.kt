package com.yogi.exitarrow

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Owns the free-hint, paid-hint, and ad-rewarded-hint flows.
 * MainActivity continues to own the game state and UI.
 */
class HintManager(
    private val activity: Activity,
    private val board: PuzzleBoardView,
    private val storage: GameStorage,
    private val getProgress: () -> GameProgress,
    private val setProgress: (GameProgress) -> Unit,
    private val getHintsRemaining: () -> Int,
    private val setHintsRemaining: (Int) -> Unit,
    private val getHintProcessing: () -> Boolean,
    private val setHintProcessing: (Boolean) -> Unit,
    private val isGameBusy: () -> Boolean,
    private val statusLabel: TextView,
    private val starsLabel: TextView,
    private val hintButton: Button,
    private val updateUI: () -> Unit,
    private val onHintUsed: () -> Unit
) {

    fun showHint() {
        if (getHintProcessing()) return
        if (isGameBusy()) return

        setHintProcessing(true)

        fun releaseHintLock() {
            setHintProcessing(false)
        }

        fun findHintArrow(): Arrow? {
            val hint = PuzzleSolver.nextHint(board.puzzle) ?: return null

            return board.puzzle.arrows.firstOrNull { arrow ->
                arrow.id == hint.arrowId &&
                    arrow.active &&
                    !board.isArrowAnimating(arrow) &&
                    board.isClear(arrow)
            }
        }

        fun showUnavailableHint() {
            statusLabel.text =
                activity.getString(R.string.no_solution_found)
            statusLabel.setTextColor(GameTheme.RED)
        }

        fun displayHint(arrow: Arrow, message: String) {
            onHintUsed()
            board.showHint(arrow)

            statusLabel.text = message
            statusLabel.setTextColor(GameTheme.GOLD)

            val progress = getProgress()

            if (!progress.reducedMotion) {
                GameEffects.starSparkleBurst(hintButton)
            }

            GameAudio.playHint(progress.soundEnabled)
            GameEffects.vibrate(
                activity,
                progress.vibrationEnabled,
                25
            )

            updateUI()
        }

        // FREE HINT: never opens a purchase dialog.
        if (getHintsRemaining() > 0) {
            val arrow = findHintArrow()

            if (arrow == null) {
                showUnavailableHint()
                releaseHintLock()
                return
            }

            setHintsRemaining(getHintsRemaining() - 1)

            displayHint(
                arrow,
                "💡 Free hint: clear arrow #${arrow.id}!"
            )

            releaseHintLock()
            return
        }

        // PAID HINT: check the balance before opening the dialog.
        if (getProgress().totalStars < 5) {
            Toast.makeText(
                activity,
                "You need 5 stars for an extra hint.",
                Toast.LENGTH_SHORT
            ).show()

            releaseHintLock()
            return
        }

        // Never charge if no valid hint is available.
        if (findHintArrow() == null) {
            showUnavailableHint()
            releaseHintLock()
            return
        }

        showSettingsStyledPurchaseDialog(
            onConfirmPurchase = { dialog ->
                if (isGameBusy()) {
                    dialog.dismiss()
                    return@showSettingsStyledPurchaseDialog
                }

                val arrow = findHintArrow()

                if (arrow == null) {
                    dialog.dismiss()
                    showUnavailableHint()
                    return@showSettingsStyledPurchaseDialog
                }

                if (getProgress().totalStars < 5) {
                    dialog.dismiss()

                    Toast.makeText(
                        activity,
                        "Not enough stars.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@showSettingsStyledPurchaseDialog
                }

                // Deduct exactly 5 stars, once.
                if (!storage.spendStars(5)) {
                    setProgress(storage.loadProgress())
                    updateUI()
                    dialog.dismiss()

                    Toast.makeText(
                        activity,
                        "Not enough stars for an extra hint.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@showSettingsStyledPurchaseDialog
                }

                // Refresh the displayed balance from saved storage.
                setProgress(storage.loadProgress())

                starsLabel.text = activity.getString(
                    R.string.stars_count_format,
                    getProgress().totalStars
                )

                updateUI()
                dialog.dismiss()

                displayHint(
                    arrow,
                    "💡 Extra hint: clear arrow #${arrow.id}!"
                )
            },
            onDismiss = {
                releaseHintLock()
            }
        )
    }

    /** Small video icon hint button action (Ad Rewarded Hint). */
    fun showAdHint() {
        if (getHintProcessing()) return
        if (isGameBusy()) return

        setHintProcessing(true)

        val hint = PuzzleSolver.nextHint(board.puzzle)

        val arrow = if (hint != null) {
            board.puzzle.arrows.firstOrNull { a ->
                a.id == hint.arrowId &&
                    a.active &&
                    !board.isArrowAnimating(a) &&
                    board.isClear(a)
            }
        } else null

        if (arrow == null) {
            statusLabel.text = activity.getString(R.string.no_solution_found)
            statusLabel.setTextColor(GameTheme.RED)
            setHintProcessing(false)
            return
        }

        Toast.makeText(
            activity,
            "🎬 Ad Reward: +1 Free Hint!",
            Toast.LENGTH_SHORT
        ).show()

        onHintUsed()
        board.showHint(arrow)

        statusLabel.text = "🎬 Ad Hint: clear arrow #${arrow.id}!"
        statusLabel.setTextColor(GameTheme.CYAN)

        val progress = getProgress()

        if (!progress.reducedMotion) {
            GameEffects.starSparkleBurst(hintButton)
        }

        GameAudio.playHint(progress.soundEnabled)
        GameEffects.vibrate(
            activity,
            progress.vibrationEnabled,
            25
        )

        updateUI()
        setHintProcessing(false)
    }

    private fun showSettingsStyledPurchaseDialog(
        onConfirmPurchase: (Dialog) -> Unit,
        onDismiss: () -> Unit
    ) {
        val navy = Color.rgb(3, 16, 39)
        val panelColor = Color.rgb(5, 28, 59)
        val white = Color.rgb(239, 247, 255)
        val muted = Color.rgb(151, 207, 235)
        val gold = Color.rgb(255, 207, 91)

        fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()

        fun roundedBackground(
            fill: Int,
            stroke: Int,
            radius: Int = 18
        ) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(2), stroke)
        }

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        dialog.setOnDismissListener {
            onDismiss()
        }

        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(navy, gold, 24)
            clipChildren = false
        }

        val headingRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(activity).apply {
            text = "💡 BUY EXTRA HINT"
            textSize = 22f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setShadowLayer(dp(8).toFloat(), 0f, 0f, gold)
        }

        headingRow.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        val closeButton = TextView(activity).apply {
            text = "✕"
            textSize = 20f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = roundedBackground(panelColor, gold, 50)
            contentDescription = "Close hint dialog"
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }

        headingRow.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        outer.addView(headingRow, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(activity).apply {
            text = "EXCHANGE ★ 5 STARS OR WATCH AN AD FOR 1 HINT"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }

        outer.addView(subtitle)

        val infoCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(panelColor, Color.rgb(20, 67, 115), 18)
        }

        val starIcon = TextView(activity).apply {
            text = "★"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(gold)
            background = roundedBackground(Color.rgb(4, 43, 83), Color.rgb(0, 106, 190), 14)
        }

        infoCard.addView(starIcon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }

        val currentStarsTitle = TextView(activity).apply {
            text = "YOUR STARS: ★ ${getProgress().totalStars}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        }

        val costDesc = TextView(activity).apply {
            text = "Exchange 5 stars or watch a short video ad"
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        }

        textColumn.addView(currentStarsTitle)
        textColumn.addView(costDesc)

        infoCard.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        outer.addView(infoCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val buyButton = TextView(activity).apply {
            text = "BUY HINT (★ 5)"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 22, 34))
            gravity = Gravity.CENTER
            background = roundedBackground(gold, gold, 18)
            isClickable = true
            isFocusable = true
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                onConfirmPurchase(dialog)
            }
        }

        outer.addView(buyButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val adButton = TextView(activity).apply {
            text = "🎬 WATCH AD (FREE HINT)"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(110, 50, 210), Color.rgb(155, 90, 255), 18)
            isClickable = true
            isFocusable = true
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                dialog.dismiss()
                showAdHint()
            }
        }

        outer.addView(adButton, LinearLayout.LayoutParams(-1, -2))

        val width = minOf((activity.resources.displayMetrics.widthPixels * 0.88f).toInt(), dp(420))

        val cardHolder = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            addView(outer, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            setOnClickListener { dialog.dismiss() }
        }
        outer.setOnClickListener { }

        dialog.setContentView(cardHolder)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }
}
