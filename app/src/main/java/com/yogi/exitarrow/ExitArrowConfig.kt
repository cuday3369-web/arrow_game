package com.yogi.exitarrow

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

/** Reads tunable intro values from app/src/main/assets/exit_arrow_config.json. */
internal class ExitArrowConfig private constructor(private val root: JSONObject) {
    private val animation = root.optJSONObject("animation") ?: JSONObject()
    private val visual = root.optJSONObject("visual") ?: JSONObject()
    private val paletteJson = root.optJSONArray("palette")

    val durationMs: Long
        get() = animation.optLong("durationMs", 7200L).coerceAtLeast(1000L)

    val spiralTurns: Float
        get() = animation.optDouble("spiralTurns", 1.15)
            .toFloat().coerceIn(0.25f, 3.0f)

    val particleCount: Int
        get() = visual.optInt("particleCount", 230).coerceIn(0, 800)

    val safeMarginFraction: Float
        get() = visual.optDouble("safeMarginFraction", 0.045).toFloat()

    val centerYFraction: Float
        get() = visual.optDouble("centerYFraction", 0.425).toFloat()

    val titleYFraction: Float
        get() = visual.optDouble("titleYFraction", 0.72).toFloat()

    val arrowHeadSizeFraction: Float
        get() = visual.optDouble("arrowHeadSizeFraction", 0.060).toFloat()

    val shaftWidthFraction: Float
        get() = visual.optDouble("shaftWidthFraction", 0.0085).toFloat()

    val glowWidthFraction: Float
        get() = visual.optDouble("glowWidthFraction", 0.030).toFloat()

    fun colors(): IntArray {
        if (paletteJson == null || paletteJson.length() == 0) {
            return DEFAULT_PALETTE.copyOf()
        }

        return IntArray(paletteJson.length()) { i ->
            try {
                Color.parseColor(paletteJson.getString(i))
            } catch (_: Exception) {
                DEFAULT_PALETTE[i % DEFAULT_PALETTE.size]
            }
        }
    }

    companion object {
        private val DEFAULT_PALETTE = intArrayOf(
            Color.rgb(255, 0, 0),
            Color.rgb(255, 127, 0),
            Color.rgb(255, 255, 0),
            Color.rgb(0, 255, 0),
            Color.rgb(0, 191, 255),
            Color.rgb(139, 0, 255)
        )

        fun load(context: Context): ExitArrowConfig {
            return try {
                val json = context.assets
                    .open("exit_arrow_config.json")
                    .bufferedReader()
                    .use { it.readText() }

                ExitArrowConfig(JSONObject(json))
            } catch (_: Exception) {
                ExitArrowConfig(JSONObject())
            }
        }
    }
}