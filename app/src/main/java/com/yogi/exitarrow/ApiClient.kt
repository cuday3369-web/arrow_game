package com.yogi.exitarrow

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(
    private val baseUrl: String,
) {
    fun fetchLevel(level: Int): Puzzle? {
        return request("$baseUrl/level/$level") { json ->
            val rows = json.getInt("rows")
            val cols = json.getInt("cols")
            val array = json.getJSONArray("arrows")
            val arrows = mutableListOf<Arrow>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val direction = Direction.fromString(
                    item.getString("direction"),
                )

                val id = item.optInt("id", i + 1)
                val color = GameTheme.ARROW_COLORS[
                    i % GameTheme.ARROW_COLORS.size,
                ]

                val pathList = mutableListOf<Pair<Int, Int>>()
                if (item.has("path")) {
                    val pathArray = item.getJSONArray("path")
                    for (p in 0 until pathArray.length()) {
                        val pt = pathArray.getJSONArray(p)
                        pathList.add(Pair(pt.getInt(0), pt.getInt(1)))
                    }
                } else {
                    val row = item.getInt("row")
                    val col = item.getInt("col")
                    pathList.add(Pair(row, col))
                }

                arrows.add(
                    Arrow(id, pathList, direction, color)
                )
            }

            if (arrows.isEmpty()) return@request null

            val puzzle = Puzzle(
                title = json.optString("title", "Level $level"),
                rows = rows,
                cols = cols,
                arrows = arrows,
                level = json.optInt("level", level),
                difficulty = json.optString("difficulty", "Easy"),
                seed = json.optLong("seed", 0L)
            )

            if (PuzzleSolver.solveOrder(puzzle) == null) null else puzzle
        }
    }

    fun checkHealth(): Boolean {
        return try {
            val connection = URL("$baseUrl/health")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"

            val success = (connection.responseCode in 200..299)
            connection.disconnect()
            success
        } catch (_: Exception) {
            false
        }
    }

    private fun <T> request(
        url: String,
        parse: (JSONObject) -> T?
    ): T? {
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            if (connection.responseCode !in 200..299) return null

            val response = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            parse(JSONObject(response))
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
