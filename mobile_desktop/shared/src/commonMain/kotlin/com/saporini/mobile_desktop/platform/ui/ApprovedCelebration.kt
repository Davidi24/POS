package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.ApprovedApplication
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A restaurant was just approved (user, 2026-11-21: "animation when I accept a restaurant"): confetti bursts over the page,
 * a green circle pops in and draws its tick, and the card says what happens next and how many still wait. Done closes it
 * and the approved card leaves the list.
 */
@Composable
internal fun ApprovedCelebration(approved: ApprovedApplication, onDone: () -> Unit) {
    val application = approved.application
    val owner = application.owner?.displayName?.ifBlank { null } ?: "The owner"
    val pop = remember(approved.tick) { Animatable(0.4f) }
    val fade = remember(approved.tick) { Animatable(0f) }
    LaunchedEffect(approved.tick) {
        coroutineScope {
            launch { fade.animateTo(1f, tween(180)) }
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
        }
    }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(20.dp).widthIn(max = 460.dp).fillMaxWidth()
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = fade.value }
                    .shadow(24.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White)
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TickBadge(approved.tick)
                Text("APPROVED", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp, color = Kit.Green)
                Text("${application.name} is approved", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink,
                    textAlign = TextAlign.Center)
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NextLine(Icons.Outlined.MarkEmailRead, "$owner got an email to set a password and becomes its owner.")
                    NextLine(Icons.Outlined.Storefront, "Its first branch, Main, is ready to set up.")
                    NextLine(Icons.Outlined.FormatListNumbered, "It's last on your restaurant list.")
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (approved.stillWaiting) {
                            0L -> "No more applications waiting."
                            1L -> "1 more application is waiting."
                            else -> "${approved.stillWaiting} more applications are waiting."
                        },
                        Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted
                    )
                    KitButton("Done", onDone)
                }
            }
            // On top of everything, and it never takes a click.
            Confetti(approved.tick, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NextLine(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = Kit.Green)
        }
        Text(text, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Ink)
    }
}

/** The green circle: it pops in, a ring spreads from it and the white tick draws itself. */
@Composable
private fun TickBadge(tick: Int) {
    val circle = remember(tick) { Animatable(0f) }
    val draw = remember(tick) { Animatable(0f) }
    val ring = remember(tick) { Animatable(0f) }
    LaunchedEffect(tick) {
        coroutineScope {
            launch { circle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
            launch { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
            launch {
                delay(220)
                draw.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
        }
    }
    Canvas(Modifier.size(112.dp)) {
        val half = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        val core = half * 0.62f
        drawCircle(Kit.Green.copy(alpha = 0.22f * (1f - ring.value)), radius = core + (half - core) * ring.value, center = center)
        drawCircle(Kit.GreenSoft, radius = core * 1.18f * circle.value.coerceAtMost(1.2f), center = center)
        drawCircle(Kit.Green, radius = core * circle.value, center = center)
        if (draw.value > 0f) {
            val path = Path().apply {
                moveTo(size.width * 0.37f, size.height * 0.51f)
                lineTo(size.width * 0.46f, size.height * 0.60f)
                lineTo(size.width * 0.64f, size.height * 0.41f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * draw.value, part, true)
            drawPath(part, Color.White, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private class Piece(val angle: Float, val speed: Float, val color: Color, val width: Float, val height: Float, val spin: Float, val delay: Float)

private val ConfettiColors = listOf(Kit.Green, Color(0xFF7FB069), Kit.Amber, Color(0xFFF4C542), Kit.Blue, Color(0xFFE46A8B), Kit.Purple)

/** Paper bits shot up and out from above the card's middle, slowed by the air and pulled down, fading at the end. */
@Composable
private fun Confetti(tick: Int, modifier: Modifier) {
    val pieces = remember(tick) {
        val random = Random(tick * 7919 + 13)
        List(110) {
            // Mostly upwards, spread to both sides.
            val angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 1.25).toFloat()
            Piece(angle, 650f + random.nextFloat() * 900f, ConfettiColors[random.nextInt(ConfettiColors.size)],
                5f + random.nextFloat() * 6f, 8f + random.nextFloat() * 8f, (random.nextFloat() - 0.5f) * 4f, random.nextFloat() * 0.18f)
        }
    }
    val time = remember(tick) { Animatable(0f) }
    LaunchedEffect(tick) { time.animateTo(1f, tween(2800, easing = LinearEasing)) }
    if (time.value >= 1f) return
    Canvas(modifier) {
        val seconds = time.value * 2.8f
        val origin = Offset(size.width / 2, size.height / 2 - 150.dp.toPx())
        val drag = 2.4f
        val gravity = 820.dp.toPx()
        val fade = (1f - ((seconds - 1.9f) / 0.9f)).coerceIn(0f, 1f)
        pieces.forEach { piece ->
            val s = seconds - piece.delay
            if (s <= 0f) return@forEach
            val speed = piece.speed.dp.toPx()
            val travel = speed / drag * (1f - exp(-drag * s))
            val x = origin.x + cos(piece.angle) * travel + sin(s * 6f + piece.spin) * 6.dp.toPx()
            val y = origin.y + sin(piece.angle) * travel + gravity * s * s / 2f * 0.55f
            val w = piece.width.dp.toPx()
            val h = piece.height.dp.toPx() * (0.35f + 0.65f * kotlin.math.abs(cos(s * 7f + piece.spin)))
            rotate(piece.spin * s * 220f, Offset(x, y)) {
                drawRect(piece.color.copy(alpha = fade), Offset(x - w / 2, y - h / 2), Size(w, h))
            }
        }
    }
}
