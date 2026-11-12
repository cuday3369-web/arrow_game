package com.yogi.exitarrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Native Android Canvas implementation of the EXIT ARROW intro.
 *
 * Phase 1: six arrowheads move outward from the exact screen centre.
 *          Their shafts remain connected to the centre along the same spiral.
 * Phase 2: after the last arrowhead reaches the screen edge, all six tails
 *          move outward together along their own spiral paths.
 * Particles emit continuously until Phase 1 ends, then leave the screen
 * without bouncing. The existing MainActivity constructor/callback API is kept.
 */
class ExitArrowIntroView(
    context: Context,
    private val onFinished: (() -> Unit)? = null
) : View(context) {

    companion object {
        private const val TWO_PI = (PI * 2.0).toFloat()

        // Global intro playback speed. 2.0f is 200% playback (twice normal speed).
        // All intro phases use the same clock so their timing stays synchronized.
        private const val INTRO_SPEED = 2.0f

        private const val HEAD_START_U = 0.045f
        private const val TAIL_START_U = 0f
        private const val ARROW_GROWTH = 0.20f
        private const val HEAD_NOTCH_BACK_RATIO = 0.60f
        private const val HEAD_WIDTH_RATIO = 0.88f
        private const val HEAD_OVERSHOOT_U = 0.16f
        private const val HEAD_OVERSHOOT_MS = 450L
        private const val TAIL_EXIT_MS = 2_800L
        private const val PARTICLES_PER_SECOND = 90f
        private const val MAX_ACTIVE_PARTICLES = 1_200
    }

    private val config = ExitArrowConfig.load(context)
    private val headDurationMs = config.durationMs
    private val totalDurationMs = headDurationMs + TAIL_EXIT_MS

    private data class Arrow(
        val color: Int,
        val baseAngle: Float,
        var boundaryU: Float = 1f
    )

    private data class Particle(
        val baseAngle: Float,
        var progress: Float,
        val speed: Float,
        var x: Float,
        var y: Float,
        var previousX: Float,
        var previousY: Float,
        val radius: Float,
        val color: Int
    )

    private val palette = config.colors()

    private val arrows = MutableList(6) { index ->
        Arrow(
            color = palette[index % palette.size],
            baseAngle = -PI.toFloat() / 2f + index * TWO_PI / 6f
        )
    }

    private val particles = ArrayList<Particle>(MAX_ACTIVE_PARTICLES)
    private val random = Random(9217)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    private val arrowPath = Path()

    private var started = false
    private var completed = false
    private var startTime = 0L
    private var previousFrameTime = 0L
    private var emissionAccumulator = 0f
    private var particleEmissionStopped = false

    private var cx = 0f
    private var cy = 0f
    private var shortSide = 0f
    private var diagonal = 0f
    private var density = 1f
    private var titleBaseline = 0f
    private var lastKnownWidth = 0
    private var lastKnownHeight = 0
    private var lastBoundaryU = 1f

    init {
        // Canvas glow uses software shadow layers for consistent Android rendering.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
    }

    fun start() {
        if (started) return
        started = true
        completed = false
        particleEmissionStopped = false
        emissionAccumulator = 0f
        particles.clear()
        startTime = SystemClock.uptimeMillis()
        previousFrameTime = startTime
        visibility = VISIBLE
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastKnownWidth = 0
        lastKnownHeight = 0
        updateGeometry(w, h)
    }

    private fun updateGeometry(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w == lastKnownWidth && h == lastKnownHeight) return

        lastKnownWidth = w
        lastKnownHeight = h
        density = resources.displayMetrics.density
        shortSide = min(w, h).toFloat()
        diagonal = kotlin.math.hypot(w.toFloat(), h.toFloat())

        // The animation origin is the exact screen centre.
        cx = w * 0.5f
        cy = h * 0.5f
        titleBaseline = h * config.titleYFraction

        arrows.forEach { it.boundaryU = findBoundary(it.baseAngle) }
        lastBoundaryU = arrows.maxOfOrNull { it.boundaryU } ?: 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!started || width <= 0 || height <= 0) return

        updateGeometry(width, height)

        val now = SystemClock.uptimeMillis()

        // Use a scaled animation clock for the complete intro: arrows,
        // synchronized tail release, particle emission/physics, logo,
        // and the completion callback all run at normal 100% playback speed.
        val realElapsedMs = (now - startTime).coerceAtLeast(0L)
        val elapsed = (realElapsedMs * INTRO_SPEED).toLong()

        val realDeltaMs = (now - previousFrameTime).coerceIn(0L, 40L)
        val deltaMs = (realDeltaMs * INTRO_SPEED).toLong()
        previousFrameTime = now

        val seconds = elapsed / 1000f
        if (elapsed >= headDurationMs) particleEmissionStopped = true

        val headPhase = (elapsed.toFloat() / headDurationMs).coerceIn(0f, 1f)
        val headEased = easeInOutCubic(headPhase)

        val headU: Float
        val tailU: Float

        if (elapsed <= headDurationMs) {
            headU = lerp(HEAD_START_U, lastBoundaryU, headEased)
            tailU = TAIL_START_U
        } else {
            val tailElapsed = elapsed - headDurationMs
            val overshootProgress = easeOutCubic(
                tailElapsed.toFloat() / HEAD_OVERSHOOT_MS
            )
            headU = lastBoundaryU + HEAD_OVERSHOOT_U * overshootProgress

            val tailProgress = easeInOutCubic(
                tailElapsed.toFloat() / TAIL_EXIT_MS
            )
            tailU = lerp(
                TAIL_START_U,
                lastBoundaryU + HEAD_OVERSHOOT_U + 0.10f,
                tailProgress
            )
        }

        canvas.drawColor(Color.rgb(8, 8, 18))
        drawBackgroundGlow(canvas, seconds)
        drawStars(canvas, seconds)
        drawCoreGlow(canvas, seconds, headPhase)

        emitParticles(deltaMs / 1000f)
        updateParticles(deltaMs / 1000f)
        drawParticles(canvas)

        arrows.forEach { arrow ->
            drawArrow(canvas, arrow, headU, tailU)
        }

        // Keep the original EXIT ARROW title treatment, revealing it as
        // the tails begin their synchronized exit from the centre.
        val logoStart = headDurationMs - 250L
        val logoProgress = (
                (elapsed - logoStart).toFloat() /
                        (totalDurationMs - logoStart).toFloat()
                ).coerceIn(0f, 1f)
        if (elapsed >= logoStart) {
            drawLogo(canvas, logoProgress, seconds)
        }

        // Keep this view animating after the arrows leave until every
        // already-emitted particle has also crossed a screen boundary.
        if (elapsed >= totalDurationMs && particles.isEmpty() && !completed) {
            completed = true
            onFinished?.invoke()
        } else if (!completed) {
            postInvalidateOnAnimation()
        }
    }

    // ============================================
    // SHARED SPIRAL GEOMETRY
    // ============================================

    private fun spiralPosition(baseAngle: Float, u: Float): Pair<Float, Float> {
        val radius = diagonal * 0.85f * u
        val angle = baseAngle - config.spiralTurns * TWO_PI * u
        return Pair(
            cx + cos(angle) * radius,
            cy + sin(angle) * radius
        )
    }

    private fun tangentAt(baseAngle: Float, u: Float): Pair<Float, Float> {
        val epsilon = 0.0005f
        val before = spiralPosition(baseAngle, max(0f, u - epsilon))
        val after = spiralPosition(baseAngle, u + epsilon)
        val dx = after.first - before.first
        val dy = after.second - before.second
        val length = kotlin.math.hypot(dx, dy).coerceAtLeast(0.0001f)
        return Pair(dx / length, dy / length)
    }

    private fun findBoundary(baseAngle: Float): Float {
        var previousU = 0f
        val samples = 2_500

        for (i in 1..samples) {
            val u = i.toFloat() / samples
            val p = spiralPosition(baseAngle, u)
            if (p.first <= 0f || p.first >= width || p.second <= 0f || p.second >= height) {
                var low = previousU
                var high = u
                repeat(24) {
                    val mid = (low + high) * 0.5f
                    val test = spiralPosition(baseAngle, mid)
                    val outside = test.first <= 0f || test.first >= width ||
                            test.second <= 0f || test.second >= height
                    if (outside) high = mid else low = mid
                }
                return (low + high) * 0.5f
            }
            previousU = u
        }
        return 1f
    }

    // ============================================
    // ARROW DRAWING
    // Navigation-arrow silhouette matching the supplied reference:
    // sharp tip, equal rear wings, centred concave notch, and
    // a spiral shaft that tapers into the notch without a kink.
    // ============================================

    /**
     * Arc length of this Archimedean spiral between two parameter values.
     * The closed-form speed is R * sqrt(1 + (k*u)^2), integrated with
     * Simpson's rule. This lets the head dimensions stay proportional
     * even when the spiral curves more strongly near the screen edge.
     */
    private fun spiralArcLength(fromU: Float, toU: Float): Float {
        val a = min(fromU, toU).coerceAtLeast(0f)
        val b = max(fromU, toU).coerceAtLeast(0f)
        if (b - a <= 0.000001f) return 0f

        val intervals = 8
        val step = (b - a) / intervals
        val k = abs(config.spiralTurns * TWO_PI)

        fun speedFactor(u: Float): Float {
            val ku = k * u
            return kotlin.math.sqrt(1f + ku * ku)
        }

        var sum = speedFactor(a) + speedFactor(b)
        for (i in 1 until intervals) {
            val u = a + step * i
            sum += if (i % 2 == 0) 2f * speedFactor(u) else 4f * speedFactor(u)
        }

        val radialScale = diagonal * 0.85f
        return radialScale * step * sum / 3f
    }

    /** Find the spiral parameter whose arc distance behind headU is targetPx. */
    private fun parameterBehindDistance(
        headU: Float,
        targetPx: Float,
        minU: Float
    ): Float {
        if (targetPx <= 0f) return headU
        if (spiralArcLength(minU, headU) <= targetPx) return minU

        var low = minU
        var high = headU
        repeat(18) {
            val mid = (low + high) * 0.5f
            if (spiralArcLength(mid, headU) > targetPx) {
                low = mid
            } else {
                high = mid
            }
        }
        return (low + high) * 0.5f
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun drawArrow(
        canvas: Canvas,
        arrow: Arrow,
        headU: Float,
        tailU: Float
    ) {
        if (tailU >= headU) return

        val availableArc = spiralArcLength(tailU, headU)
        if (availableArc < dp(2f)) return

        // One scale controls every part of the head. The available-path
        // limit makes the arrow naturally smaller at the centre and while
        // the tail catches up, without distorting its proportions.
        val growthProgress = (headU / lastBoundaryU.coerceAtLeast(0.001f))
            .coerceIn(0f, 1f)
        val growthScale = 1f + ARROW_GROWTH * growthProgress
        val desiredHeadLength = dp(34f) * growthScale
        val headLength = min(desiredHeadLength, availableArc * 0.82f)
        if (headLength < dp(2.5f)) return

        // The reference icon has a head width just under its length.
        // Keep this ratio fixed throughout the complete animation.
        val headWidth = headLength * HEAD_WIDTH_RATIO
        val bodyWidth = dp(7f) * growthScale
        val halfBody = bodyWidth * 0.5f
        val baseAngle = arrow.baseAngle

        val tip = spiralPosition(baseAngle, headU)

        // The notch sits 60% of the head length behind the tip. Its
        // centre is taken from the actual spiral, so the shaft joins it
        // without a detached straight segment.
        val notchDistance = headLength * HEAD_NOTCH_BACK_RATIO
        val notchU = parameterBehindDistance(
            headU,
            notchDistance,
            tailU
        )
        val notch = spiralPosition(baseAngle, notchU)

        // Use the notch-to-tip chord as the local head axis. This keeps
        // the notch, tip and both wings on one perfectly symmetrical frame.
        val axisDx = tip.first - notch.first
        val axisDy = tip.second - notch.second
        val axisLength = kotlin.math.hypot(axisDx, axisDy).coerceAtLeast(0.0001f)
        val axisX = axisDx / axisLength
        val axisY = axisDy / axisLength
        val normalX = -axisY
        val normalY = axisX

        // The wing line is one complete head length behind the tip.
        // The notch is nearer the tip, creating the distinct inward V.
        val wingCenterX = tip.first - axisX * headLength
        val wingCenterY = tip.second - axisY * headLength
        val halfHeadWidth = headWidth * 0.5f

        val leftWingX = wingCenterX + normalX * halfHeadWidth
        val leftWingY = wingCenterY + normalY * halfHeadWidth
        val rightWingX = wingCenterX - normalX * halfHeadWidth
        val rightWingY = wingCenterY - normalY * halfHeadWidth

        // Build the shaft from the exact tail parameter to the exact
        // notch parameter. Its width eases smoothly to zero at the notch.
        val span = (notchU - tailU).coerceAtLeast(0f)
        if (span <= 0.000001f) return

        val steps = max(24, (span * 900f).toInt().coerceAtMost(180))
        val leftX = FloatArray(steps + 1)
        val leftY = FloatArray(steps + 1)
        val rightX = FloatArray(steps + 1)
        val rightY = FloatArray(steps + 1)

        val taperDistance = min(headLength * 0.42f, spiralArcLength(tailU, notchU) * 0.72f)
            .coerceAtLeast(dp(0.5f))

        for (i in 0..steps) {
            val u = lerp(tailU, notchU, i.toFloat() / steps)
            val p = spiralPosition(baseAngle, u)
            val tangent = tangentAt(baseAngle, u)

            val remainingToNotch = spiralArcLength(u, notchU)
            val taperT = (1f - remainingToNotch / taperDistance).coerceIn(0f, 1f)
            val widthFactor = 1f - smoothStep(taperT)
            val localHalfWidth = halfBody * widthFactor

            val nx = -tangent.second
            val ny = tangent.first

            leftX[i] = p.first + nx * localHalfWidth
            leftY[i] = p.second + ny * localHalfWidth
            rightX[i] = p.first - nx * localHalfWidth
            rightY[i] = p.second - ny * localHalfWidth
        }

        // Smooth, mirrored concave curves from the central notch to
        // each rear wing. The outer tip-to-wing edges remain crisp.
        val innerControlAlong = headLength * 0.12f
        val innerControlAcross = headWidth * 0.10f

        val lowerControl1X = notch.first - axisX * innerControlAlong - normalX * innerControlAcross
        val lowerControl1Y = notch.second - axisY * innerControlAlong - normalY * innerControlAcross
        val lowerControl2X = rightWingX + axisX * (headLength * 0.16f) + normalX * (headWidth * 0.07f)
        val lowerControl2Y = rightWingY + axisY * (headLength * 0.16f) + normalY * (headWidth * 0.07f)

        val upperControl1X = leftWingX + axisX * (headLength * 0.16f) - normalX * (headWidth * 0.07f)
        val upperControl1Y = leftWingY + axisY * (headLength * 0.16f) - normalY * (headWidth * 0.07f)
        val upperControl2X = notch.first - axisX * innerControlAlong + normalX * innerControlAcross
        val upperControl2Y = notch.second - axisY * innerControlAlong + normalY * innerControlAcross

        // Single silhouette:
        // tail-left -> notch -> lower curved side -> right wing -> tip
        // -> left wing -> upper curved side -> notch -> tail-right.
        arrowPath.reset()
        arrowPath.moveTo(leftX[0], leftY[0])

        for (i in 1..steps) {
            arrowPath.lineTo(leftX[i], leftY[i])
        }

        arrowPath.cubicTo(
            lowerControl1X, lowerControl1Y,
            lowerControl2X, lowerControl2Y,
            rightWingX, rightWingY
        )

        // Outer edge: straight and sharp, like the reference icon.
        arrowPath.lineTo(tip.first, tip.second)
        arrowPath.lineTo(leftWingX, leftWingY)

        arrowPath.cubicTo(
            upperControl1X, upperControl1Y,
            upperControl2X, upperControl2Y,
            notch.first, notch.second
        )

        for (i in steps - 1 downTo 0) {
            arrowPath.lineTo(rightX[i], rightY[i])
        }
        arrowPath.close()

        // Glow pass, followed by a clean solid fill.
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = arrow.color
        fillPaint.alpha = 90
        fillPaint.setShadowLayer(dp(14f), 0f, 0f, arrow.color)
        canvas.drawPath(arrowPath, fillPaint)

        fillPaint.clearShadowLayer()
        fillPaint.alpha = 255
        canvas.drawPath(arrowPath, fillPaint)
    }

    // ============================================
    // CONTINUOUS PARTICLE EMISSION / PHYSICS
    // ============================================

    private fun emitParticles(deltaSeconds: Float) {
        if (particleEmissionStopped || deltaSeconds <= 0f) return

        emissionAccumulator += deltaSeconds * PARTICLES_PER_SECOND
        while (emissionAccumulator >= 1f && particles.size < MAX_ACTIVE_PARTICLES) {
            createParticle()
            emissionAccumulator -= 1f
        }
        if (particles.size >= MAX_ACTIVE_PARTICLES) {
            emissionAccumulator = 0f
        }
    }

    private fun createParticle() {
        val angle = random.nextFloat() * TWO_PI
        particles.add(
            Particle(
                baseAngle = angle,
                progress = 0f,
                // Particle speed is halved in updateParticles(). Each particle
                // uses the same spiral equation as the six arrows.
                speed = 0.26f + random.nextFloat() * 0.16f,
                x = cx,
                y = cy,
                previousX = cx,
                previousY = cy,
                radius = dp(0.8f + random.nextFloat() * 1.8f),
                color = palette[random.nextInt(palette.size)]
            )
        )
    }

    private fun updateParticles(deltaSeconds: Float) {
        if (deltaSeconds <= 0f || particles.isEmpty()) return

        val dt = deltaSeconds.coerceAtMost(0.04f)
        val iterator = particles.iterator()

        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.previousX = particle.x
            particle.previousY = particle.y

            // Same spiral formula as the arrows, with half-speed progression.
            particle.progress += particle.speed * 0.5f * dt

            val position = spiralPosition(
                particle.baseAngle,
                particle.progress
            )
            particle.x = position.first
            particle.y = position.second

            val margin = dp(35f)
            if (
                particle.x < -margin || particle.x > width + margin ||
                particle.y < -margin || particle.y > height + margin ||
                particle.progress > 2f
            ) {
                iterator.remove()
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            // Short trail from the previous frame to the current spiral point.
            linePaint.color = p.color
            linePaint.alpha = 105
            linePaint.strokeWidth = max(dp(0.6f), p.radius * 0.65f)
            linePaint.setShadowLayer(dp(5f), 0f, 0f, p.color)
            canvas.drawLine(
                p.previousX,
                p.previousY,
                p.x,
                p.y,
                linePaint
            )
            linePaint.clearShadowLayer()

            fillPaint.color = p.color
            fillPaint.alpha = 235
            fillPaint.setShadowLayer(dp(7f), 0f, 0f, p.color)
            canvas.drawCircle(p.x, p.y, p.radius, fillPaint)
            fillPaint.clearShadowLayer()
            fillPaint.alpha = 255
        }
        linePaint.alpha = 255
    }

    // ============================================
    // BACKGROUND / BRANDING
    // ============================================

    private fun drawBackgroundGlow(canvas: Canvas, seconds: Float) {
        val radius = shortSide * 0.90f
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                Color.argb(42, 0, 115, 255),
                Color.argb(18, 95, 30, 160),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        glowPaint.shader = null
    }

    private fun drawCoreGlow(canvas: Canvas, seconds: Float, progress: Float) {
        val pulse = 1f + 0.06f * sin(seconds * 5.5f)
        val radius = shortSide * (0.055f + 0.04f * progress) * pulse
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                Color.argb(52, 0, 235, 255),
                Color.argb(22, 75, 55, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, glowPaint)
        glowPaint.shader = null
    }

    private fun drawStars(canvas: Canvas, seconds: Float) {
        for (i in 0 until 82) {
            val x = ((i * 97 + 23) % 997) / 997f * width
            val y = ((i * 173 + 41) % 991) / 991f * height
            val twinkle = 0.25f + 0.75f * abs(
                sin(seconds * (0.8f + (i % 4) * 0.17f) + i)
            )
            fillPaint.color = Color.argb((105f * twinkle).toInt(), 165, 205, 255)
            canvas.drawCircle(x, y, dp(0.55f + (i % 3) * 0.22f), fillPaint)
        }
    }

    private fun drawLogo(canvas: Canvas, raw: Float, seconds: Float) {
        val p = raw.coerceIn(0f, 1f)
        val reveal = easeOutBack(p)
        val alpha = (255f * easeOutCubic(p)).toInt().coerceIn(0, 255)
        val textSize = shortSide * 0.095f
        val baseline = titleBaseline + (1f - reveal) * shortSide * 0.045f
        val pulse = 0.88f + 0.12f * sin(seconds * 3.1f)

        textPaint.typeface = android.graphics.Typeface.create(
            "sans-serif-black", android.graphics.Typeface.BOLD
        )
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = textSize
        textPaint.style = Paint.Style.FILL
        textPaint.alpha = alpha
        textPaint.setShadowLayer(shortSide * 0.035f * pulse, 0f, 0f, Color.rgb(0, 205, 255))
        textPaint.shader = LinearGradient(
            0f, baseline - textSize, width.toFloat(), baseline,
            intArrayOf(
                Color.rgb(0, 240, 255),
                Color.WHITE,
                Color.rgb(115, 190, 255),
                Color.rgb(255, 70, 175)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        val measured = textPaint.measureText("EXIT ARROW")
        val maxTextWidth = width * 0.88f
        val scale = if (measured > maxTextWidth) maxTextWidth / measured else 1f

        canvas.save()
        canvas.scale(scale, 1f, cx, baseline)
        canvas.drawText("EXIT ARROW", cx, baseline, textPaint)
        canvas.restore()

        textPaint.shader = null
        textPaint.clearShadowLayer()
        textPaint.alpha = 255

        textPaint.typeface = android.graphics.Typeface.create(
            "sans-serif-medium", android.graphics.Typeface.NORMAL
        )
        textPaint.textSize = textSize * 0.19f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb((175f * alpha / 255f).toInt(), 170, 215, 255)
        textPaint.setShadowLayer(shortSide * 0.012f, 0f, 0f, Color.CYAN)
        canvas.drawText("MOVE BEYOND LIMITS", cx, baseline + textSize * 0.42f, textPaint)
        textPaint.clearShadowLayer()
        textPaint.alpha = 255

        val scan = easeInOutCubic((p - 0.25f) / 0.65f)
        val scanY = baseline + textSize * 0.20f
        linePaint.shader = LinearGradient(
            cx - shortSide * 0.28f + shortSide * 0.56f * scan,
            scanY,
            cx + shortSide * 0.28f + shortSide * 0.56f * scan,
            scanY,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(alpha, 90, 245, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        linePaint.strokeWidth = max(dp(1f), shortSide * 0.0025f)
        canvas.drawLine(
            cx - shortSide * 0.30f, scanY,
            cx + shortSide * 0.30f, scanY,
            linePaint
        )
        linePaint.shader = null
    }

    // ============================================
    // EASING
    // ============================================

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun dp(value: Float): Float = value * density

    private fun easeOutCubic(x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        return 1f - (1f - p) * (1f - p) * (1f - p)
    }

    private fun easeInOutCubic(x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        return if (p < 0.5f) {
            4f * p * p * p
        } else {
            1f - ((-2f * p + 2f).let { it * it * it }) / 2f
        }
    }

    private fun easeOutBack(x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val q = p - 1f
        return 1f + c3 * q * q * q + c1 * q * q
    }
}
