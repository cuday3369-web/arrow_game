package com.yogi.exitarrow

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GameOverDialog(
    private val activity: Activity,
    private val level: Int,
    private val totalStars: Int,
    private val canBuyHeart: Boolean,
    private val onBuyHeart: () -> Unit,
    private val onAdHeart: () -> Unit,
    private val onRestart: () -> Unit,
    private val onLevelMap: () -> Unit
) {
    fun show() {
        val cyan = Color.rgb(0, 225, 255)
        val navy = Color.rgb(3, 16, 39)
        val panelColor = Color.rgb(5, 28, 59)
        val white = Color.rgb(239, 247, 255)
        val muted = Color.rgb(151, 207, 235)

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
        dialog.setCancelable(false)

        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(
                navy,
                Color.rgb(255, 75, 65),
                24
            )
            clipChildren = false
        }

        val heading = TextView(activity).apply {
            text = "💔 OUT OF HEARTS!"
            textSize = 22f
            setTextColor(white)
            typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD_ITALIC
            )
            gravity = Gravity.CENTER
            setShadowLayer(
                dp(8).toFloat(),
                0f,
                0f,
                Color.rgb(255, 75, 65)
            )
        }

        outer.addView(
            heading,
            LinearLayout.LayoutParams(-1, -2)
        )

        val subtitle = TextView(activity).apply {
            text = "LEVEL $level FAILED · STARS: ★ $totalStars"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 205, 55))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }

        outer.addView(subtitle)

        val optionsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addOption(
            icon: String,
            title: String,
            description: String,
            fillColor: Int = panelColor,
            strokeColor: Int = Color.rgb(20, 67, 115),
            iconBg: Int = Color.rgb(4, 43, 83),
            iconBorder: Int = Color.rgb(0, 106, 190),
            onClick: () -> Unit
        ) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBackground(
                    fillColor,
                    strokeColor,
                    18
                )
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }

            val iconView = TextView(activity).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(cyan)
                background = roundedBackground(
                    iconBg,
                    iconBorder,
                    14
                )
            }

            row.addView(
                iconView,
                LinearLayout.LayoutParams(dp(44), dp(44))
            )

            val textColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(6), 0)
            }

            val titleView = TextView(activity).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            val descriptionView = TextView(activity).apply {
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

        // Light dark red styled option for Ad Video Heart
        addOption(
            icon = "🎬",
            title = "Watch Ad for 1 Heart",
            description = "Watch a short video ad to get 1 heart & continue",
            fillColor = Color.rgb(110, 20, 38),
            strokeColor = Color.rgb(220, 55, 75),
            iconBg = Color.rgb(75, 10, 22),
            iconBorder = Color.rgb(255, 80, 100),
            onClick = onAdHeart
        )

        if (canBuyHeart) {
            addOption(
                icon = "❤️",
                title = "Buy 1 Heart (★ 10)",
                description = "Exchange 10 stars to get 1 heart & continue",
                onClick = onBuyHeart
            )
        }

        addOption(
            icon = "↺",
            title = "Restart Level",
            description = "Restart level $level from beginning",
            onClick = onRestart
        )

        addOption(
            icon = "🗺️",
            title = "Level Map",
            description = "Return to level map screen",
            onClick = onLevelMap
        )

        outer.addView(optionsContainer)

        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(
                outer,
                FrameLayout.LayoutParams(-1, -2)
            )
        }

        val width = minOf(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            dp(420)
        )

        val cardHolder = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    width,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        dialog.setContentView(cardHolder)

        dialog.window?.apply {
            setBackgroundDrawableResource(
                android.R.color.transparent
            )
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }
}
