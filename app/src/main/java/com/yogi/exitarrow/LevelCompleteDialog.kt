package com.yogi.exitarrow

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LevelCompleteDialog(
    private val activity: Activity
) {

    fun show(
        completedLevel: Int,
        stars: Int,
        timeTaken: String,
        arrowsCleared: Int,
        onNextLevel: () -> Unit,
        onLevelMap: () -> Unit,
        onReplay: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val density = activity.resources.displayMetrics.density
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

        val screenRoot = FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            fitsSystemWindows = false

            @Suppress("DEPRECATION")
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        val overlay = View(activity).apply {
            setBackgroundColor(Color.argb(105, 245, 248, 255))
            isClickable = true
            isFocusable = true
        }

        screenRoot.addView(
            overlay,
            FrameLayout.LayoutParams(-1, -1)
        )

        val metrics = activity.resources.displayMetrics
        val panelWidth = (metrics.widthPixels * 0.80f).toInt()
        val panelHeight = (metrics.heightPixels * 0.70f).toInt()

        val panel = LinearLayout(activity).apply {
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

        val title = TextView(activity).apply {
            text = activity.getString(
                R.string.level_completed_title,
                completedLevel
            )
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

        val levelText = TextView(activity).apply {
            text = activity.getString(
                R.string.level_label_format,
                completedLevel
            )
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 210, 55))
        }

        panel.addView(
            levelText,
            LinearLayout.LayoutParams(-1, 0, 0.8f)
        )

        val banner = ImageView(activity).apply {
            setImageResource(R.drawable.banner)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Level completed banner"
        }

        panel.addView(
            banner,
            LinearLayout.LayoutParams(-1, 0, 1.7f)
        )

        val starRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val starSize = dp(44)
        val starMargin = dp(6)

        for (i in 1..3) {
            val starImage = ImageView(activity).apply {
                setImageResource(R.drawable.star1)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                alpha = if (i <= stars) 1.0f else 0.25f
            }

            starRow.addView(
                starImage,
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

        val stats = TextView(activity).apply {
            text = "TIME     $timeTaken\nARROWS   $arrowsCleared"
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
            val button = Button(activity).apply {
                text = label
                textSize = 20f
                isAllCaps = false
                typeface = Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )
                setTextColor(Color.rgb(0, 20, 40))
                background = rounded(color, 14)
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
                LinearLayout.LayoutParams(-1, 0, 1f).apply {
                    topMargin = dp(5)
                    bottomMargin = dp(5)
                }
            )
        }

        addActionButton(
            "▶  NEXT LEVEL",
            Color.rgb(255, 205, 55),
            onNextLevel
        )

        addActionButton(
            "◎  LEVEL MAP",
            Color.rgb(45, 210, 225),
            onLevelMap
        )

        addActionButton(
            "↻  REPLAY LEVEL",
            Color.WHITE,
            onReplay
        )

        val scrollView = ScrollView(activity).apply {
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
            setBackgroundDrawableResource(
                android.R.color.transparent
            )
            setLayout(-1, -1)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
            }
        }

        dialog.show()
    }
}
