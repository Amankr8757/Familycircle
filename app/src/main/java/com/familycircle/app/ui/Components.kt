package com.familycircle.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Map("Map", Icons.Outlined.Map),
    Places("Places", Icons.Outlined.LocationOn),
    History("History", Icons.Outlined.History),
    Profile("Profile", Icons.Outlined.Person)
}

private val NavBarHeight = 68.dp
private val NavLift = 12.dp
private val NavCircleSize = 54.dp
private val NavCircleCenterY = 20.dp   // circle centre, measured from the top edge of the bar
private val NavNotchGap = 5.dp         // empty gap between the circle and the valley wall
private val NavNotchShoulder = 50.dp   // half-width of the valley where it meets the top edge
private val NavCorner = 30.dp
private val NavInset = 20.dp

/**
 * Valley (cup) shape: smooth shoulders flowing down into a semicircle that is
 * concentric with the active circle, so the dip always hugs the button evenly.
 * The path must already be at (cx - sh, 0); it ends at (cx + sh, 0).
 */
private fun Path.valley(cx: Float, cy: Float, r: Float, sh: Float) {
    val k = (sh - r) * 0.6f
    val ky = cy * 0.65f
    cubicTo(cx - sh + k, 0f, cx - r, cy - ky, cx - r, cy)
    arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 180f, -180f, false)
    cubicTo(cx + r, cy - ky, cx + sh - k, 0f, cx + sh, 0f)
}

/**
 * Frosted-glass bottom bar with a valley that follows the selected tab.
 * Everything that animates is read in the draw/layout phase, so the bar never recomposes while moving.
 *
 * History is NOT shown in the bar (only 4 tabs). It still opens from the Home bell / "See all".
 */
@Composable
fun BottomNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // History bar mein nahi dikhegi
    val tabs = remember { Tab.values().filter { it != Tab.History } }
    val selectedIndex = tabs.indexOf(selected).let { if (it < 0) 0 else it }
    val shownTab = tabs[selectedIndex]
    val density = LocalDensity.current

    val position = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "navPosition"
    )

    val fillBrush = remember(dark) {
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF2B2B47).copy(alpha = 0.90f), Color(0xFF1B1B2E).copy(alpha = 0.86f))
            else listOf(Color.White.copy(alpha = 0.90f), Color(0xFFEDEBFF).copy(alpha = 0.86f))
        )
    }
    val edgeBrush = remember(dark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.35f else 0.95f),
                Color.White.copy(alpha = if (dark) 0.06f else 0.25f)
            )
        )
    }
    val idleIcon = if (dark) Color(0xFFB8B8D4) else Muted
    val notch = remember { Path() }
    val curve = remember { Path() }
    val barClip = remember { Path() }

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(NavBarHeight + NavLift)) {
            val widthPx = with(density) { maxWidth.toPx() }
            val insetPx = with(density) { NavInset.toPx() }
            val slotPx = (widthPx - 2 * insetPx) / tabs.size

            // Glass bar (drawn manually: no shadow layer, no recomposition while animating)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(NavBarHeight).drawBehind {
                    val corner = NavCorner.toPx()
                    val cy = NavCircleCenterY.toPx()
                    val r = NavCircleSize.toPx() / 2f + NavNotchGap.toPx()
                    val sh = NavNotchShoulder.toPx()
                    val cx = insetPx + slotPx * (position.value + 0.5f)
                    val stroke = Stroke(1.dp.toPx())

                    // Edge line of the valley
                    curve.reset()
                    curve.moveTo(cx - sh, 0f)
                    curve.valley(cx, cy, r, sh)

                    // Area that is cut out of the bar
                    notch.reset()
                    notch.moveTo(cx - sh, -40f)
                    notch.lineTo(cx - sh, 0f)
                    notch.valley(cx, cy, r, sh)
                    notch.lineTo(cx + sh, -40f)
                    notch.close()

                    // Keeps the edge line inside the rounded bar (needed on the first / last tab)
                    barClip.reset()
                    barClip.addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner)))

                    clipPath(notch, clipOp = ClipOp.Difference) {
                        drawRoundRect(brush = fillBrush, cornerRadius = CornerRadius(corner, corner))
                        drawRoundRect(brush = edgeBrush, cornerRadius = CornerRadius(corner, corner), style = stroke)
                    }
                    clipPath(barClip) {
                        drawPath(curve, brush = edgeBrush, style = stroke)
                    }
                }
            )

            // Tab icons
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(NavBarHeight).padding(horizontal = NavInset)) {
                tabs.forEach { tab ->
                    val iconAlpha = animateFloatAsState(
                        targetValue = if (tab == shownTab) 0f else 1f,
                        animationSpec = tween(200),
                        label = "iconAlpha"
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            tab.icon, tab.label, tint = idleIcon,
                            modifier = Modifier.size(24.dp).graphicsLayer { alpha = iconAlpha.value }
                        )
                    }
                }
            }

            // Floating active circle (position read in the layout phase)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (insetPx + slotPx * (position.value + 0.5f) - NavCircleSize.toPx() / 2f).roundToInt(),
                            (NavLift + NavCircleCenterY - NavCircleSize / 2).roundToPx()
                        )
                    }
                    .size(NavCircleSize)
                    .shadow(10.dp, CircleShape, ambientColor = Color(0xFF6A5CF0), spotColor = Color(0xFF6A5CF0))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF8B6BFF), Color(0xFF4A7BFF))))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = shownTab, animationSpec = tween(220), label = "navIcon") { tab ->
                    Icon(tab.icon, tab.label, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/** Light card: border only (no elevation shadow) so long scrolling lists stay smooth. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatusChip(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun InitialsAvatar(name: String, size: Int = 48, bg: Color = Indigo, photoBase64: String? = null) {
    val photo = androidx.compose.runtime.remember(photoBase64) { decodeFamilyPhoto(photoBase64)?.asImageBitmap() }
    Box(Modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(photo, contentDescription = "$name profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp)
        }
    }
}