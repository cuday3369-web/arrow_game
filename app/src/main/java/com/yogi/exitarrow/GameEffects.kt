package com.yogi.exitarrow

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object GameEffects {

    private val palette = intArrayOf(
        Color.rgb(255, 72, 133),
        Color.rgb(255, 184, 62),
        Color.rgb(68, 224, 255),
        Color.rgb(145, 255, 112),
        Color.rgb(177, 117, 255),
        Color.rgb(255, 104, 78)
    )

    /** Small spring-like press animation for buttons and cards. */
    fun buttonPress(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f

        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    /** Twist-and-scale spring press animation for buttons using DynamicAnimation physics engine. */
    fun twistSpringPress(view: View) {
        view.animate().cancel()
        view.scaleX = 0.88f
        view.scaleY = 0.88f
        view.rotation = -2.5f

        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        val springRot = SpringAnimation(view, DynamicAnimation.ROTATION, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }

        springX.start()
        springY.start()
        springRot.start()
    }

    /** Attach spring click touch listener to buttons and views. */
    fun attachSpringFeedback(view: View, reducedMotion: Boolean = false) {
        view.setOnTouchListener { v, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                if (!reducedMotion) {
                    twistSpringPress(v)
                }
            }
            false
        }
    }

    /** Elastic spring drop-in entrance animation for the puzzle board using SpringAnimation physics. */
    fun boardEntranceBounce(view: View, delayMs: Long = 60L) {
        springViewTransition(
            view = view,
            initialTranslationY = -50f,
            initialScale = 0.72f,
            stiffness = SpringForce.STIFFNESS_LOW,
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        )
    }

    /** Smooth physics spring transition using androidx.dynamicanimation physics engine. */
    fun springViewTransition(
        view: View,
        initialTranslationY: Float = -60f,
        initialScale: Float = 0.7f,
        stiffness: Float = SpringForce.STIFFNESS_LOW,
        dampingRatio: Float = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
    ) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = view.resources.displayMetrics.density * initialTranslationY
        view.scaleX = initialScale
        view.scaleY = initialScale

        view.animate().alpha(1f).setDuration(220).start()

        val springY = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = dampingRatio
        }
        val springScaleX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = dampingRatio
        }
        val springScaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = dampingRatio
        }

        springY.start()
        springScaleX.start()
        springScaleY.start()
    }

    /** Celebratory pulse wave and 3D scale-tilt for board victory. */
    fun boardVictoryWave(view: View, onEnd: (() -> Unit)? = null) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .rotation(3f)
            .setDuration(220)
            .withEndAction {
                view.animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .rotation(-2f)
                    .setDuration(180)
                    .withEndAction {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .rotation(0f)
                            .setDuration(220)
                            .setInterpolator(OvershootInterpolator(2f))
                            .withEndAction {
                                onEnd?.invoke()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    /** Elastic spring scale drop-in animation for dialog card containers using DynamicAnimation physics. */
    fun popInDialogCard(cardView: View) {
        springViewTransition(
            view = cardView,
            initialTranslationY = -80f,
            initialScale = 0.5f,
            stiffness = SpringForce.STIFFNESS_LOW,
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        )
    }

    /** Sparkle burst animation for star count label or reward stars. */
    fun starSparkleBurst(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.45f)
            .scaleY(1.45f)
            .rotation(18f)
            .setDuration(200)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .rotation(0f)
                    .setDuration(280)
                    .setInterpolator(OvershootInterpolator(2.5f))
                    .start()
            }
            .start()
    }

    /** Rhythmic double heartbeat pulse animation for life/heart indicators. */
    fun heartBeatPulse(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.28f)
            .scaleY(1.28f)
            .setDuration(130)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(110)
                    .withEndAction {
                        view.animate()
                            .scaleX(1.20f)
                            .scaleY(1.20f)
                            .setDuration(120)
                            .withEndAction {
                                view.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(180)
                                    .setInterpolator(OvershootInterpolator(2f))
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    /** Subtle pulse rhythm on timer tick. */
    fun timerTickPulse(view: View) {
        if (view.scaleX != 1.0f) return
        view.animate().cancel()
        view.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(120)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(180)
                    .start()
            }
            .start()
    }

    /** Handles touch feedback for buttons. */
    fun tap(view: View, reducedMotion: Boolean = false) {
        if (!reducedMotion) {
            twistSpringPress(view)
        }
    }

    /** Celebratory animation for level loading or completion. */
    fun celebration(view: View, reducedMotion: Boolean = false) {
        if (!reducedMotion) {
            popIn(view)
        }
    }

    /** Gentle repeating pulse. Keep the returned animator so it can be stopped. */
    fun pulse(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.055f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.055f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.88f, 1f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1100
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 150
            start()
        }
    }

    /** Makes a title bounce into view. */
    fun popIn(view: View) {
        inertialBounce(view)
    }

    /** Opening Logo / Title Drop-In with elastic bounce physics. */
    fun openingLogoBounceDropIn(view: View, delayMs: Long = 80L) {
        view.animate().cancel()
        view.translationY = -80f
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setStartDelay(delayMs)
            .setDuration(550)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()
    }

    /** Gentle floating wiggle animation for opening title logos, background elements, or start buttons. */
    fun gentleIdleWiggle(view: View, floatDistanceDp: Float = 5f): ObjectAnimator {
        val density = view.resources.displayMetrics.density
        val distancePx = floatDistanceDp * density
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -distancePx, 0f).apply {
            duration = 2400L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** Real-world physics spring bounce using DynamicAnimation physics engine. */
    fun physicsSpringBounce(
        view: View,
        stiffness: Float = SpringForce.STIFFNESS_MEDIUM,
        dampingRatio: Float = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
    ) {
        view.scaleX = 0.5f
        view.scaleY = 0.5f

        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = dampingRatio
        }
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = dampingRatio
        }

        springX.start()
        springY.start()
    }

    /** Bouncy physics inertial scale animation for badges, stars, and pop-ups with optional delay. */
    fun inertialBounce(view: View, startDelayMs: Long = 0L, tension: Float = 2.2f) {
        view.animate().cancel()
        view.scaleX = 0.25f
        view.scaleY = 0.25f
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(startDelayMs)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(tension))
            .start()
    }

    /** Staggered entrance pop-in for multiple UI elements in sequence. */
    fun staggeredPopIn(views: List<View>, stepDelayMs: Long = 120L) {
        views.forEachIndexed { index, v ->
            inertialBounce(v, startDelayMs = index * stepDelayMs)
        }
    }

    /** Brief shake for a blocked move or invalid action. */
    fun shake(view: View) {
        view.animate().cancel()
        view.translationX = 0f

        ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f, -10f, 10f, -7f, 7f, -3f, 3f, 0f
        ).apply {
            duration = 360
            start()
        }
    }

    /** Fade and scale a view out before hiding it. */
    fun fadeOut(view: View, durationMs: Long = 220L) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(durationMs)
            .withEndAction {
                view.visibility = View.GONE
            }
            .start()
    }

    /** Animate a score or reward number ticker dynamically with comma formatting (e.g. "1,250"). */
    fun numberTicker(
        view: TextView,
        from: Int,
        to: Int,
        prefix: String = "",
        suffix: String = ""
    ): ValueAnimator {
        return ValueAnimator.ofInt(from, to).apply {
            duration = 900
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                val formatted = String.format(java.util.Locale.US, "%,d", value)
                view.text = "$prefix$formatted$suffix"
            }
            start()
        }
    }

    /** Organic breathing glow / pulsing alpha animation for mythic & epic active rewards. */
    fun organicBreathingGlow(
        view: View,
        minAlpha: Float = 0.55f,
        maxAlpha: Float = 1.0f,
        durationMs: Long = 1400L
    ): ObjectAnimator {
        view.alpha = minAlpha
        return ObjectAnimator.ofFloat(view, View.ALPHA, minAlpha, maxAlpha, minAlpha).apply {
            duration = durationMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** Legacy countUp alias pointing to numberTicker. */
    fun countUp(
        view: TextView,
        from: Int,
        to: Int,
        suffix: String = ""
    ): ValueAnimator = numberTicker(view, from, to, prefix = "", suffix = suffix)

    /** A star-like celebratory entrance for a reward view. */
    fun rewardPop(view: View) {
        view.animate().cancel()
        view.scaleX = 0f
        view.scaleY = 0f
        view.rotation = -18f
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .alpha(1f)
            .setDuration(650)
            .setInterpolator(BounceInterpolator())
            .start()
    }

    /** Vibration with optional toggle check. */
    fun vibrate(context: Context, enabled: Boolean, durationMs: Long = 25L) {
        if (!enabled) return
        vibrate(context, durationMs)
    }

    /** Short vibration for a successful move. */
    fun vibrate(context: Context, durationMs: Long = 25L) {
        try {
            val vibrator: Vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(
                        Context.VIBRATOR_MANAGER_SERVICE
                    ) as VibratorManager
                    manager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs.coerceIn(1L, 1000L),
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs.coerceIn(1L, 1000L))
            }
        } catch (_: Exception) {
            // Vibration is optional; unsupported devices should continue normally.
        }
    }

    /** A stronger, two-stage vibration for a level completion. */
    fun celebrateVibration(context: Context) {
        vibrate(context, 70L)
    }

    fun randomColor(): Int = palette[Random.nextInt(palette.size)]
}

/**
 * Lightweight Canvas-based confetti effect.
 *
 * Usage:
 * val confetti = ConfettiView(context)
 * parent.addView(confetti)
 * confetti.start()
 *
 * Add it over the celebration screen. It does not intercept touches.
 */
class ConfettiView(context: Context) : View(context) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var size: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var color: Int,
        var alpha: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<Particle>()
    private var running = false
    private var startTime = 0L

    private val colors = intArrayOf(
        Color.rgb(255, 74, 132),
        Color.rgb(255, 194, 74),
        Color.rgb(63, 220, 255),
        Color.rgb(139, 255, 122),
        Color.rgb(190, 128, 255),
        Color.WHITE
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        alpha = 1f
    }

    fun start() {
        particles.clear()

        val particleCount = 100
        repeat(particleCount) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width.coerceAtLeast(1),
                    y = -Random.nextFloat() * height.coerceAtLeast(1),
                    velocityX = Random.nextFloat() * 5f - 2.5f,
                    velocityY = Random.nextFloat() * 5f + 2f,
                    size = Random.nextFloat() * 9f + 4f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = Random.nextFloat() * 12f - 6f,
                    color = colors[Random.nextInt(colors.size)],
                    alpha = 255
                )
            )
        }

        running = true
        startTime = System.currentTimeMillis()
        visibility = VISIBLE
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        particles.clear()
        invalidate()
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!running) return

        val elapsed = System.currentTimeMillis() - startTime

        if (elapsed >= 3000L) {
            stop()
            return
        }

        val dt = 1f

        particles.forEach { particle ->
            particle.x += particle.velocityX * dt
            particle.y += particle.velocityY * dt
            particle.velocityY += 0.045f
            particle.rotation += particle.rotationSpeed

            val remaining = (1f - elapsed / 3000f).coerceIn(0f, 1f)
            particle.alpha = (255 * remaining).toInt()

            paint.color = particle.color
            paint.alpha = particle.alpha

            canvas.save()
            canvas.rotate(particle.rotation, particle.x, particle.y)

            canvas.drawRoundRect(
                particle.x - particle.size / 2f,
                particle.y - particle.size / 3f,
                particle.x + particle.size / 2f,
                particle.y + particle.size / 3f,
                particle.size / 5f,
                particle.size / 5f,
                paint
            )

            canvas.restore()
        }

        postInvalidateOnAnimation()
    }
}

/**
 * Glowing, floating stars for the victory screen.
 *
 * Usage:
 * val stars = StarCelebrationView(context)
 * parent.addView(stars)
 * stars.start()
 */
class StarCelebrationView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startTime = 0L
    private var running = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    fun start() {
        startTime = System.currentTimeMillis()
        running = true
        visibility = VISIBLE
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        visibility = GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!running) return

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= 2600L) {
            stop()
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val progress = elapsed / 2600f

        for (i in 0 until 14) {
            val phase = i * 0.83f
            val x = ((i * 71f + sin(progress * 5f + phase) * 35f) % w.coerceAtLeast(1f))
            val y = h * (1f - progress) + cos(phase + progress * 7f) * 24f
            val radius = 4f + (sin(phase + progress * 12f) + 1f) * 4f

            paint.color = GameEffects.randomColor()
            paint.alpha = ((1f - progress) * 220).toInt()

            canvas.drawCircle(x, y, radius, paint)
        }

        postInvalidateOnAnimation()
    }
}

/**
 * Dynamic ambient background featuring floating colorful glowing orbs and light waves.
 */
class ColorfulBackgroundView(context: Context) : View(context) {

    private class Orb(
        val xRatio: Float,
        val yRatio: Float,
        val radiusDp: Float,
        val color: Int,
        val phase: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var startTime = System.currentTimeMillis()

    private val orbs = listOf(
        Orb(0.18f, 0.18f, 150f, Color.rgb(255, 72, 133), 0f),    // Neon Pink
        Orb(0.82f, 0.22f, 170f, Color.rgb(68, 224, 255), 1.2f),  // Cyan
        Orb(0.50f, 0.55f, 180f, Color.rgb(177, 117, 255), 2.5f), // Purple
        Orb(0.22f, 0.82f, 160f, Color.rgb(255, 184, 62), 3.8f),  // Gold
        Orb(0.85f, 0.78f, 140f, Color.rgb(145, 255, 112), 5.0f)  // Lime Green
    )

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        val isNight = GameTheme.isNightMode(context)

        // Adaptive background base: Dark mode (#090E1D) or Light mode (#F0F4F9)
        canvas.drawColor(if (isNight) Color.rgb(9, 14, 29) else Color.rgb(240, 244, 249))

        // Adaptive ambient dots (Light white in Dark Mode, Light black in Light Mode)
        paint.style = Paint.Style.FILL
        paint.color = if (isNight) Color.argb(45, 255, 255, 255) else Color.argb(35, 0, 0, 0)
        
        for (i in 0 until 60) {
            val dx = ((i * 137.5f) % w)
            val dy = ((i * 219.3f) % h)
            val r = (1.5f + (i % 3) * 0.8f) * density
            canvas.drawCircle(dx, dy, r, paint)
        }

        orbs.forEach { orb ->
            val x = w * ((orb.xRatio + sin(elapsed * 0.18f + orb.phase) * 0.12f).mod(1f))
            val y = h * ((orb.yRatio + cos(elapsed * 0.15f + orb.phase) * 0.10f).mod(1f))
            val radius = orb.radiusDp * density * (1f + 0.10f * sin(elapsed * 0.45f + orb.phase))

            val gradient = RadialGradient(
                x, y, radius,
                intArrayOf(
                    Color.argb(85, Color.red(orb.color), Color.green(orb.color), Color.blue(orb.color)),
                    Color.argb(25, Color.red(orb.color), Color.green(orb.color), Color.blue(orb.color)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )

            paint.shader = gradient
            canvas.drawCircle(x, y, radius, paint)
        }
        paint.shader = null

        postInvalidateOnAnimation()
    }
}
