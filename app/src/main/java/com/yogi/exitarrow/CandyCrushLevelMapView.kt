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
    private val nodeRadius = 34f * density // Bigger level circle nodes
    private val levelSpacing = 92f * density
    private val topMargin = 60f * density

    private val nodePositions = mutableListOf<Pair<Float, Float>>()
    private val clipRect = Rect()

    private var tappedNodeIndex = -1
    private var tappedNodeScale = 1.0f
    private var lockedShakeIndex = -1
    private var lockedShakeOffset = 0f

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

        // Fill background canvas with 3D deep ocean theme (#061325)
        canvas.drawColor(Color.rgb(6, 19, 37))

        // Draw animated 3D ocean caustics & rising aquatic bubbles
        val timeSec = System.currentTimeMillis() / 1000f
        paint.style = Paint.Style.FILL
        for (i in 0 until 50) {
            val bx = ((i * 147.5f + sin(timeSec + i) * 15f) % w)
            val by = ((i * 219.3f - timeSec * 25f) % h).let { if (it < 0) it + h else it }
            if (by in visibleTop..visibleBottom) {
                val r = (1.8f + (i % 4) * 1.2f) * density
                paint.color = Color.argb(80, 68, 224, 255)
                canvas.drawCircle(bx, by, r, paint)
            }
        }

        nodePositions.clear()

        // Calculate positions along a winding 3D S-curve (Level 1 at bottom, totalLevels at top)
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
            paint.strokeWidth = 10f * density
            paint.color = Color.argb(70, 0, 180, 255)
            canvas.drawPath(path, paint)

            // Main 3D ocean stream connection line stroke
            paint.strokeWidth = 6f * density
            paint.color = GameTheme.CYAN
            paint.pathEffect = DashPathEffect(floatArrayOf(16f * density, 12f * density), 0f)
            canvas.drawPath(path, paint)
            paint.pathEffect = null
        }

        // Draw circular 3D level nodes (only visible ones)
        for (i in 1..totalLevels) {
            val pos = nodePositions[i - 1]
            val origX = pos.first
            val y = pos.second

            if (y !in visibleTop..visibleBottom) continue

            val isUnlocked = i <= unlockedLevel
            val isCurrent = i == currentLevel
            val stars = if (isUnlocked) getBestStars(i) else 0

            val scale = if (i == tappedNodeIndex) tappedNodeScale else 1f
            val shakeX = if (i == lockedShakeIndex) lockedShakeOffset else 0f
            val x = origX + shakeX
            val radius = nodeRadius * scale

            // 1. Draw 3D Under-Shadow for depth elevation
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(160, 2, 8, 18)
            canvas.drawCircle(x, y + 5f * density, radius, paint)

            // 2. Draw current level glowing aura ring
            if (isCurrent) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f * density
                paint.color = GameTheme.GOLD
                val pulse = 1f + 0.12f * sin(System.currentTimeMillis() / 150.0).toFloat()
                canvas.drawCircle(x, y, radius * 1.35f * pulse, paint)
                postInvalidateOnAnimation()
            }

            // 3. Draw node circle background
            paint.style = Paint.Style.FILL
            paint.color = when {
                isCurrent -> GameTheme.GOLD
                isUnlocked -> when (i % 3) {
                    0 -> GameTheme.CYAN
                    1 -> GameTheme.PURPLE
                    else -> GameTheme.GREEN
                }
                else -> Color.rgb(20, 28, 44)
            }
            canvas.drawCircle(x, y, radius, paint)

            // 4. Draw 3D metallic/crystal border stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f * density
            paint.color = if (isUnlocked) GameTheme.WHITE else Color.rgb(45, 58, 88)
            canvas.drawCircle(x, y, radius, paint)

            // Draw text/icon inside node
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER

            val diffName = PuzzleGenerator.difficultyForLevel(i)
            val shapeTheme = PuzzleGenerator.shapeThemeForLevel(i)

            if (isUnlocked) {
                paint.textSize = 20f * density * scale
                paint.color = if (isCurrent) GameTheme.BACKGROUND else GameTheme.WHITE
                paint.isFakeBoldText = true
                val textY = y - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText("$i", x, textY, paint)

                // Row 1: BOLD difficulty tier name
                paint.textSize = 12f * density
                paint.isFakeBoldText = true
                paint.color = if (i % 5 == 0) GameTheme.RED else GameTheme.CYAN
                canvas.drawText(diffName, x, y + radius + 16f * density, paint)

                // Row 2: BOLD Shape Theme name (e.g. "Heart Shaped Arrow Level ♥")
                paint.textSize = 9.5f * density // Adjusted font size to fit long shape phrases
                paint.isFakeBoldText = true
                paint.color = GameTheme.GOLD
                canvas.drawText(shapeTheme, x, y + radius + 28f * density, paint)

                // Row 3: Only show earned stars (hide unearned stars)
                val starStr = when (stars) {
                    3 -> "★★★"
                    2 -> "★★"
                    1 -> "★"
                    else -> ""
                }
                if (starStr.isNotEmpty()) {
                    paint.textSize = 11f * density
                    paint.color = GameTheme.GOLD
                    canvas.drawText(starStr, x, y + radius + 40f * density, paint)
                }
            } else {
                paint.textSize = 16f * density * scale
                paint.color = GameTheme.MUTED
                val textY = y - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText("🔒", x, textY, paint)

                // Draw BOLD difficulty tier name & Shape Theme & "LOCKED" label below locked node
                paint.textSize = 11f * density
                paint.isFakeBoldText = true
                paint.color = Color.rgb(130, 148, 180)
                canvas.drawText(diffName, x, y + radius + 15f * density, paint)

                paint.textSize = 8.5f * density // Adjusted font size to fit long shape phrases
                paint.isFakeBoldText = true
                paint.color = Color.rgb(160, 175, 205)
                canvas.drawText(shapeTheme, x, y + radius + 26f * density, paint)

                paint.textSize = 8f * density
                paint.isFakeBoldText = true
                paint.color = Color.rgb(90, 105, 135)
                canvas.drawText("LOCKED", x, y + radius + 37f * density, paint)
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
                    animateNodeBounce(i) {
                        onLevelSelected(i)
                    }
                } else {
                    animateLockedShake(i) {
                        onLockedLevelTapped?.invoke(i)
                    }
                }
                return true
            }
        }
        performClick()
        return true
    }

    private fun animateNodeBounce(levelIndex: Int, onComplete: () -> Unit) {
        tappedNodeIndex = levelIndex
        android.animation.ValueAnimator.ofFloat(1.0f, 1.35f, 1.0f).apply {
            duration = 240L
            interpolator = android.view.animation.OvershootInterpolator(2.5f)
            addUpdateListener { anim ->
                tappedNodeScale = anim.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    tappedNodeIndex = -1
                    tappedNodeScale = 1.0f
                    onComplete()
                }
            })
            start()
        }
    }

    private fun animateLockedShake(levelIndex: Int, onComplete: () -> Unit) {
        lockedShakeIndex = levelIndex
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                lockedShakeOffset = (sin(p * Math.PI * 6) * (1f - p) * 10f * density).toFloat()
                postInvalidateOnAnimation()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    lockedShakeIndex = -1
                    lockedShakeOffset = 0f
                    onComplete()
                }
            })
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
