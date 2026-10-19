package com.yogi.exitarrow

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.IdentityHashMap
import kotlin.math.min
import kotlin.math.sin

class PuzzleBoardView(context: Context) : View(context) {

    var puzzle: Puzzle = Puzzle(
        "Loading", 5, 5, mutableListOf()
    )
        set(value) {
            cancelAnimations()
            field = value
            hintedArrow = null
            invalidate()
        }

    var onArrowTapped: ((Arrow) -> Unit)? = null
    var boardBackgroundColor = GameTheme.PANEL

    private val exits = IdentityHashMap<Arrow, ValueAnimator>()
    private val shakes = IdentityHashMap<Arrow, ValueAnimator>()
    private val shakeOffsets = IdentityHashMap<Arrow, Float>()
    private var hintedArrow: Arrow? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density

    private var cell = 0f
    private var left = 0f
    private var top = 0f
    private var boardW = 0f
    private var boardH = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true

        if (isInEditMode) {
            val p1 = listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 2))
            val p2 = listOf(Pair(2, 2), Pair(2, 1), Pair(2, 0), Pair(1, 0))
            val a1 = Arrow(1, p1, Direction.DOWN, 0xFF42E8FF.toInt())
            val a2 = Arrow(2, p2, Direction.UP, 0xFFFFCF5B.toInt())
            puzzle = Puzzle("Preview", 3, 3, mutableListOf(a1, a2))
        }
    }

    fun isArrowAnimating(arrow: Arrow) = exits.containsKey(arrow)

    fun isClear(arrow: Arrow): Boolean {
        if (!arrow.active || isArrowAnimating(arrow)) return false
        return PuzzleSolver.isClear(puzzle, arrow) { isArrowAnimating(it) }
    }

    fun showHint(arrow: Arrow?) {
        hintedArrow = arrow
        invalidate()
    }

    fun clearHint() {
        hintedArrow = null
        invalidate()
    }

    fun cancelAnimations() {
        val oldExits = exits.values.toList()
        val oldShakes = shakes.values.toList()
        exits.clear()
        shakes.clear()
        shakeOffsets.clear()
        oldExits.forEach { it.cancel() }
        oldShakes.forEach { it.cancel() }

        puzzle.arrows.forEach {
            it.resetAnimation()
        }
        invalidate()
    }

    fun animateArrowOut(arrow: Arrow, onComplete: () -> Unit) {
        if (exits.containsKey(arrow)) return

        shakes.remove(arrow)?.cancel()
        shakeOffsets.remove(arrow)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            interpolator = DecelerateInterpolator(1.2f)

            addUpdateListener {
                val progress = it.animatedValue as Float
                arrow.animProgress = progress
                arrow.alpha = (1f - progress * 0.85f).coerceAtLeast(0f)
                postInvalidateOnAnimation()
            }

            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (exits[arrow] === animation) exits.remove(arrow)

                    if (!cancelled) {
                        arrow.active = false
                        arrow.resetAnimation()
                        invalidate()
                        onComplete()
                    }
                }
            })
        }

        exits[arrow] = animator
        animator.start()
    }

    fun animateBlockedArrow(arrow: Arrow) {
        if (!arrow.active || isArrowAnimating(arrow)) return
        shakes.remove(arrow)?.cancel()

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener {
                val p = it.animatedValue as Float
                shakeOffsets[arrow] =
                    (sin(p * Math.PI * 6) * (1f - p) * 7f * density).toFloat()
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (shakes[arrow] === animation) shakes.remove(arrow)
                    shakeOffsets.remove(arrow)
                    invalidate()
                }
            })
        }

        shakes[arrow] = animator
        animator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val finalW = resolveSize(w, widthMeasureSpec)
        val finalH = resolveSize(h, heightMeasureSpec)
        setMeasuredDimension(finalW, finalH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (puzzle.rows <= 0 || puzzle.cols <= 0) return

        val pad = 3f * density
        cell = min(
            (width - 2f * pad) / puzzle.cols,
            (height - 2f * pad) / puzzle.rows
        )

        boardW = cell * puzzle.cols
        boardH = cell * puzzle.rows
        left = (width - boardW) / 2f
        top = (height - boardH) / 2f

        rect.set(left, top, left + boardW, top + boardH)
        paint.style = Paint.Style.FILL
        paint.color = boardBackgroundColor
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)

        // Draw animated colorful neon glow border around the board
        val timeMs = System.currentTimeMillis()
        val hue = (timeMs / 25f) % 360f
        val glowColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.75f, 0.95f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = glowColor
        paint.setShadowLayer(8f * density, 0f, 0f, glowColor)
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)
        paint.clearShadowLayer()

        // Draw Dot Grid View (minute subtle dots at cell centers)
        paint.style = Paint.Style.FILL
        for (r in 0 until puzzle.rows) {
            for (c in 0 until puzzle.cols) {
                val cx = left + (c + 0.5f) * cell
                val cy = top + (r + 0.5f) * cell
                paint.color = 0xFF22324D.toInt()
                canvas.drawCircle(cx, cy, 1.2f * density, paint)
            }
        }

        // Draw snaking multi-cell arrows with path-following slithering animation
        puzzle.arrows.forEach { arrow ->
            if (!arrow.active && !isArrowAnimating(arrow)) return@forEach
            if (arrow.path.isEmpty()) return@forEach

            val hinted = arrow === hintedArrow
            val color = if (hinted) GameTheme.GOLD else arrow.color
            val pulse = if (hinted) {
                1f + 0.10f * sin(System.currentTimeMillis() / 150.0).toFloat()
            } else 1f

            val shakeX = shakeOffsets[arrow] ?: 0f

            // 1. Build pixel polyline for original arrow body
            val rawPoints = arrow.path.map { (pr, pc) ->
                val px = left + (pc + 0.5f) * cell + shakeX
                val py = top + (pr + 0.5f) * cell
                PointF(px, py)
            }

            // Calculate body segment lengths & total body length
            val segmentLengths = mutableListOf<Float>()
            var bodyLength = 0f
            for (i in 0 until rawPoints.size - 1) {
                val dx = rawPoints[i + 1].x - rawPoints[i].x
                val dy = rawPoints[i + 1].y - rawPoints[i].y
                val len = kotlin.math.hypot(dx, dy)
                segmentLengths.add(len)
                bodyLength += len
            }

            // 2. Extend polyline past head in arrow.direction for exit route
            val headPoint = rawPoints.last()
            val exitExtension = maxOf(width.toFloat(), height.toFloat()) + cell * 2f
            val extHeadX = when (arrow.direction) {
                Direction.LEFT -> headPoint.x - exitExtension
                Direction.RIGHT -> headPoint.x + exitExtension
                else -> headPoint.x
            }
            val extHeadY = when (arrow.direction) {
                Direction.UP -> headPoint.y - exitExtension
                Direction.DOWN -> headPoint.y + exitExtension
                else -> headPoint.y
            }

            val polyline = rawPoints.toMutableList()
            polyline.add(PointF(extHeadX, extHeadY))

            val polyLengths = segmentLengths.toMutableList()
            polyLengths.add(exitExtension)

            val cumDists = FloatArray(polyline.size)
            cumDists[0] = 0f
            for (i in 0 until polyLengths.size) {
                cumDists[i + 1] = cumDists[i] + polyLengths[i]
            }

            fun samplePolyline(dist: Float): PointF {
                val clampedDist = dist.coerceIn(0f, cumDists.last())
                for (i in 0 until cumDists.size - 1) {
                    if (clampedDist >= cumDists[i] && clampedDist <= cumDists[i + 1]) {
                        val segLen = cumDists[i + 1] - cumDists[i]
                        val t = if (segLen > 0f) (clampedDist - cumDists[i]) / segLen else 0f
                        val p0 = polyline[i]
                        val p1 = polyline[i + 1]
                        return PointF(p0.x + t * (p1.x - p0.x), p0.y + t * (p1.y - p0.y))
                    }
                }
                return polyline.last()
            }

            // Slithering shift distance along the head route
            val shift = arrow.animProgress * exitExtension

            // Sample animated body points along the head route
            val animatedPoints = mutableListOf<PointF>()
            val sampleCount = maxOf(14, rawPoints.size * 6)
            val stepDist = bodyLength / (sampleCount - 1)

            for (i in 0 until sampleCount) {
                val u = i * stepDist
                val sampledDist = u + shift
                animatedPoints.add(samplePolyline(sampledDist))
            }

            // Build snaking line path
            val snakingPath = Path()
            snakingPath.moveTo(animatedPoints[0].x, animatedPoints[0].y)
            for (i in 1 until animatedPoints.size) {
                snakingPath.lineTo(animatedPoints[i].x, animatedPoints[i].y)
            }

            // Draw motion trail particles when arrow is departing
            if (isArrowAnimating(arrow)) {
                paint.style = Paint.Style.FILL
                paint.color = color
                for (i in 1..4) {
                    val trailFactor = i * 0.18f
                    val backDist = (bodyLength + shift - trailFactor * cell * 2f).coerceAtLeast(0f)
                    val tp = samplePolyline(backDist)
                    val radius = (4.5f - i * 0.8f) * density
                    paint.alpha = ((1f - trailFactor) * arrow.alpha * 180).toInt().coerceIn(0, 255)
                    canvas.drawCircle(tp.x, tp.y, radius.coerceAtLeast(1f), paint)
                }
            }

            // Draw refined, sleek snaking body pipe stroke (reduced thickness)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND

            // Glow shadow layer (subtle glow)
            paint.strokeWidth = cell * 0.08f * pulse
            paint.color = color
            paint.alpha = (arrow.alpha * 25).toInt().coerceIn(0, 255)
            paint.setShadowLayer(2f * density, 0f, 0f, color)
            canvas.drawPath(snakingPath, paint)
            paint.clearShadowLayer()

            // Main pipe stroke (sleek, thin & refined tail)
            paint.strokeWidth = cell * 0.055f * pulse
            paint.alpha = (arrow.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawPath(snakingPath, paint)

            // Draw sharp, distinct solid-filled triangular arrowhead at the advancing head tip
            val headPt = animatedPoints.last()
            val hx = headPt.x
            val hy = headPt.y
            val headLen = cell * 0.18f * pulse
            val headWidth = cell * 0.12f * pulse

            val headPath = Path()
            when (arrow.direction) {
                Direction.LEFT -> {
                    headPath.moveTo(hx - headLen, hy)
                    headPath.lineTo(hx + headLen * 0.5f, hy - headWidth)
                    headPath.lineTo(hx + headLen * 0.5f, hy + headWidth)
                    headPath.close()
                }
                Direction.RIGHT -> {
                    headPath.moveTo(hx + headLen, hy)
                    headPath.lineTo(hx - headLen * 0.5f, hy - headWidth)
                    headPath.lineTo(hx - headLen * 0.5f, hy + headWidth)
                    headPath.close()
                }
                Direction.UP -> {
                    headPath.moveTo(hx, hy - headLen)
                    headPath.lineTo(hx - headWidth, hy + headLen * 0.5f)
                    headPath.lineTo(hx + headWidth, hy + headLen * 0.5f)
                    headPath.close()
                }
                Direction.DOWN -> {
                    headPath.moveTo(hx, hy + headLen)
                    headPath.lineTo(hx - headWidth, hy - headLen * 0.5f)
                    headPath.lineTo(hx + headWidth, hy - headLen * 0.5f)
                    headPath.close()
                }
            }

            paint.style = Paint.Style.FILL
            paint.color = color
            paint.alpha = (arrow.alpha * 255).toInt().coerceIn(0, 255)
            paint.setShadowLayer(8f * density, 0f, 0f, color)
            canvas.drawPath(headPath, paint)
            paint.clearShadowLayer()
        }

        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        if (cell <= 0f) return true

        val col = ((event.x - left) / cell).toInt()
        val row = ((event.y - top) / cell).toInt()

        if (row !in 0 until puzzle.rows || col !in 0 until puzzle.cols) {
            performClick()
            return true
        }

        val arrow = puzzle.arrows.firstOrNull {
            it.active && it.occupies(row, col) && !isArrowAnimating(it)
        }

        if (arrow != null) {
            clearHint()
            onArrowTapped?.invoke(arrow)
        }

        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
