package com.familycircle.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familycircle.app.R

internal fun loginBase(dark: Boolean): Color = if (dark) Color(0xFF1A1A2A) else Color(0xFFEFEEFB)

/** Soft "neumorphic" raised surface: light shadow top-left, dark shadow bottom-right. */
private fun Modifier.neu(radius: Dp, dark: Boolean, depth: Dp = 8.dp): Modifier {
    val base = loginBase(dark)
    val light = if (dark) Color(0xFF29293F) else Color.White
    val shade = if (dark) Color(0xFF0B0B15) else Color(0xFFCBC7EE)
    return this.drawBehind {
        val r = radius.toPx()
        val d = depth.toPx()
        val w = size.width
        val h = size.height
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = base.toArgb()
            paint.setShadowLayer(d * 2f, -d, -d, light.toArgb())
            canvas.nativeCanvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            paint.setShadowLayer(d * 2f, d, d, shade.toArgb())
            canvas.nativeCanvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
        }
    }
}

@Composable
fun LoginScreen(
    initialName: String,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onCreateFamily: (name: String) -> Unit,
    onJoinFamily: (name: String, code: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var code by remember { mutableStateOf("") }
    val textColor = if (dark) Color(0xFFEDEDF7) else Color(0xFF1E1E2E)
    val mutedColor = if (dark) Color(0xFF9A9AB5) else Color(0xFF8A8AA3)
    val accent = if (dark) Color(0xFF9B8CFF) else Indigo

    Column(
        Modifier.fillMaxSize()
            .background(loginBase(dark))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Theme toggle
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.size(44.dp).neu(22.dp, dark, 4.dp).clip(CircleShape).clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, "Switch theme", tint = accent, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Logo tile
        Box(Modifier.size(124.dp).neu(34.dp, dark, 10.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.family_circle_icon),
                contentDescription = "Family Circle logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
            )
        }

        Spacer(Modifier.height(26.dp))
        Text("Family Circle", color = textColor, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Stay close, wherever you are.",
            color = mutedColor, fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "You choose when to share. Owners approve join requests.",
            color = mutedColor, fontSize = 12.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Main card
        Column(
            Modifier.fillMaxWidth().neu(34.dp, dark, 10.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NeuField(name, { name = it }, "Your name (e.g. Aman)", Icons.Outlined.Person, dark, accent, textColor, mutedColor)

            GradientButton("Create a family", enabled = name.isNotBlank()) { onCreateFamily(name.trim()) }

            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(mutedColor.copy(alpha = 0.3f)))
                Text("OR JOIN A FAMILY", fontSize = 11.sp, color = mutedColor, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Box(Modifier.weight(1f).height(1.dp).background(mutedColor.copy(alpha = 0.3f)))
            }

            NeuField(
                code, { code = it }, "Paste family code", Icons.Outlined.VpnKey, dark, accent, textColor, mutedColor,
                KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )

            NeuButton("Request to join", enabled = name.isNotBlank() && code.isNotBlank(), dark = dark, accent = accent) {
                onJoinFamily(name.trim(), code.trim())
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Shared: your chosen name, live location while sharing is on, battery percentage, and update time. No messages, calls, contacts, or device identifiers.",
            color = mutedColor, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NeuField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    dark: Boolean,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().height(58.dp).neu(22.dp, dark, 5.dp)) {
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            shape = shape,
            placeholder = { Text(placeholder, color = mutedColor, fontSize = 15.sp) },
            leadingIcon = { Icon(icon, null, tint = accent) },
            keyboardOptions = keyboardOptions,
            colors = TextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                cursorColor = accent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun GradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .shadow(
                if (enabled) 14.dp else 0.dp, shape,
                ambientColor = Color(0xFF6A5CF0), spotColor = Color(0xFF6A5CF0)
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF7B55F5), Color(0xFF4A7BFF))), alpha = if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NeuButton(text: String, enabled: Boolean, dark: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .neu(27.dp, dark, 5.dp)
            .clip(RoundedCornerShape(27.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = accent.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
    }
}
