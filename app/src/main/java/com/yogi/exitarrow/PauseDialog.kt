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

class PauseDialog(
    private val activity: Activity,
    private val level: Int,
    private val onResume: () -> Unit,
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

        fun resumeAndDismiss() {
            if (dialog.isShowing) dialog.dismiss()
            onResume()
        }

        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(navy, cyan, 24)
            clipChildren = false
        }

        val headingRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(activity).apply {
            text = "GAME PAUSED"
            textSize = 24f
            setTextColor(white)
            typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD_ITALIC
            )
            gravity = Gravity.CENTER
            setShadowLayer(dp(8).toFloat(), 0f, 0f, cyan)
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
            background = roundedBackground(panelColor, cyan, 50)
            contentDescription = "Resume game"
            isClickable = true
            isFocusable = true
            setOnClickListener { resumeAndDismiss() }
        }

        headingRow.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        outer.addView(
            headingRow,
            LinearLayout.LayoutParams(-1, -2)
        )

        val subtitle = TextView(activity).apply {
            text = "PAUSED · PROGRESS SAVED"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }

        outer.addView(subtitle)

        val optionsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addPauseOption(
            icon: String,
            title: String,
            description: String,
            onClick: () -> Unit
        ) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedBackground(
                    panelColor,
                    Color.rgb(20, 67, 115),
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
                    Color.rgb(4, 43, 83),
                    Color.rgb(0, 106, 190),
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

        addPauseOption(
            "▶",
            "Resume Game",
            "Continue playing level $level",
            onResume
        )

        addPauseOption(
            "↺",
            "Restart Level",
            "Restart level $level from beginning",
            onRestart
        )

        addPauseOption(
            "🗺️",
            "Level Map",
            "Return to level map screen",
            onLevelMap
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
            setOnClickListener {
                resumeAndDismiss()
            }
        }

        // Tapping inside the panel must not trigger the outside-tap action.
        outer.setOnClickListener { }

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
