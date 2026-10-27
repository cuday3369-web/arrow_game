package com.yogi.exitarrow

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ExitArrowIntroView @JvmOverloads constructor(
    context: Context,
    private val onFinished: (() -> Unit)? = null
) : View(context) {

    companion object {
        private const val DURATION = 7200L
        private const val FRAME_DELAY = 16L
        private const val ARROW_COUNT = 18
        private const val TWO_PI = Math.PI * 2.0
    }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val random = java.util.Random(42L)

    private var startTime = 0L
    private var lastFrame = 0L
    private var animationFinished = false

    private val interpolator = DecelerateInterpolator()

    private val colors = intArrayOf(
        Color.rgb(255, 35, 65),
        Color.rgb(255, 105, 0),
        Color.rgb(255, 220, 25),
        Color.rgb(80, 255, 55),
        Color.rgb(0, 255, 200),
        Color.rgb(0, 165, 255),
        Color.rgb(100, 70, 255),
        Color.rgb(255, 30, 220)
    )

    private data class Arrow(
        val angle: Float,
        val delay: Float,
        val colorIndex: Int,
        val direction: Float,
        val size: Float
    )

    private val arrows = List(ARROW_COUNT) { i ->
        Arrow(
            angle = (TWO_PI * i / ARROW_COUNT).toFloat() +
                    (random.nextFloat() - 0.5f) * 0.20f,
            delay = random.nextFloat() * 0.45f,
            colorIndex = i % colors.size,
            direction = if (i % 2 == 0) 1f else -1f,
            size = 0.75f + random.nextFloat() * 0.55f
        )
    }

    private val particles = List(140) {
        floatArrayOf(
            random.nextFloat(),
            random.nextFloat(),
            0.4f + random.nextFloat() * 2.0f,
            random.nextFloat() * TWO_PI.toFloat(),
            0.4f + random.nextFloat() * 1.8f
        )
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1000f,
            intArrayOf(
                Color.rgb(1, 4, 17),
                Color.rgb(3, 8, 30),
                Color.rgb(0, 2, 12)
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    private fun dp(value: Float) = value * density

    private fun clamp(value: Float) = value.coerceIn(0f, 1f)

    private fun smooth(value: Float): Float {
        val x = clamp(value)
        return x * x * (3f - 2f * x)
    }

    private fun progress(t: Float, begin: Float, end: Float): Float =
        smooth((t - begin) / (end - begin))

    private fun colorAt(index: Int): Int =
        colors[Math.floorMod(index, colors.size)]

    fun start() {
        startTime = SystemClock.uptimeMillis()
        lastFrame = 0L
        animationFinished = false
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(null)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        val now = SystemClock.uptimeMillis()

        if (lastFrame == 0L) lastFrame = now

        val t = ((now - startTime).coerceAtLeast(0L) / 1000f)
            .coerceAtMost(7.2f)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.46f
        val unit = min(w, h)

        canvas.drawColor(Color.BLACK)
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        drawBackgroundGlow(canvas, cx, cy, unit, t)
        drawParticles(canvas, w, h, cx, cy, unit, t)

        when {
            t < 0.8f -> {
                // 1. Cinematic opening.
            }

            t < 2.8f -> {
                // 2–3. Arrows enter and sweep across the screen.
                drawArrows(canvas, cx, cy, unit, t, 0.8f, 2.8f)
            }

            t < 4.2f -> {
                // 4. Arrows follow a curved spiral toward the center.
                drawSwirl(canvas, cx, cy, unit, t)
            }

            t < 5.4f -> {
                // 5–6. Central flash and expanding energy ring.
                drawConvergence(canvas, cx, cy, unit, t)
            }

            t < 6.6f -> {
                // 7–8. Logo reveal, growth and glow.
                drawArrows(
                    canvas, cx, cy, unit, t,
                    5.4f, 6.2f, 0.28f
                )
                drawTitle(canvas, cx, cy, unit, t)
                drawEnergyRing(canvas, cx, cy, unit, t, 0.25f)
            }

            t < 6.8f -> {
                // 9. Crisp final logo, surrounding arrows fade.
                drawArrows(
                    canvas, cx, cy, unit, t,
                    5.4f, 6.8f, 0.18f
                )
                drawTitle(canvas, cx, cy, unit, t)
            }

            else -> {
                // 10. Final logo and rainbow swoosh.
                drawSwoosh(canvas, cx, cy, unit, t)
                drawTitle(canvas, cx, cy, unit, t)
            }
        }

        if (t < 7.2f && !animationFinished) {
            lastFrame = now
            postInvalidateOnAnimation()
        } else if (!animationFinished) {
            animationFinished = true
            onFinished?.invoke()
        }
    }

    private fun drawBackgroundGlow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float
    ) {
        val intensity = when {
            t < 0.8f -> 0.30f
            t < 4.2f -> 0.48f
            t < 5.4f -> 0.85f
            else -> 0.30f
        }

        paint.shader = RadialGradient(
            cx, cy, unit * 0.70f,
            intArrayOf(
                Color.argb((45 * intensity).toInt(), 45, 35, 255),
                Color.argb((24 * intensity).toInt(), 0, 160, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawParticles(
        canvas: Canvas,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float
    ) {
        val burst = progress(t, 4.2f, 5.4f)
        val titleFade = 1f - progress(t, 6.0f, 6.8f) * 0.65f

        particles.forEachIndexed { i, p ->
            val angle = p[3] + t * 0.20f * p[4]
            val baseX = p[0] * w
            val baseY = p[1] * h

            val radialX = cx + cos(angle) * unit * p[0] * 0.55f
            val radialY = cy + sin(angle) * unit * p[1] * 0.55f

            val x = baseX + (radialX - baseX) * burst
            val y = baseY + (radialY - baseY) * burst

            val twinkle = 0.35f + 0.65f *
                    abs(sin(t * 2.5f + i.toFloat()))

            val alpha = (150 * twinkle * titleFade).toInt()
                .coerceIn(0, 255)

            paint.color = colorAt(i % colors.size)
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            paint.maskFilter = null

            canvas.drawCircle(
                x, y,
                dp(p[2]) * (1f + burst * 0.7f),
                paint
            )
        }

        paint.alpha = 255
    }

    private fun drawArrowShape(
        canvas: Canvas,
        x: Float,
        y: Float,
        angle: Float,
        length: Float,
        color: Int,
        alpha: Int = 255
    ) {
        val shaftWidth = length * 0.12f
        val headLength = length * 0.43f
        val headWidth = length * 0.40f

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())

        path.reset()

        // A single clean, symmetric arrow polygon.
        path.moveTo(-length * 0.5f, -shaftWidth * 0.5f)
        path.lineTo(length * 0.5f - headLength, -shaftWidth * 0.5f)
        path.lineTo(length * 0.5f - headLength, -headWidth * 0.5f)
        path.lineTo(length * 0.5f, 0f)
        path.lineTo(length * 0.5f - headLength, headWidth * 0.5f)
        path.lineTo(length * 0.5f - headLength, shaftWidth * 0.5f)
        path.lineTo(-length * 0.5f, shaftWidth * 0.5f)
        path.close()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        paint.alpha = alpha
        paint.maskFilter = BlurMaskFilter(dp(9f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)

        paint.maskFilter = null
        paint.style = Paint.Style.FILL
        paint.alpha = (alpha * 0.24f).toInt()
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.alpha = alpha
        paint.maskFilter = null
        canvas.drawPath(path, paint)

        canvas.restore()
        paint.alpha = 255
    }

    private fun drawArrows(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float,
        begin: Float,
        end: Float,
        opacity: Float = 1f
    ) {
        val enter = progress(t, begin, begin + 0.65f)
        val exit = 1f - progress(t, end - 0.25f, end)

        arrows.forEachIndexed { i, a ->
            val angle = a.angle + t * a.direction * 0.18f
            val radius = unit * (0.18f + 0.40f * (1f - enter))

            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius

            val sweepAngle = angle + PI.toFloat() / 2f *
                    sin(t * 1.8f + i)

            val length = unit * 0.105f * a.size
            val alpha = (255 * enter * exit * opacity).toInt()
                .coerceIn(0, 255)

            drawArrowShape(
                canvas,
                x, y,
                sweepAngle,
                length,
                colorAt(a.colorIndex),
                alpha
            )
        }
    }

    private fun drawSwirl(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float
    ) {
        val p = progress(t, 2.8f, 4.2f)

        arrows.forEachIndexed { i, a ->
            val startAngle = a.angle
            val angle = startAngle + p * TWO_PI.toFloat() * 1.5f

            val radius = unit * (
                0.48f * (1f - p) + 0.045f +
                    0.018f * sin(t * 5f + i)
                )

            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius

            val tangent = angle + PI.toFloat() / 2f
            val length = unit * 0.11f * a.size

            drawArrowShape(
                canvas, x, y, tangent, length,
                colorAt(a.colorIndex),
                245
            )

            val trailRadius = radius * 1.15f
            val trailX = cx + cos(angle - 0.24f) * trailRadius
            val trailY = cy + sin(angle - 0.24f) * trailRadius

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = colorAt(a.colorIndex)
            paint.alpha = 105
            paint.maskFilter = BlurMaskFilter(dp(7f), BlurMaskFilter.Blur.NORMAL)

            canvas.drawLine(x, y, trailX, trailY, paint)

            paint.maskFilter = null
            paint.alpha = 255
        }
    }

    private fun drawConvergence(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float
    ) {
        val p = progress(t, 4.2f, 4.8f)
        val ringP = progress(t, 4.8f, 5.4f)

        arrows.forEachIndexed { i, a ->
            val angle = a.angle + t * 0.5f
            val radius = unit * (0.43f * (1f - p) + 0.02f)

            drawArrowShape(
                canvas,
                cx + cos(angle) * radius,
                cy + sin(angle) * radius,
                angle + PI.toFloat(),
                unit * 0.10f * a.size,
                colorAt(a.colorIndex),
                ((1f - p * 0.65f) * 255).toInt()
            )
        }

        drawEnergyRing(canvas, cx, cy, unit, t, ringP)

        val flash = (1f - progress(t, 4.65f, 5.05f)) *
                progress(t, 4.25f, 4.6f)

        if (flash > 0f) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx, cy, unit * 0.32f,
                intArrayOf(
                    Color.argb((255 * flash).toInt(), 255, 255, 255),
                    Color.argb((170 * flash).toInt(), 80, 220, 255),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawCircle(cx, cy, unit * 0.32f, paint)
            paint.shader = null
        }
    }

    private fun drawEnergyRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float,
        p: Float
    ) {
        if (t < 4.65f) return

        val radius = unit * (0.06f + p * 0.28f)
        val alpha = ((1f - p) * 220).toInt().coerceIn(0, 255)

        for (i in 0 until 3) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (i == 0) 2.5f else 1f)
            paint.color = colorAt(i * 2)
            paint.alpha = alpha
            paint.maskFilter = if (i == 0) {
                BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL)
            } else null

            canvas.drawCircle(
                cx, cy,
                radius * (1f + i * 0.12f),
                paint
            )
        }

        paint.maskFilter = null
        paint.alpha = 255
    }

    private fun drawTitle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float
    ) {
        val p = progress(t, 5.4f, 6.6f)
        val alpha = (255 * progress(t, 5.4f, 5.8f)).toInt()
        val scale = 0.65f + 0.35f * interpolator.getInterpolation(p)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        val titleSize = unit * 0.145f
        val subtitleSize = unit * 0.102f

        paint.typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL
        paint.textSize = titleSize
        paint.color = Color.WHITE
        paint.alpha = alpha

        // Glow behind EXIT.
        paint.setShadowLayer(dp(20f), 0f, 0f, Color.CYAN)
        paint.color = Color.rgb(255, 180, 40)
        canvas.drawText("EXIT", 0f, titleSize * 0.12f, paint)

        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.rgb(255, 210, 70)
        canvas.drawText("EXIT", 0f, titleSize * 0.12f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = subtitleSize
        paint.color = Color.rgb(0, 205, 255)
        paint.setShadowLayer(dp(12f), 0f, 0f, Color.CYAN)
        canvas.drawText("ARROW", 0f, subtitleSize * 1.25f, paint)

        paint.clearShadowLayer()
        paint.alpha = 255

        canvas.restore()
    }

    private fun drawSwoosh(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unit: Float,
        t: Float
    ) {
        val p = progress(t, 6.8f, 7.2f)
        val radius = unit * 0.24f

        path.reset()
        path.moveTo(cx - radius * 1.45f, cy + radius * 0.65f)
        path.cubicTo(
            cx - radius * 1.9f, cy - radius * 1.5f,
            cx + radius * 1.45f, cy - radius * 1.6f,
            cx + radius * 1.75f, cy - radius * 0.55f
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.CYAN
        paint.alpha = (210 * p).toInt()
        paint.maskFilter = BlurMaskFilter(dp(12f), BlurMaskFilter.Blur.NORMAL)

        canvas.drawPath(path, paint)

        paint.maskFilter = null
        paint.strokeWidth = dp(2f)
        paint.alpha = (240 * p).toInt()
        canvas.drawPath(path, paint)

        paint.alpha = 255
        paint.style = Paint.Style.FILL

        drawArrowShape(
            canvas,
            cx + radius * 1.68f,
            cy - radius * 0.65f,
            -0.5f,
            unit * 0.12f,
            Color.rgb(0, 255, 230),
            (255 * p).toInt()
        )
    }
}
