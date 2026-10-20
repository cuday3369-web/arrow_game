package com.yogi.exitarrow.game

import androidx.compose.ui.graphics.Color
import com.yogi.exitarrow.Direction
import org.json.JSONObject

object JsonLevelLoader {

    fun parseLevelFromJson(jsonString: String): LightningPuzzle {
        val root = JSONObject(jsonString)
        val level = root.optInt("level", 1)
        val rows = root.optInt("rows", 10)
        val cols = root.optInt("cols", 10)

        val jsonArrows = root.getJSONArray("arrows")
        val arrows = mutableListOf<LightningArrow>()

        for (i in 0 until jsonArrows.length()) {
            val arrowObj = jsonArrows.getJSONObject(i)
            val id = arrowObj.optInt("id", i + 1)
            val dirString = arrowObj.optString("direction", "UP")
            val colorHex = arrowObj.optString("color", "#42E8FF")

            val direction = try {
                Direction.valueOf(dirString)
            } catch (_: Exception) {
                Direction.UP
            }

            val parsedColor = try {
                Color(android.graphics.Color.parseColor(colorHex))
            } catch (_: Exception) {
                Color(0xFF42E8FF)
            }

            val pathArray = arrowObj.getJSONArray("path")
            val path = mutableListOf<Pair<Int, Int>>()

            for (j in 0 until pathArray.length()) {
                val ptObj = pathArray.getJSONObject(j)
                val r = ptObj.optInt("row", 0)
                val c = ptObj.optInt("col", 0)
                path.add(Pair(r, c))
            }

            arrows.add(
                LightningArrow(
                    id = id,
                    path = path,
                    direction = direction,
                    color = parsedColor,
                ),
            )
        }

        return LightningPuzzle(
            level = level,
            rows = rows,
            cols = cols,
            arrows = arrows,
        )
    }
}
