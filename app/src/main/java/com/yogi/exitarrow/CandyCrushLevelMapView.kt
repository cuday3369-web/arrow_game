package com.yogi.exitarrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

class CandyCrushLevelMapView(
    context: Context,
    private val totalLevels: Int = 100,
    private val unlockedLevel: Int,
    private val currentLevel: Int,
    private val getBestStars: (Int) -> Int,
    private val onLevelSelected: (Int) -> Unit,
    private val onLockedLevelTapped: ((Int) -> Unit)? = null
) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodeRadius = 26f * density
    private val levelSpacing = 72f * density
    private val topMargin = 50f * density

    private val nodePositions = mutableListOf<Pair<Float, Float>>()
    private val clipRect = Rect()

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (totalLevels * levelSpacing + topMargin * 2f).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val centerX = w / 2f
        val amplitude = w * 0.28f

        // Get visible viewport clip bounds for 60/120 FPS infinite scrolling
        canvas.getClipBounds(clipRect)
        val visibleTop = clipRect.top.toFloat() - levelSpacing * 2f
        val visibleBottom = clipRect.bottom.toFloat() + levelSpacing * 2f

        // Fill background canvas with dark cosmic space theme
        canvas.drawColor(Color.rgb(11, 16, 33))

        // Draw ambient background stars
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(120, 255, 255, 255)
        for (i in 0 until 60) {
            val sx = ((i * 137.5f) % w)
            val sy = ((i * 219.3f) % h)
            if (sy in visibleTop..visibleBottom) {
                val r = (1.2f + (i % 3) * 0.8f) * density
                canvas.drawCircle(sx, sy, r, paint)
            }
        }

        nodePositions.clear()

        // Calculate positions along a winding S-curve (Level 1 at bottom, totalLevels at top)
        for (i in 1..totalLevels) {
            val index = totalLevels - i
            val y = topMargin + index * levelSpacing
            val x = centerX + sin(i * 0.55f) * amplitude
            nodePositions.add(Pair(x, y))
        }

        // Draw winding circular connection lines connecting level nodes
        if (nodePositions.size > 1) {
            val path = Path()
            var pathStarted = false

            for (i in 1 until nodePositions.size) {
                val prev = nodePositions[i - 1]
                val curr = nodePositions[i]

                if ((prev.second in visibleTop..visibleBottom) || (curr.second in visibleTop..visibleBottom)) {
                    if (!pathStarted) {
                        path.moveTo(prev.first, prev.second)
                        pathStarted = true
                    }
                    val midY = (prev.second + curr.second) / 2f
                    path.cubicTo(
                        prev.first, midY,
                        curr.first, midY,
                        curr.first, curr.second
                    )
                } else {
                    pathStarted = false
                }
            }

            // Glow / Shadow stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f * density
            paint.color = Color.argb(60, 68, 224, 255)
            canvas.drawPath(path, paint)

            // Main connection line stroke
            paint.strokeWidth = 5f * density
            paint.color = GameTheme.CYAN
            paint.pathEffect = DashPathEffect(floatArrayOf(14f * density, 10f * density), 0f)
            canvas.drawPath(path, paint)
            paint.pathEffect = null
        }

        // Draw circular level nodes (only visible ones)
        for (i in 1..totalLevels) {
            val pos = nodePositions[i - 1]
            val x = pos.first
            val y = pos.second

            if (y !in visibleTop..visibleBottom) continue

            val isUnlocked = i <= unlockedLevel
            val isCurrent = i == currentLevel
            val stars = if (isUnlocked) getBestStars(i) else 0

            // Draw current level glowing aura ring
            if (isCurrent) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * density
                paint.color = GameTheme.GOLD
                val pulse = 1f + 0.10f * sin(System.currentTimeMillis() / 160.0).toFloat()
                canvas.drawCircle(x, y, nodeRadius * 1.35f * pulse, paint)
                postInvalidateOnAnimation()
            }

            // Draw node circle background
            paint.style = Paint.Style.FILL
            paint.color = when {
                isCurrent -> GameTheme.GOLD
                isUnlocked -> when (i % 3) {
                    0 -> GameTheme.CYAN
                    1 -> GameTheme.PURPLE
                    else -> GameTheme.GREEN
                }
                else -> Color.rgb(25, 32, 48)
            }
            canvas.drawCircle(x, y, nodeRadius, paint)

            // Draw node border stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * density
            paint.color = if (isUnlocked) GameTheme.WHITE else Color.rgb(55, 68, 98)
            canvas.drawCircle(x, y, nodeRadius, paint)

            // Draw text/icon inside node
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER

            val diffName = PuzzleGenerator.difficultyForLevel(i)

            if (isUnlocked) {
                paint.textSize = 16f * density
                paint.color = if (isCurrent) GameTheme.BACKGROUND else GameTheme.WHITE
                paint.isFakeBoldText = true
                val textY = y - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText("$i", x, textY, paint)

                // Draw difficulty tier & star rating below node
                paint.textSize = 10f * density
                paint.color = if (i % 5 == 0) GameTheme.RED else GameTheme.CYAN
                canvas.drawText(diffName, x, y + nodeRadius + 13f * density, paint)

                paint.textSize = 10f * density
                paint.color = GameTheme.GOLD
                val starStr = when (stars) {
                    3 -> "★★★"
                    2 -> "★★☆"
                    1 -> "★☆☆"
                    else -> "☆☆☆"
                }
                canvas.drawText(starStr, x, y + nodeRadius + 25f * density, paint)
            } else {
                paint.textSize = 13f * density
                paint.color = GameTheme.MUTED
                val textY = y - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText("🔒", x, textY, paint)

                // Draw difficulty tier & "LOCKED" label below locked node
                paint.textSize = 9f * density
                paint.color = Color.rgb(120, 135, 165)
                canvas.drawText(diffName, x, y + nodeRadius + 12f * density, paint)

                paint.textSize = 8f * density
                paint.color = Color.rgb(90, 105, 135)
                canvas.drawText("LOCKED", x, y + nodeRadius + 23f * density, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        val touchX = event.x
        val touchY = event.y

        for (i in 1..totalLevels) {
            if (i > nodePositions.size) break
            val pos = nodePositions[i - 1]
            val dx = touchX - pos.first
            val dy = touchY - pos.second
            val distSq = dx * dx + dy * dy

            if (distSq <= (nodeRadius * 1.5f) * (nodeRadius * 1.5f)) {
                performClick()
                if (i <= unlockedLevel) {
                    onLevelSelected(i)
                } else {
                    onLockedLevelTapped?.invoke(i)
                }
                return true
            }
        }
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
