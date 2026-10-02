@file:OptIn(ExperimentalTextApi::class)

package app.undo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.undo.R
import app.undo.engine.Undoability

/** Design tokens beyond Material's slots. */
@Immutable
data class UndoColors(
    val paper: Color,
    val surface: Color,
    val sunk: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val hairline: Color,
    val rewind: Color,
    val rewindText: Color,
    val onInk: Color,
    val good: Color, val goodBg: Color,
    val warn: Color, val warnBg: Color,
    val guide: Color, val guideBg: Color,
    val bad: Color, val badBg: Color,
    val isDark: Boolean,
) {
    fun statusFg(u: Undoability) = when (u) {
        Undoability.UNDOABLE -> good
        Undoability.PARTIAL -> warn
        Undoability.GUIDED -> guide
        Undoability.NOT_UNDOABLE -> bad
    }
    fun statusBg(u: Undoability) = when (u) {
        Undoability.UNDOABLE -> goodBg
        Undoability.PARTIAL -> warnBg
        Undoability.GUIDED -> guideBg
        Undoability.NOT_UNDOABLE -> badBg
    }
}

// Contrast notes (WCAG 2.1): muted on paper ≈ 5.9:1, rewindText on paper ≈ 5.3:1, status fg on status bg ≥ 4.8:1.
private val Light = UndoColors(
    paper = Color(0xFFF5F2EC), surface = Color(0xFFFFFFFF), sunk = Color(0xFFECE8DF),
    ink = Color(0xFF15161A), inkSoft = Color(0xFF34363D), muted = Color(0xFF5C5F68), hairline = Color(0xFFE0DBD0),
    rewind = Color(0xFFE8553A), rewindText = Color(0xFFB13A22), onInk = Color(0xFFF5F2EC),
    good = Color(0xFF17673F), goodBg = Color(0xFFE2F1E8),
    warn = Color(0xFF7D5200), warnBg = Color(0xFFFAEDD2),
    guide = Color(0xFF4340A8), guideBg = Color(0xFFE8E7F9),
    bad = Color(0xFFA8231B), badBg = Color(0xFFFAE3E0),
    isDark = false,
)

private val Dark = UndoColors(
    paper = Color(0xFF0E0F12), surface = Color(0xFF17181D), sunk = Color(0xFF1F2127),
    ink = Color(0xFFF2EFE8), inkSoft = Color(0xFFD3D1CB), muted = Color(0xFFA3A6AF), hairline = Color(0xFF2A2C33),
    rewind = Color(0xFFFF7556), rewindText = Color(0xFFFF8F75), onInk = Color(0xFF0E0F12),
    good = Color(0xFF6BD9A2), goodBg = Color(0xFF12291E),
    warn = Color(0xFFF2BC55), warnBg = Color(0xFF2C2312),
    guide = Color(0xFFB0ADFF), guideBg = Color(0xFF1E1D3B),
    bad = Color(0xFFFF8F86), badBg = Color(0xFF341A18),
    isDark = true,
)

val LocalUndo = staticCompositionLocalOf { Light }

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp
}

object Radius {
    val s = RoundedCornerShape(10.dp)
    val m = RoundedCornerShape(16.dp)
    val l = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Grotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

private val UndoType = Typography(
    displayLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em),
    displayMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em),
    headlineLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.015).em),
    headlineSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.005.em),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em),
)

@Composable
fun UndoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = c.ink, onPrimary = c.onInk, secondary = c.rewind, onSecondary = c.onInk,
        background = c.paper, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted, outline = c.hairline, outlineVariant = c.hairline,
        error = c.bad, errorContainer = c.badBg,
    ) else lightColorScheme(
        primary = c.ink, onPrimary = c.onInk, secondary = c.rewind, onSecondary = Color.White,
        background = c.paper, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted, outline = c.hairline, outlineVariant = c.hairline,
        error = c.bad, errorContainer = c.badBg,
    )
    CompositionLocalProvider(LocalUndo provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = UndoType,
            shapes = Shapes(small = Radius.s, medium = Radius.m, large = Radius.l),
            content = content,
        )
    }
}
