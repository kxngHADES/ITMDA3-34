package com.example.swiftq.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/** The four keyframes of the launch animation (Figma frames 00, 00b, 00c, 00d). */
enum class SplashPhase { Blank, Logo, Name, Loading }

private val SplashGradientStart = Color(0xFF3AA0FF)
private val SplashGradientMid = Color(0xFF0A7DFF)
private val SplashGradientEnd = Color(0xFF0040C8)
private val SplashAccent = Color(0xFFBFE0FF)
private val SplashIconShadow = Color(0x59001A66)

private const val PhaseTweenMillis = 500
private const val LoaderDotCount = 3

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableStateOf(SplashPhase.Blank) }
    var activeDot by remember { mutableIntStateOf(0) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(150)
        phase = SplashPhase.Logo
        delay(700)
        phase = SplashPhase.Name
        delay(800)
        phase = SplashPhase.Loading
        for (dot in 1 until LoaderDotCount) {
            activeDot = dot
            delay(450)
        }
        currentOnFinished()
    }

    SplashScreenContent(phase = phase, activeDot = activeDot, modifier = modifier)
}

@Composable
fun SplashScreenContent(
    phase: SplashPhase,
    activeDot: Int,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = phase, label = "splash")

    val iconSize by transition.animateDp(
        transitionSpec = {
            if (targetState == SplashPhase.Logo) {
                spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
            } else {
                tween(PhaseTweenMillis)
            }
        },
        label = "iconSize",
    ) {
        when (it) {
            SplashPhase.Blank -> 56.dp
            SplashPhase.Logo -> 132.dp
            else -> 120.dp
        }
    }
    val iconAlpha by transition.animateFloat(
        transitionSpec = { tween(250) },
        label = "iconAlpha",
    ) { if (it == SplashPhase.Blank) 0f else 1f }
    // Icon and glow share a centre, measured from the middle of the screen.
    val iconCenterY by transition.animateDp(
        transitionSpec = { tween(PhaseTweenMillis) },
        label = "iconCenterY",
    ) { if (it >= SplashPhase.Name) (-96).dp else (-26).dp }
    val glowSize by transition.animateDp(
        transitionSpec = { tween(PhaseTweenMillis) },
        label = "glowSize",
    ) {
        when (it) {
            SplashPhase.Blank -> 40.dp
            SplashPhase.Logo -> 300.dp
            SplashPhase.Name -> 340.dp
            SplashPhase.Loading -> 360.dp
        }
    }
    val textAlpha by transition.animateFloat(
        transitionSpec = { tween(PhaseTweenMillis) },
        label = "textAlpha",
    ) { if (it >= SplashPhase.Name) 1f else 0f }
    val textSlide by transition.animateDp(
        transitionSpec = { tween(PhaseTweenMillis) },
        label = "textSlide",
    ) { if (it >= SplashPhase.Name) 0.dp else 24.dp }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(splashGradient(size)) },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, iconCenterY.roundToPx()) }
                .size(glowSize)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
                    ),
                    shape = CircleShape,
                ),
        )

        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, iconCenterY.roundToPx()) }
                .size(iconSize)
                .graphicsLayer { alpha = iconAlpha }
                .dropShadow(
                    shape = RoundedCornerShape(percent = 33),
                    shadow = Shadow(
                        radius = 14.dp,
                        color = SplashIconShadow,
                        offset = DpOffset(0.dp, 12.dp),
                    ),
                ),
        )

        // 76dp tall block whose top sits 8dp above the middle of the screen once settled.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, (30.dp + textSlide).roundToPx()) }
                .graphicsLayer { alpha = textAlpha },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val wordmarkStyle = TextStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 44.dp.toFixedSp(),
                    lineHeight = 52.dp.toFixedSp(),
                    letterSpacing = (-0.02).em,
                )
                Text(text = "Swift", color = Color.White, style = wordmarkStyle, softWrap = false)
                Text(text = "Q", color = SplashAccent, style = wordmarkStyle, softWrap = false)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = Color.White.copy(alpha = 0.82f),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.dp.toFixedSp(),
                    letterSpacing = 0.32.em,
                ),
                softWrap = false,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 126.dp)
                .height(14.dp)
                .graphicsLayer { alpha = textAlpha },
        ) {
            repeat(LoaderDotCount) { index ->
                LoaderDot(active = index == activeDot)
            }
        }
    }
}

@Composable
private fun LoaderDot(active: Boolean) {
    val size by animateDpAsState(if (active) 14.dp else 10.dp, label = "dotSize")
    val color by animateColorAsState(
        if (active) Color.White else SplashAccent.copy(alpha = 0.5f),
        label = "dotColor",
    )
    Box(modifier = Modifier.size(size).background(color, CircleShape))
}

/** The brand lockup is artwork, so it keeps its designed size regardless of font scale. */
@Composable
private fun Dp.toFixedSp() = with(LocalDensity.current) { toSp() }

/** 121.59° three-stop gradient from the design, scaled to whatever size the screen is. */
private fun splashGradient(size: Size): Brush {
    val angle = Math.toRadians(121.59)
    val dx = sin(angle).toFloat()
    val dy = -cos(angle).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        0.14286f to SplashGradientStart,
        0.5f to SplashGradientMid,
        0.85714f to SplashGradientEnd,
        start = Offset(center.x - dx * half, center.y - dy * half),
        end = Offset(center.x + dx * half, center.y + dy * half),
    )
}

@Preview(name = "00 · Splash", widthDp = 393, heightDp = 852)
@Composable
private fun SplashBlankPreview() {
    SwiftQTheme { SplashScreenContent(phase = SplashPhase.Blank, activeDot = 0) }
}

@Preview(name = "00b · Splash (logo)", widthDp = 393, heightDp = 852)
@Composable
private fun SplashLogoPreview() {
    SwiftQTheme { SplashScreenContent(phase = SplashPhase.Logo, activeDot = 0) }
}

@Preview(name = "00c · Splash (name)", widthDp = 393, heightDp = 852)
@Composable
private fun SplashNamePreview() {
    SwiftQTheme { SplashScreenContent(phase = SplashPhase.Name, activeDot = 0) }
}

@Preview(name = "00d · Splash (loading)", widthDp = 393, heightDp = 852)
@Composable
private fun SplashLoadingPreview() {
    SwiftQTheme { SplashScreenContent(phase = SplashPhase.Loading, activeDot = 2) }
}
