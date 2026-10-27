package com.yogi.exitarrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class CandyCrushLevelMapView(
    context: Context,
    private val totalLevels: Int = 100,
    private var unlockedLevel: Int,
    private var currentLevel: Int,
    private val getBestStars: (Int) -> Int,
    private val onLevelSelected: (Int) -> Unit,
    private val onLockedLevelTapped: ((Int) -> Unit)? = null
) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val stageSize = 25

    private val stageNames = arrayOf(
        "GREEN FIELDS",
        "DESERT RUINS",
        "ICE WORLD",
        "VOLCANO"
    )

    private val stageColors = intArrayOf(
        Color.rgb(0, 220, 160),
        Color.rgb(255, 165, 45),
        Color.rgb(50, 190, 255),
        Color.rgb(255, 75, 65)
    )

    private val accent = Color.rgb(0, 220, 255)

    private var selectedStage =
        ((currentLevel - 1).coerceAtLeast(0) / stageSize).coerceIn(0, 3)

    private var levelRects = mutableListOf<Pair<Int, RectF>>()
    private var stageRects = mutableListOf<RectF>()

    private var tabScrollX = 0f
    private var maxTabScrollX = 0f
    private var tabLastTouchX = 0f
    private var isTabDragging = false

    fun updateProgress(
        unlockedLevel: Int,
        currentLevel: Int
    ) {
        // Never trust a level selection as unlock progress.
        this.unlockedLevel = unlockedLevel.coerceIn(1, totalLevels)
        this.currentLevel = currentLevel.coerceIn(1, totalLevels)

        // Update the displayed world to match the map selection.
        selectedStage = ((this.currentLevel - 1) / stageSize).coerceIn(0, 3)

        invalidate()
    }

    private val dp: (Float) -> Float = { it * density }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = true
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = dp(size)
        paint.typeface = if (bold) {
            Typeface.create("sans-serif", Typeface.BOLD)
        } else {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(value, x, y, paint)
    }

    private fun rounded(
        canvas: Canvas,
        r: RectF,
        radius: Float,
        color: Int,
        border: Int? = null,
        stroke: Float = 1f
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(r, dp(radius), dp(radius), paint)

        if (border != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(stroke)
            paint.color = border
            canvas.drawRoundRect(r, dp(radius), dp(radius), paint)
            paint.style = Paint.Style.FILL
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val background = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.rgb(3, 10, 31),
                Color.rgb(5, 24, 55),
                Color.rgb(8, 10, 31)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        paint.shader = background
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Decorative stars.
        paint.style = Paint.Style.FILL
        for (i in 0 until 55) {
            val x = (i * 83f + 17f) % w
            val y = (i * 109f + 31f) % h

            paint.color = Color.argb(
                90 + (i % 5) * 25,
                120, 210, 255
            )

            canvas.drawCircle(
                x,
                y,
                dp(1f + (i % 3) * 0.5f),
                paint
            )
        }

        val margin = dp(12f)
        val contentWidth = w - margin * 2f

        // Header.
        rect.set(margin, dp(10f), w - margin, dp(58f))
        rounded(
            canvas,
            rect,
            16f,
            Color.rgb(5, 26, 67),
            accent,
            2f
        )

        text(
            canvas,
            "LEVEL MAP",
            w / 2f,
            dp(40f),
            24f,
            Color.WHITE
        )

        // World selection tabs.
        stageRects.clear()

        val tabGap = dp(8f)
        val tabWidth = dp(145f)
        val tabTop = dp(66f)
        val tabHeight = dp(77f)
        val totalTabsWidth = 4 * tabWidth + 3 * tabGap

        maxTabScrollX = (totalTabsWidth - contentWidth).coerceAtLeast(0f)
        tabScrollX = tabScrollX.coerceIn(0f, maxTabScrollX)

        canvas.save()

        val tabClipRect = RectF(
            margin,
            tabTop - dp(2f),
            w - margin,
            tabTop + tabHeight + dp(2f)
        )

        canvas.clipRect(tabClipRect)

        for (stage in 0..3) {
            val left = margin + stage * (tabWidth + tabGap) - tabScrollX

            val tab = RectF(
                left,
                tabTop,
                left + tabWidth,
                tabTop + tabHeight
            )

            stageRects.add(tab)

            val unlockedStage = stage * stageSize + 1 <= unlockedLevel

            val tabColor = if (stage == selectedStage) {
                Color.rgb(10, 50, 93)
            } else {
                Color.rgb(13, 23, 46)
            }

            rounded(
                canvas,
                tab,
                12f,
                tabColor,
                if (stage == selectedStage) {
                    stageColors[stage]
                } else {
                    Color.rgb(55, 78, 120)
                },
                if (stage == selectedStage) 2.2f else 1f
            )

            text(
                canvas,
                "WORLD ${stage + 1}",
                tab.centerX(),
                tab.top + dp(22f),
                13.8f,
                if (unlockedStage) Color.WHITE
                else Color.rgb(135, 151, 181)
            )

            text(
                canvas,
                stageNames[stage],
                tab.centerX(),
                tab.top + dp(42f),
                12.5f,
                if (unlockedStage) stageColors[stage]
                else Color.rgb(115, 131, 161)
            )

            val firstLvl = stage * stageSize + 1
            val lastLvl = min(firstLvl + stageSize - 1, totalLevels)
            val completedInStage =
                (firstLvl..lastLvl).count { it < unlockedLevel }

            text(
                canvas,
                "$completedInStage / $stageSize",
                tab.centerX(),
                tab.top + dp(62f),
                13.8f,
                if (unlockedStage) Color.rgb(255, 211, 65)
                else Color.GRAY
            )
        }

        canvas.restore()

        // Five-by-five level grid.
        levelRects.clear()

        val gridLeft = margin + dp(3f)
        val gridRight = w - margin - dp(3f)
        val gridTop = dp(154f)
        val bottomArea = dp(32f)

        val availableWidth = gridRight - gridLeft
        val availableHeight = h - gridTop - bottomArea

        val gapX = dp(7f)
        val gapY = dp(9f)

        val cellWidth = (availableWidth - gapX * 4f) / 5f

        val cellHeight = min(
            dp(86f),
            (availableHeight - gapY * 4f) / 5f
        )

        val gridHeight = cellHeight * 5f + gapY * 4f

        val actualTop = gridTop +
                ((availableHeight - gridHeight) / 2f).coerceAtLeast(0f)

        val firstLevel = selectedStage * stageSize + 1

        for (index in 0 until stageSize) {
            val row = index / 5
            val col = index % 5
            val levelNumber = firstLevel + index

            if (levelNumber > totalLevels) continue

            val left = gridLeft + col * (cellWidth + gapX)
            val top = actualTop + row * (cellHeight + gapY)

            val levelRect = RectF(
                left,
                top,
                left + cellWidth,
                top + cellHeight
            )

            levelRects.add(levelNumber to levelRect)

            val isUnlocked = levelNumber <= unlockedLevel
            val isCurrent = levelNumber == currentLevel
            val stars = getBestStars(levelNumber).coerceIn(0, 3)

            val fillColor = when {
                isCurrent -> Color.rgb(210, 220, 235)
                isUnlocked -> Color.rgb(5, 104, 190)
                else -> Color.rgb(25, 35, 59)
            }

            val borderColor = when {
                isCurrent -> Color.rgb(235, 242, 255)
                isUnlocked -> accent
                else -> Color.rgb(68, 83, 117)
            }

            if (isCurrent) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3.5f)
                paint.color = Color.argb(130, 220, 230, 250)

                val glow = RectF(levelRect)
                glow.inset(-dp(2.5f), -dp(2.5f))

                canvas.drawRoundRect(
                    glow,
                    dp(12f),
                    dp(12f),
                    paint
                )
            }

            rounded(
                canvas,
                levelRect,
                10f,
                fillColor,
                borderColor,
                1.7f
            )

            val centerX = levelRect.centerX()

            if (isUnlocked) {
                text(
                    canvas,
                    levelNumber.toString(),
                    centerX,
                    levelRect.top + cellHeight * 0.48f,
                    if (cellWidth < dp(55f)) 18.5f else 22.5f,
                    if (isCurrent) Color.rgb(30, 30, 45)
                    else Color.WHITE
                )

                if (stars > 0) {
                    val starText = "★".repeat(stars)

                    text(
                        canvas,
                        starText,
                        centerX,
                        levelRect.bottom - dp(6f),
                        17.5f,
                        Color.rgb(255, 218, 66)
                    )
                }
            } else {
                text(
                    canvas,
                    levelNumber.toString(),
                    centerX,
                    levelRect.top + cellHeight * 0.43f,
                    if (cellWidth < dp(55f)) 18.5f else 22.5f,
                    Color.rgb(174, 190, 218)
                )

                text(
                    canvas,
                    "🔒",
                    centerX,
                    levelRect.bottom - dp(8f),
                    22.5f,
                    Color.LTGRAY
                )
            }
        }

        text(
            canvas,
            "SELECT AN UNLOCKED LEVEL TO PLAY",
            w / 2f,
            h - dp(10f),
            12.5f,
            Color.rgb(158, 198, 236)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTabDragging = false
                tabLastTouchX = x
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (maxTabScrollX > 0f && y in dp(60f)..dp(140f)) {
                    val dx = x - tabLastTouchX

                    if (!isTabDragging && abs(dx) > dp(3f)) {
                        isTabDragging = true
                    }

                    if (isTabDragging) {
                        tabScrollX = (tabScrollX - dx)
                            .coerceIn(0f, maxTabScrollX)

                        tabLastTouchX = x
                        invalidate()
                        return true
                    }
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isTabDragging) {
                    isTabDragging = false
                    return true
                }

                for (stage in stageRects.indices) {
                    if (stageRects[stage].contains(x, y)) {
                        selectedStage = stage
                        invalidate()
                        performClick()
                        return true
                    }
                }

                for ((levelNumber, levelRect) in levelRects) {
                    if (levelRect.contains(x, y)) {
                        performClick()

                        if (levelNumber <= unlockedLevel) {
                            onLevelSelected(levelNumber)
                        } else {
                            onLockedLevelTapped?.invoke(levelNumber)
                        }

                        return true
                    }
                }

                performClick()
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}