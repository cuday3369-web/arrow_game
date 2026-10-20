package com.yogi.exitarrow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yogi.exitarrow.R
import com.yogi.exitarrow.game.GameEngine
import com.yogi.exitarrow.game.LevelConfig
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

enum class GameMode {
    CLASSIC,
    TIME_ATTACK,
    ZEN,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrowPuzzleScreen(
    modifier: Modifier = Modifier,
    initialLevel: Int = 1,
    initialStars: Int = 89,
    initialLives: Int = 3,
) {
    var selectedMode by remember { mutableStateOf(GameMode.CLASSIC) }
    var level by remember { mutableIntStateOf(initialLevel) }
    var unlockedLevel by remember { mutableIntStateOf(50) }
    var stars by remember { mutableIntStateOf(initialStars) }
    var lives by remember { mutableIntStateOf(initialLives) }
    var moves by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(1250) }
    var combo by remember { mutableIntStateOf(0) }

    var soundEnabled by remember { mutableStateOf(value = true) }
    var vibrationEnabled by remember { mutableStateOf(value = true) }
    var reducedMotion by remember { mutableStateOf(value = false) }

    var magnetsRemaining by remember { mutableIntStateOf(2) }
    var bombsRemaining by remember { mutableIntStateOf(2) }
    var hintsRemaining by remember { mutableIntStateOf(3) }

    var elapsedSeconds by remember { mutableIntStateOf(6) }
    var isPaused by remember { mutableStateOf(value = false) }

    var showMenuSheet by remember { mutableStateOf(value = false) }
    var showLevelMapSheet by remember { mutableStateOf(value = false) }
    var showStarShopSheet by remember { mutableStateOf(value = false) }
    var isLevelComplete by remember { mutableStateOf(value = false) }

    val (gridSize, _, difficulty) = remember(level) { LevelConfig.getLevelInfo(level) }
    var currentPuzzle by remember(level) { mutableStateOf(GameEngine.generatePuzzle(level)) }

    val totalArrows = remember(currentPuzzle) { currentPuzzle.arrows.size }
    val activeCount = currentPuzzle.activeArrows().size

    LaunchedEffect(isPaused) {
        while (!isPaused) {
            delay(1.seconds)
            elapsedSeconds++
        }
    }

    val displayTimerText = remember(elapsedSeconds) {
        String.format(java.util.Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090E1D))
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderBar(
                starsCount = stars,
                onMenuClick = { showMenuSheet = true },
                onExitClick = { showLevelMapSheet = true },
                onStarsClick = { showStarShopSheet = true },
            )

            GameModeSelectorBar(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "LEVEL $level",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⏱ $displayTimerText",
                    color = Color(0xFFFFCF5B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringResource(R.string.score_fmt, score),
                    color = Color(0xFF5FF0B1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (level <= 3) {
                val tutorialText = when (level) {
                    1 -> "💡 Tap the highlighted glowing arrow to slither it off!"
                    2 -> "💡 Free unblocked outer arrows first to clear inner ones!"
                    else -> "💡 Drag with 1 finger to move board · Pinch to zoom!"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = tutorialText,
                        color = Color(0xFF5FF0B1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.moves_fmt, moves),
                    color = Color(0xFF97A6C1),
                    fontSize = 13.sp,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lives_label) + " ",
                        color = Color(0xFF97A6C1),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "❤".repeat(lives),
                        color = Color(0xFFFF5678),
                        fontSize = 13.sp,
                    )
                }

                Text(
                    text = stringResource(R.string.arrows_fmt, activeCount),
                    color = Color(0xFF97A6C1),
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                LightningArrowBoard(
                    puzzle = currentPuzzle,
                    showTutorial = level <= 3,
                    onArrowCleared = {
                        combo++
                        score += 10 * combo
                        val remaining = currentPuzzle.activeArrows().size
                        if (remaining == 0) {
                            stars += 10 + (level * 3)
                            if (level >= unlockedLevel) unlockedLevel = level + 1
                            isLevelComplete = true
                        }
                    },
                    onArrowBlocked = {
                        combo = 0
                        if (lives > 1) {
                            lives--
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Text(
                text = stringResource(R.string.grid_info_fmt, difficulty, gridSize, gridSize),
                color = Color(0xFF97A6C1),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (magnetsRemaining > 0) {
                            val clearArrow = currentPuzzle.activeArrows().firstOrNull { GameEngine.isArrowClear(currentPuzzle, it) }
                            if (clearArrow != null) {
                                magnetsRemaining--
                                score += 50
                                clearArrow.active = false
                                currentPuzzle = currentPuzzle.copy()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B6EFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_magnet, magnetsRemaining),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }

                Button(
                    onClick = {
                        if (bombsRemaining > 0) {
                            val targetArrow = currentPuzzle.activeArrows().firstOrNull()
                            if (targetArrow != null) {
                                bombsRemaining--
                                score += 30
                                targetArrow.active = false
                                currentPuzzle = currentPuzzle.copy()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF73A0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_bomb, bombsRemaining),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }

                Button(
                    onClick = {
                        if (hintsRemaining > 0) {
                            hintsRemaining--
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCF5B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_hint, hintsRemaining),
                        color = Color(0xFF090E1D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        currentPuzzle = GameEngine.generatePuzzle(level)
                        moves = 0
                        combo = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5678)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_reset),
                        color = Color(0xFF090E1D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }

                Button(
                    onClick = { isPaused = !isPaused },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42E8FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Text(
                        text = if (isPaused) "▶ RESUME" else stringResource(R.string.btn_pause),
                        color = Color(0xFF090E1D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isLevelComplete,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1E293B),
                shadowElevation = 24.dp,
                modifier = Modifier.padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "⚡ LEVEL $level CLEARED! ⚡",
                        color = Color(0xFF42E8FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "⭐ ⭐ ⭐",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = "Arrows Exited:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(text = "$totalArrows / $totalArrows", color = Color(0xFF5FF0B1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = "Stars Earned:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(text = "+${10 + (level * 3)} ★", color = Color(0xFFFFCF5B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = "Next Difficulty:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(text = LevelConfig.getLevelInfo(level + 1).difficulty, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF42E8FF),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            isLevelComplete = false
                            level++
                            currentPuzzle = GameEngine.generatePuzzle(level)
                        },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 14.dp),
                        ) {
                            Text(
                                text = "NEXT LEVEL ➔",
                                color = Color(0xFF090E1D),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }

        if (showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMenuSheet = false },
                containerColor = Color(0xFF1E293B),
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "⚙️ GAME SETTINGS",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Sound Effects", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF42E8FF)),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Haptic Vibration", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { vibrationEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF42E8FF)),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Reduced Motion", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = reducedMotion,
                            onCheckedChange = { reducedMotion = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF42E8FF)),
                        )
                    }
                }
            }
        }

        if (showLevelMapSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLevelMapSheet = false },
                containerColor = Color(0xFF0F172A),
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .height(420.dp),
                ) {
                    Text(
                        text = "🗺️ LEVEL MAP SELECT",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(50) { index ->
                            val lvlNum = index + 1
                            val isUnlocked = lvlNum <= unlockedLevel
                            val isCurrent = lvlNum == level

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isCurrent -> Color(0xFF42E8FF)
                                    isUnlocked -> Color(0xFF1E293B)
                                    else -> Color(0xFF090E1D)
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clickable(enabled = isUnlocked) {
                                        level = lvlNum
                                        currentPuzzle = GameEngine.generatePuzzle(level)
                                        showLevelMapSheet = false
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isUnlocked) "$lvlNum\n⭐⭐" else "🔒",
                                        color = if (isCurrent) Color(0xFF090E1D) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showStarShopSheet) {
            ModalBottomSheet(
                onDismissRequest = { showStarShopSheet = false },
                containerColor = Color(0xFF1E293B),
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "⭐ STAR SHOP",
                        color = Color(0xFFFFCF5B),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "+1 Life ❤ (20 ★)", color = Color.White, fontSize = 15.sp)
                        Button(
                            onClick = {
                                if (stars >= 20) {
                                    stars -= 20
                                    lives++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5678)),
                        ) {
                            Text("BUY")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "+1 Magnet 🧲 (25 ★)", color = Color.White, fontSize = 15.sp)
                        Button(
                            onClick = {
                                if (stars >= 25) {
                                    stars -= 25
                                    magnetsRemaining++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B6EFF)),
                        ) {
                            Text("BUY")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "+1 Hint 💡 (15 ★)", color = Color.White, fontSize = 15.sp)
                        Button(
                            onClick = {
                                if (stars >= 15) {
                                    stars -= 15
                                    hintsRemaining++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCF5B)),
                        ) {
                            Text("BUY", color = Color(0xFF090E1D))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameModeSelectorBar(
    modifier: Modifier = Modifier,
    selectedMode: GameMode = GameMode.CLASSIC,
    onModeSelected: (GameMode) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            val label = when (mode) {
                GameMode.CLASSIC -> stringResource(R.string.mode_classic)
                GameMode.TIME_ATTACK -> stringResource(R.string.mode_time_attack)
                GameMode.ZEN -> stringResource(R.string.mode_zen)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Color(0xFF42E8FF) else Color(0xFF222E48),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onModeSelected(mode) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color(0xFF090E1D) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderBar(
    modifier: Modifier = Modifier,
    starsCount: Int = 89,
    onMenuClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    onStarsClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF222E48),
            modifier = Modifier.clickable(onClick = onMenuClick),
        ) {
            Text(
                text = stringResource(R.string.btn_menu),
                color = Color(0xFF42E8FF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Text(
            text = stringResource(R.string.brand_title),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF222E48),
                modifier = Modifier.clickable(onClick = onStarsClick),
            ) {
                Text(
                    text = "★ $starsCount",
                    color = Color(0xFFFFCF5B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF222E48),
                modifier = Modifier.clickable(onClick = onExitClick),
            ) {
                Text(
                    text = stringResource(R.string.btn_exit),
                    color = Color(0xFFFF5678),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740)
@Composable
fun ArrowPuzzleScreenPreview() {
    ArrowPuzzleScreen()
}
