package com.yogi.exitarrow.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import com.yogi.exitarrow.Direction
import com.yogi.exitarrow.game.GameEngine
import com.yogi.exitarrow.game.LightningArrow
import com.yogi.exitarrow.game.LightningPuzzle
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun LightningArrowBoard(
    puzzle: LightningPuzzle,
    modifier: Modifier = Modifier,
    showTutorial: Boolean = false,
    onArrowCleared: (LightningArrow) -> Unit = {},
    onArrowBlocked: (LightningArrow) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val animProgressMap = remember(puzzle) { mutableMapOf<Int, Animatable<Float, *>>() }
    val shakeMap = remember(puzzle) { mutableMapOf<Int, Animatable<Float, *>>() }

    var scale by remember(puzzle) { mutableFloatStateOf(1f) }
    var panX by remember(puzzle) { mutableFloatStateOf(0f) }
    var panY by remember(puzzle) { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val tutorialTarget = remember(puzzle) {
        puzzle.arrows.firstOrNull { GameEngine.isArrowClear(puzzle, it) }
    }

    Box(
        modifier = modifier.pointerInput(puzzle) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var isDraggingOrZooming = false
                var dragAccumulator = Offset.Zero
                val touchSlop = viewConfiguration.touchSlop

                do {
                    val event = awaitPointerEvent()
                    val pointerCount = event.changes.size

                    val (panChange, zoomFactor) = if (pointerCount > 1) {
                        Pair(event.calculatePan(), event.calculateZoom())
                    } else {
                        val singleChange = event.changes.first()
                        Pair(singleChange.positionChange(), 1f)
                    }

                    if ((panChange != Offset.Zero) || (zoomFactor != 1f)) {
                        dragAccumulator += panChange
                        if ((dragAccumulator.getDistance() > touchSlop) || (zoomFactor != 1f)) {
                            isDraggingOrZooming = true
                        }

                        if (isDraggingOrZooming) {
                            scale = (scale * zoomFactor).coerceIn(0.5f, 6.0f)
                            panX += panChange.x
                            panY += panChange.y
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })

                if (!isDraggingOrZooming) {
                    val tapOffset = down.position
                    val rows = puzzle.rows
                    val cols = puzzle.cols
                    val cellW = size.width.toFloat() / cols
                    val cellH = size.height.toFloat() / rows
                    val cellSize = minOf(cellW, cellH)

                    val offsetX = (size.width - (cellSize * cols)) / 2f
                    val offsetY = (size.height - (cellSize * rows)) / 2f

                    val center = Offset(size.width / 2f, size.height / 2f)
                    val boardX = center.x + ((tapOffset.x - panX - center.x) / scale)
                    val boardY = center.y + ((tapOffset.y - panY - center.y) / scale)
                    val boardTapOffset = Offset(boardX, boardY)

                    val tappedArrow = findTappedArrow(
                        puzzle = puzzle,
                        tapOffset = boardTapOffset,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        cellSize = cellSize,
                    )

                    if ((tappedArrow != null) && tappedArrow.active) {
                        val arrowId = tappedArrow.id
                        if (GameEngine.isArrowClear(puzzle, tappedArrow)) {
                            val anim = animProgressMap.getOrPut(arrowId) { Animatable(0f) }
                            coroutineScope.launch {
                                anim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                )
                                tappedArrow.active = false
                                onArrowCleared(tappedArrow)
                            }
                        } else {
                            onArrowBlocked(tappedArrow)
                            val shakeAnim = shakeMap.getOrPut(arrowId) { Animatable(0f) }
                            coroutineScope.launch {
                                shakeAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 180),
                                )
                                shakeAnim.snapTo(0f)
                            }
                        }
                    }
                }
            }
        },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panX
                    translationY = panY
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            val rows = puzzle.rows
            val cols = puzzle.cols

            val cellW = size.width / cols
            val cellH = size.height / rows
            val cellSize = minOf(cellW, cellH)

            val offsetX = (size.width - (cellSize * cols)) / 2f
            val offsetY = (size.height - (cellSize * rows)) / 2f

            drawDotGrid(
                rows = rows,
                cols = cols,
                offsetX = offsetX,
                offsetY = offsetY,
                cellSize = cellSize,
            )

            val strokeWidthPx = cellSize * 0.22f
            val dotRadiusPx = cellSize * 0.22f
            val arrowHeadSizePx = cellSize * 0.42f
            val cornerRadiusPx = cellSize * 0.35f
            val pathEffect = PathEffect.cornerPathEffect(cornerRadiusPx)

            puzzle.arrows.forEach { arrow ->
                if (!arrow.active) return@forEach

                val animProgress = animProgressMap[arrow.id]?.value ?: 0f
                val shakeValue = shakeMap[arrow.id]?.value ?: 0f

                val shakeOffsetPx = if (shakeValue > 0f) {
                    val shakeMagnitude = cellSize * 0.15f * sin(shakeValue * Math.PI * 6).toFloat()
                    when (arrow.direction) {
                        Direction.LEFT, Direction.RIGHT -> Offset(0f, shakeMagnitude)
                        Direction.UP, Direction.DOWN -> Offset(shakeMagnitude, 0f)
                    }
                } else Offset.Zero

                val exitDistPx = size.width.coerceAtLeast(size.height) * animProgress
                val exitVector = when (arrow.direction) {
                    Direction.LEFT -> Offset(-exitDistPx, 0f)
                    Direction.RIGHT -> Offset(exitDistPx, 0f)
                    Direction.UP -> Offset(0f, -exitDistPx)
                    Direction.DOWN -> Offset(0f, exitDistPx)
                }

                drawSingleLightningArrow(
                    arrow = arrow,
                    offsetX = offsetX + shakeOffsetPx.x + exitVector.x,
                    offsetY = offsetY + shakeOffsetPx.y + exitVector.y,
                    cellSize = cellSize,
                    strokeWidthPx = strokeWidthPx,
                    dotRadiusPx = dotRadiusPx,
                    arrowHeadSizePx = arrowHeadSizePx,
                    pathEffect = pathEffect,
                    isShaking = shakeValue > 0f,
                )
            }

            if (showTutorial && tutorialTarget != null && tutorialTarget.active) {
                val headR = tutorialTarget.headRow
                val headC = tutorialTarget.headCol
                val cx = offsetX + (headC * cellSize) + (cellSize / 2f)
                val cy = offsetY + (headR * cellSize) + (cellSize / 2f)

                drawCircle(
                    color = Color(0xFF42E8FF).copy(alpha = pulseAlpha),
                    radius = cellSize * 0.6f,
                    center = Offset(cx, cy),
                    style = Stroke(width = cellSize * 0.08f),
                )
            }
        }
    }
}

private fun DrawScope.drawDotGrid(
    rows: Int,
    cols: Int,
    offsetX: Float,
    offsetY: Float,
    cellSize: Float,
) {
    val dotColor = Color(0x33FFFFFF)
    val dotRadius = cellSize * 0.05f

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val cx = offsetX + (c * cellSize) + (cellSize / 2f)
            val cy = offsetY + (r * cellSize) + (cellSize / 2f)
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(cx, cy),
            )
        }
    }
}

private fun DrawScope.drawSingleLightningArrow(
    arrow: LightningArrow,
    offsetX: Float,
    offsetY: Float,
    cellSize: Float,
    strokeWidthPx: Float,
    dotRadiusPx: Float,
    arrowHeadSizePx: Float,
    pathEffect: PathEffect,
    isShaking: Boolean,
) {
    val points = arrow.path
    if (points.size < 2) return

    val path = Path()
    val startPx = Offset(
        offsetX + (points.first().second * cellSize) + (cellSize / 2f),
        offsetY + (points.first().first * cellSize) + (cellSize / 2f),
    )
    path.moveTo(startPx.x, startPx.y)

    for (i in 1 until points.size) {
        val pt = Offset(
            offsetX + (points[i].second * cellSize) + (cellSize / 2f),
            offsetY + (points[i].first * cellSize) + (cellSize / 2f),
        )
        path.lineTo(pt.x, pt.y)
    }

    val arrowColor = if (isShaking) Color(0xFFFF3366) else arrow.color

    drawPath(
        path = path,
        color = Color(0xCC090E1D),
        style = Stroke(
            width = strokeWidthPx * 1.3f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = pathEffect,
        ),
    )

    drawPath(
        path = path,
        color = arrowColor,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = pathEffect,
        ),
    )

    drawCircle(
        color = arrowColor,
        radius = dotRadiusPx,
        center = startPx,
    )

    val (lastRow, lastCol) = points.last()
    val (prevRow, prevCol) = points[points.size - 2]

    val lastPx = Offset(
        offsetX + (lastCol * cellSize) + (cellSize / 2f),
        offsetY + (lastRow * cellSize) + (cellSize / 2f),
    )
    val prevPx = Offset(
        offsetX + (prevCol * cellSize) + (cellSize / 2f),
        offsetY + (prevRow * cellSize) + (cellSize / 2f),
    )

    val angle = atan2(lastPx.y - prevPx.y, lastPx.x - prevPx.x)

    drawLightningHead(
        tip = lastPx,
        angle = angle,
        size = arrowHeadSizePx,
        color = arrowColor,
    )
}

private fun DrawScope.drawLightningHead(
    tip: Offset,
    angle: Float,
    size: Float,
    color: Color,
) {
    val wingAngle = 0.55f
    val wingLen = size * 0.95f

    val leftWing = Offset(
        tip.x - (wingLen * cos(angle - wingAngle)),
        tip.y - (wingLen * sin(angle - wingAngle)),
    )
    val rightWing = Offset(
        tip.x - (wingLen * cos(angle + wingAngle)),
        tip.y - (wingLen * sin(angle + wingAngle)),
    )

    val headPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(leftWing.x, leftWing.y)
        lineTo(rightWing.x, rightWing.y)
        close()
    }

    drawPath(
        path = headPath,
        color = color,
    )
}

private fun findTappedArrow(
    puzzle: LightningPuzzle,
    tapOffset: Offset,
    offsetX: Float,
    offsetY: Float,
    cellSize: Float,
): LightningArrow? {
    val touchThreshold = cellSize * 0.8f

    return puzzle.arrows.firstOrNull { arrow ->
        if (!arrow.active) return@firstOrNull false

        arrow.path.any { (r, c) ->
            val cx = offsetX + (c * cellSize) + (cellSize / 2f)
            val cy = offsetY + (r * cellSize) + (cellSize / 2f)
            hypot(tapOffset.x - cx, tapOffset.y - cy) <= touchThreshold
        }
    }
}
