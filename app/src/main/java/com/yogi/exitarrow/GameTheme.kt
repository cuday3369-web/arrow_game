package com.yogi.exitarrow

import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.widget.TextView
import android.graphics.Typeface

object GameTheme {
    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    val BACKGROUND = rgb(9, 14, 29)
    val PANEL = rgb(20, 29, 49)
    val PANEL_LIGHT = rgb(34, 46, 72)
    val CYAN = rgb(66, 232, 255)
    val PURPLE = rgb(155, 110, 255)
    val GOLD = rgb(255, 207, 91)
    val WHITE = rgb(245, 248, 255)
    val MUTED = rgb(151, 166, 193)
    val RED = rgb(255, 86, 120)
    val GREEN = rgb(95, 240, 177)

    val ARROW_COLORS = intArrayOf(
        CYAN,
        PURPLE,
        GOLD,
        GREEN,
        rgb(255, 115, 160)
    )

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(context: Context, color: Int, radiusDp: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    fun text(
        context: Context,
        value: String,
        size: Float,
        color: Int = WHITE,
        bold: Boolean = false
    ) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(
            "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
    }
}
