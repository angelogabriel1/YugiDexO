package com.yugidex.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yugidex.app.data.Card
import com.yugidex.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class LegendaryKind(val accent: Color, val proclamation: String, val subtitle: String) {
    EXODIA(PharaohGold, "EXODIA FOI LIBERTADO", "Os cinco selos do Proibido respondem ao chamado"),
    BLUE_EYES(Color(0xFFBDEBFF), "O DRAGAO BRANCO DESPERTA", "O relampago branco atravessa os ceus"),
    DARK_MAGICIAN(Color(0xFFB06CFF), "O MAGO SUPREMO SE ERGUE", "Magia negra, rompa o veu entre os mundos"),
    EGYPTIAN_GOD(PharaohGold, "UMA DIVINDADE DESCENDE", "O campo de duelo se curva ao poder de um deus"),
    RED_EYES(Color(0xFFFF4D45), "AS CHAMAS NEGRAS ARDEM", "O potencial oculto do dragao foi libertado"),
    LEGENDARY(MysticGold, "UMA LENDA FOI REVELADA", "O coracao das cartas reconhece seu poder")
}

private fun normalizeLegend(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase()

fun legendaryKind(name: String): LegendaryKind? {
    val value = normalizeLegend(name)
    return when {
        value.contains("exodia") || value.contains("do proibido") || value.contains("forbidden one") -> LegendaryKind.EXODIA
        value.contains("blue-eyes") || value.contains("olhos azuis") -> LegendaryKind.BLUE_EYES
        value.contains("dark magician") || value.contains("mago negro") || value.contains("maga negra") -> LegendaryKind.DARK_MAGICIAN
        listOf("slifer", "obelisk", "winged dragon of ra", "dragao alado de ra").any(value::contains) -> LegendaryKind.EGYPTIAN_GOD
        value.contains("red-eyes") || value.contains("olhos vermelhos") -> LegendaryKind.RED_EYES
        listOf("stardust dragon", "dragao da poeira estelar", "black luster soldier", "blue-eyes ultimate", "mago do caos").any(value::contains) -> LegendaryKind.LEGENDARY
        else -> null
    }
}

@Composable
fun LegendaryAmbient(kind: LegendaryKind) {
    val transition = rememberInfiniteTransition(label = "legendary-ambient")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "drift")
    val pulse by transition.animateFloat(.25f, .75f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.fillMaxSize()) {
        repeat(34) { index ->
            val x = ((index * 83) % 101) / 101f * size.width
            val y = size.height - (((index * 47) / 101f + drift) % 1f) * size.height
            drawCircle(kind.accent, radius = (index % 4 + 1).toFloat(), center = Offset(x, y), alpha = pulse)
        }
    }
}

@Composable
fun LegendarySummoning(card: Card, kind: LegendaryKind, onDismiss: () -> Unit) {
    val cardAlpha = remember { Animatable(0f) }
    val cardScale = remember { Animatable(.28f) }
    val darkness = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var ready by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "legendary-ritual")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (kind == LegendaryKind.EXODIA) 5200 else 7800, easing = LinearEasing)), label = "rotation")
    val reverseRotation by transition.animateFloat(360f, 0f, infiniteRepeatable(tween(9500, easing = LinearEasing)), label = "reverse")
    val energy by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "energy")
    val flicker by transition.animateFloat(.2f, 1f, infiniteRepeatable(tween(110), RepeatMode.Reverse), label = "flicker")

    LaunchedEffect(card.id) {
        darkness.animateTo(1f, tween(350))
        delay(250)
        flash.snapTo(1f)
        launch { flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
        delay(if (kind == LegendaryKind.EXODIA) 650 else 350)
        launch { cardAlpha.animateTo(1f, tween(750)) }
        cardScale.animateTo(1.08f, tween(900, easing = FastOutSlowInEasing))
        cardScale.animateTo(1f, spring(dampingRatio = .45f, stiffness = 180f))
        delay(650)
        ready = true
    }

    Box(
        Modifier.fillMaxSize()
            .background(DarkObsidian.copy(alpha = .97f * darkness.value))
            .clickable(enabled = ready, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        RitualCanvas(kind, rotation, reverseRotation, energy, flicker)

        Box(
            Modifier.size(260.dp, 380.dp)
                .blur(if (cardAlpha.value < .2f) 16.dp else 0.dp)
                .graphicsLayer {
                    alpha = cardAlpha.value
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    rotationY = (1f - cardAlpha.value) * 105f
                    shadowElevation = 30f
                }
        ) {
            AsyncImage(
                card.images.firstOrNull()?.url ?: "https://images.ygoprodeck.com/images/cards/${card.id}.jpg",
                card.localized?.name ?: card.name,
                Modifier.fillMaxSize()
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 62.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(kind.proclamation, color = kind.accent, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            Text(kind.subtitle, color = GoldGlow.copy(alpha = .82f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }

        AnimatedVisibility(
            ready,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
            enter = fadeIn(tween(450)) + slideInVertically { it / 2 }
        ) {
            Surface(color = CardViolet.copy(alpha = .88f), shape = RoundedCornerShape(30.dp), border = androidx.compose.foundation.BorderStroke(1.dp, kind.accent)) {
                Text("TOQUE PARA CONTINUAR", Modifier.padding(horizontal = 22.dp, vertical = 11.dp), color = kind.accent, fontWeight = FontWeight.Bold)
            }
        }

        Canvas(Modifier.fillMaxSize()) { drawRect(Color.White, alpha = flash.value * .9f) }
    }
}

@Composable
private fun RitualCanvas(kind: LegendaryKind, rotation: Float, reverseRotation: Float, energy: Float, flicker: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * .37f
        val accent = kind.accent

        repeat(20) { index ->
            val angle = Math.toRadians((index * 18f + rotation).toDouble())
            val inner = base * .55f
            val outer = base * (1.35f + (index % 3) * .12f)
            drawLine(
                accent,
                Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = if (index % 3 == 0) 5f else 2f,
                alpha = energy * .45f
            )
        }

        drawCircle(accent, base, center, alpha = .7f, style = Stroke(5f))
        drawCircle(GoldGlow, base * .78f, center, alpha = energy * .65f, style = Stroke(2f))

        when (kind) {
            LegendaryKind.EXODIA -> drawExodiaSeal(center, base, rotation, reverseRotation, energy)
            LegendaryKind.BLUE_EYES -> drawLightning(center, base, Color(0xFFD9F7FF), rotation, flicker, 9)
            LegendaryKind.DARK_MAGICIAN -> drawArcaneSeal(center, base, reverseRotation, accent, energy)
            LegendaryKind.EGYPTIAN_GOD -> {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = .26f), Color.Transparent)), Offset(center.x - base * .7f, 0f), Size(base * 1.4f, size.height))
                drawLightning(center, base, accent, rotation, flicker, 7)
            }
            LegendaryKind.RED_EYES -> drawFlames(center, base, rotation, energy)
            LegendaryKind.LEGENDARY -> drawArcaneSeal(center, base, reverseRotation, accent, energy)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExodiaSeal(center: Offset, radius: Float, rotation: Float, reverse: Float, energy: Float) {
    val points = List(5) { index ->
        val angle = Math.toRadians((index * 72f - 90f + rotation * .08f).toDouble())
        Offset(center.x + cos(angle).toFloat() * radius * .82f, center.y + sin(angle).toFloat() * radius * .82f)
    }
    points.forEachIndexed { index, point ->
        drawCircle(PharaohGold, radius * .15f, point, alpha = .3f + energy * .45f, style = Stroke(4f))
        drawCircle(GoldGlow, radius * .045f, point, alpha = energy)
        drawLine(PharaohGold, point, points[(index + 2) % 5], 4f, alpha = .7f)
    }
    val eyeWidth = radius * .62f
    drawOval(PharaohGold, topLeft = Offset(center.x - eyeWidth / 2, center.y - radius * .12f), size = Size(eyeWidth, radius * .24f), alpha = .24f, style = Stroke(5f))
    drawCircle(GoldGlow, radius * .08f, center, alpha = energy)
    drawCircle(MysticGold, radius * 1.08f, center, alpha = .5f, style = Stroke(3f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcaneSeal(center: Offset, radius: Float, rotation: Float, color: Color, energy: Float) {
    repeat(3) { ring -> drawCircle(color, radius * (.48f + ring * .2f), center, alpha = .35f + energy * .25f, style = Stroke((ring + 1) * 2f)) }
    repeat(8) { index ->
        val angle = Math.toRadians((index * 45f + rotation).toDouble())
        val point = Offset(center.x + cos(angle).toFloat() * radius * .72f, center.y + sin(angle).toFloat() * radius * .72f)
        drawCircle(GoldGlow, radius * .035f, point, alpha = energy)
        drawLine(color, center, point, 2f, alpha = .35f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightning(center: Offset, radius: Float, color: Color, phase: Float, flicker: Float, count: Int) {
    repeat(count) { index ->
        val startAngle = Math.toRadians((index * (360f / count) + phase * .7f).toDouble())
        val start = Offset(center.x + cos(startAngle).toFloat() * radius * 1.35f, center.y + sin(startAngle).toFloat() * radius * 1.35f)
        val path = Path().apply {
            moveTo(start.x, start.y)
            repeat(5) { step ->
                val t = (step + 1) / 6f
                val wobble = sin((phase + index * 31 + step * 73).toDouble()).toFloat() * radius * .11f
                lineTo(start.x + (center.x - start.x) * t + wobble, start.y + (center.y - start.y) * t - wobble * .4f)
            }
            lineTo(center.x, center.y)
        }
        drawPath(path, color, alpha = flicker * .8f, style = Stroke(width = if (index % 2 == 0) 5f else 2f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlames(center: Offset, radius: Float, phase: Float, energy: Float) {
    repeat(14) { index ->
        val x = center.x - radius + index * radius * 2f / 13f
        val height = radius * (.4f + ((index * 37) % 60) / 100f) * energy
        val path = Path().apply {
            moveTo(x - radius * .08f, center.y + radius)
            quadraticTo(x - radius * .2f, center.y + radius - height * .55f, x, center.y + radius - height)
            quadraticTo(x + radius * .18f, center.y + radius - height * .45f, x + radius * .08f, center.y + radius)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF342B), Color.Transparent)), alpha = .65f)
    }
}
