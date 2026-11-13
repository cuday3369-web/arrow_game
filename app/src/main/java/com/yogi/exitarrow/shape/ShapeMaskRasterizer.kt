package com.yogi.exitarrow.shape

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Converts a vector/rule-based object template into the same logical grid used by Arrow.path. */
object ShapeMaskRasterizer {
    fun rasterize(
        template: ShapeTemplate,
        rows: Int,
        cols: Int,
        formation: ShapeFormation = ShapeFormation.HYBRID
    ): ShapeMask {
        require(rows >= 4 && cols >= 4) { "Shape masks need at least a 4x4 grid." }

        val full = Array(rows) { BooleanArray(cols) }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = ((c + 0.5f) / cols) * 2f - 1f
                val y = 1f - ((r + 0.5f) / rows) * 2f
                full[r][c] = containsPoint(template, x, y)
            }
        }

        val contour = linkedSetOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!full[r][c]) continue
                val isBoundary = listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                    .any { (nr, nc) -> nr !in 0 until rows || nc !in 0 until cols || !full[nr][nc] }
                if (isBoundary) contour += r to c
            }
        }

        val selected = when (formation) {
            ShapeFormation.FILLED, ShapeFormation.HYBRID -> full
            ShapeFormation.OUTLINE -> Array(rows) { r ->
                BooleanArray(cols) { c -> full[r][c] && (r to c) in contour }
            }
        }

        // Very small/thin silhouettes are widened by one logical cell. This avoids
        // unusable templates on compact phone grids while retaining the overall shape.
        if (selected.sumOf { row -> row.count { it } } < 6) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (full[r][c]) continue
                    val neighborInside = listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                        .any { (nr, nc) -> nr in 0 until rows && nc in 0 until cols && full[nr][nc] }
                    if (neighborInside) selected[r][c] = true
                }
            }
        }

        return ShapeMask(rows, cols, template, formation, selected, contour)
    }

    private fun containsPoint(template: ShapeTemplate, x: Float, y: Float): Boolean {
        if (template.kind == ShapeKind.ALPHABET || template.kind == ShapeKind.NUMBER) {
            return glyphContains(template, x, y)
        }

        return when (template.kind) {
            ShapeKind.HEART -> {
                val hx = x * 1.15f
                val hy = y * 1.15f
                val q = hx * hx + hy * hy - 1f
                q * q * q - hx * hx * hy * hy * hy <= 0f
            }
            ShapeKind.DIAMOND -> abs(x) + abs(y) <= 0.92f
            ShapeKind.STAR -> pointInPolygon(x, y, starPoints())
            ShapeKind.BUTTERFLY ->
                ellipse(x, y, -0.38f, 0.25f, 0.43f, 0.48f) ||
                        ellipse(x, y, 0.38f, 0.25f, 0.43f, 0.48f) ||
                        ellipse(x, y, -0.36f, -0.34f, 0.34f, 0.34f) ||
                        ellipse(x, y, 0.36f, -0.34f, 0.34f, 0.34f) ||
                        (abs(x) <= 0.085f && y in -0.68f..0.70f)
            ShapeKind.FISH ->
                ellipse(x, y, 0.10f, 0f, 0.69f, 0.43f) ||
                        pointInPolygon(x, y, listOf(-0.48f to 0f, -0.98f to 0.42f, -0.98f to -0.42f))
            ShapeKind.ELEPHANT -> elephantContains(x, y)
            ShapeKind.ROOSTER -> roosterContains(x, y)
            ShapeKind.TREE ->
                pointInPolygon(x, y, listOf(-0.88f to -0.10f, -0.62f to 0.42f, -0.30f to 0.70f, 0f to 0.52f, 0.30f to 0.77f, 0.70f to 0.42f, 0.91f to -0.10f)) ||
                        rectangle(x, y, -0.20f, -0.78f, 0.20f, -0.05f)
            ShapeKind.HOUSE ->
                rectangle(x, y, -0.67f, -0.77f, 0.67f, 0.22f) ||
                        pointInPolygon(x, y, listOf(-0.82f to 0.12f, 0f to 0.88f, 0.82f to 0.12f))
            ShapeKind.CAR ->
                pointInPolygon(x, y, listOf(-0.91f to -0.42f, -0.78f to 0.02f, -0.42f to 0.08f, -0.12f to 0.48f, 0.45f to 0.48f, 0.73f to 0.05f, 0.91f to -0.12f, 0.91f to -0.45f)) ||
                        ellipse(x, y, -0.52f, -0.47f, 0.19f, 0.19f) || ellipse(x, y, 0.55f, -0.47f, 0.19f, 0.19f)
            ShapeKind.ROCKET ->
                pointInPolygon(x, y, listOf(0f to 0.96f, 0.36f to 0.38f, 0.36f to -0.48f, 0.68f to -0.83f, 0.20f to -0.72f, 0f to -0.96f, -0.20f to -0.72f, -0.68f to -0.83f, -0.36f to -0.48f, -0.36f to 0.38f))
            ShapeKind.ALPHABET, ShapeKind.NUMBER -> false
        }
    }

    private fun elephantContains(x: Float, y: Float): Boolean =
        ellipse(x, y, 0.10f, -0.08f, 0.66f, 0.43f) ||
                ellipse(x, y, -0.48f, 0.18f, 0.30f, 0.34f) ||
                ellipse(x, y, -0.58f, 0.18f, 0.24f, 0.35f) ||
                pointInPolygon(x, y, listOf(-0.67f to 0.13f, -0.85f to 0.04f, -0.82f to -0.64f, -0.57f to -0.64f, -0.52f to -0.12f)) ||
                rectangle(x, y, -0.30f, -0.72f, -0.06f, -0.34f) ||
                rectangle(x, y, 0.25f, -0.72f, 0.49f, -0.34f)

    private fun roosterContains(x: Float, y: Float): Boolean =
        ellipse(x, y, 0.12f, -0.12f, 0.50f, 0.36f) ||
                ellipse(x, y, -0.37f, 0.30f, 0.20f, 0.22f) ||
                pointInPolygon(x, y, listOf(-0.48f to 0.43f, -0.55f to 0.77f, -0.36f to 0.62f, -0.22f to 0.82f, -0.18f to 0.43f)) ||
                pointInPolygon(x, y, listOf(-0.55f to 0.31f, -0.85f to 0.20f, -0.55f to 0.12f)) ||
                pointInPolygon(x, y, listOf(0.47f to 0.02f, 0.91f to 0.38f, 0.73f to -0.02f, 0.95f to -0.25f, 0.46f to -0.18f)) ||
                rectangle(x, y, -0.02f, -0.62f, 0.10f, -0.39f) ||
                rectangle(x, y, 0.27f, -0.62f, 0.39f, -0.39f)

    private fun glyphContains(template: ShapeTemplate, x: Float, y: Float): Boolean {
        val rows = ShapeLibrary.glyphRows(template) ?: return false
        val col = (((x + 1f) * 0.5f) * 5f).toInt().coerceIn(0, 4)
        val row = (((1f - y) * 0.5f) * 7f).toInt().coerceIn(0, 6)
        return rows.getOrNull(row)?.getOrNull(col) == '1'
    }

    private fun ellipse(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
        val nx = (x - cx) / rx
        val ny = (y - cy) / ry
        return nx * nx + ny * ny <= 1f
    }

    private fun rectangle(x: Float, y: Float, left: Float, bottom: Float, right: Float, top: Float): Boolean =
        x in left..right && y in bottom..top

    private fun starPoints(): List<Pair<Float, Float>> = (0 until 10).map { index ->
        val radius = if (index % 2 == 0) 0.94f else 0.40f
        val angle = (-PI / 2.0 + index * PI / 5.0).toFloat()
        (cos(angle) * radius) to (sin(angle) * radius)
    }

    private fun pointInPolygon(x: Float, y: Float, points: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val (xi, yi) = points[i]
            val (xj, yj) = points[j]
            val crosses = (yi > y) != (yj > y) &&
                    x < (xj - xi) * (y - yi) / ((yj - yi).let { if (abs(it) < 0.00001f) 0.00001f else it }) + xi
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

}
