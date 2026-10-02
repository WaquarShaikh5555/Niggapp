package app.undo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.undo.capture.AppInfo
import app.undo.engine.Undoability
import app.undo.ui.Fmt
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space

/** True when the user has turned animations off system-wide (respect reduced motion). */
@Composable
fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Press-to-shrink micro-interaction shared by every tappable surface. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, role: Role = Role.Button, enabled: Boolean = true): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = reducedMotion()
    val s by animateFloatAsState(if (pressed && !reduce) 0.975f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = androidx.compose.material.ripple.rememberRipple(), enabled = enabled, role = role, onClick = onClick)
}

enum class ButtonKind { Primary, Secondary, Ghost, Accent }

@Composable
fun UndoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = LocalUndo.current
    val bg = when (kind) {
        ButtonKind.Primary -> c.ink
        ButtonKind.Accent -> c.rewind
        ButtonKind.Secondary -> c.surface
        ButtonKind.Ghost -> Color.Transparent
    }
    val fg = when (kind) {
        ButtonKind.Primary -> c.onInk
        ButtonKind.Accent -> if (c.isDark) c.onInk else Color(0xFF1A0A05)
        ButtonKind.Secondary, ButtonKind.Ghost -> c.ink
    }
    val bgAnim by animateColorAsState(if (enabled) bg else c.sunk, tween(180), label = "btn")
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(Radius.m)
            .background(bgAnim)
            .then(if (kind == ButtonKind.Secondary) Modifier.border(1.dp, c.hairline, Radius.m) else Modifier)
            .pressable(onClick = onClick, enabled = enabled)
            .padding(horizontal = Space.xl, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) fg else c.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) fg else c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalUndo.current.muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier.semantics { heading() })
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Space.xxl, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(text, Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun StatusPill(u: Undoability, modifier: Modifier = Modifier, long: Boolean = false) {
    val c = LocalUndo.current
    Row(
        modifier
            .clip(Radius.pill)
            .background(c.statusBg(u))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.statusFg(u)))
        Spacer(Modifier.width(6.dp))
        Text(if (long) Fmt.undoLabel(u) else Fmt.undoShort(u), style = MaterialTheme.typography.labelMedium, color = c.statusFg(u), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, icon: ImageVector? = null) {
    val c = LocalUndo.current
    val bg by animateColorAsState(if (selected) c.ink else c.surface, tween(160), label = "chip")
    val fg = if (selected) c.onInk else c.inkSoft
    Row(
        modifier
            .clip(Radius.pill)
            .background(bg)
            .border(1.dp, if (selected) c.ink else c.hairline, Radius.pill)
            .then(if (onClick != null) Modifier.pressable(onClick, role = Role.Tab) else Modifier)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.l,
    color: Color = LocalUndo.current.surface,
    content: @Composable () -> Unit,
) {
    val c = LocalUndo.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radius.l)
            .background(color)
            .border(1.dp, c.hairline, Radius.l)
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

/** App icon if we can see the app; otherwise a tinted glyph for the event type. */
@Composable
fun AppGlyph(pkg: String?, fallback: ImageVector, size: Dp = 44.dp, tint: Color = LocalUndo.current.ink) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val bmp: ImageBitmap? = remember(pkg) {
        AppInfo.icon(ctx, pkg)?.let { d ->
            runCatching {
                val px = 96
                val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val cv = AndroidCanvas(b)
                d.setBounds(0, 0, px, px)
                d.draw(cv)
                b.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.sunk),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp == null) Icon(fallback, null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
        if (bmp != null) Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
    }
}

@Composable
fun TopBar(title: String?, onBack: (() -> Unit)?, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val c = LocalUndo.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).pressable(onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.ink) }
        } else Spacer(Modifier.width(Space.s))
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f).padding(start = Space.xs).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Shimmering placeholder block for loading states. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(10.dp)) {
    val c = LocalUndo.current
    val reduce = reducedMotion()
    val x = if (reduce) 0f else {
        val t = rememberInfiniteTransition(label = "shimmer")
        val v by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
        v
    }
    val base = c.sunk
    val hi = if (c.isDark) Color(0xFF2A2C33) else Color(0xFFF7F4EE)
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(
                colors = listOf(base, hi, base),
                start = Offset(x * 600f, 0f),
                end = Offset(x * 600f + 400f, 200f),
            ),
        ),
    )
}

@Composable
fun SkeletonEventCard() {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(44.dp), RoundedCornerShape(13.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Skeleton(Modifier.fillMaxWidth(0.7f).height(16.dp))
                Spacer(Modifier.height(Space.s))
                Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
            }
        }
    }
}

/** UNDO's mark: a rewind arc with an arrowhead, drawn so it scales crisply anywhere. */
@Composable
fun RewindMark(modifier: Modifier = Modifier, color: Color = LocalUndo.current.rewind, stroke: Dp = 6.dp) {
    Canvas(modifier) {
        val w = size.minDimension
        val sw = stroke.toPx()
        val inset = sw / 2 + w * 0.08f
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(w - inset * 2, w - inset * 2),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        // Arrowhead at the arc start (pointing back / counter-clockwise).
        val r = (w - inset * 2) / 2
        val cx = w / 2
        val cy = w / 2
        val a = Math.toRadians(200.0)
        val px = cx + r * kotlin.math.cos(a).toFloat()
        val py = cy + r * kotlin.math.sin(a).toFloat()
        val head = w * 0.2f
        val path = Path().apply {
            moveTo(px - head * 0.15f, py - head * 0.95f)
            lineTo(px, py)
            lineTo(px + head * 0.9f, py - head * 0.35f)
        }
        drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = LocalUndo.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.m)
            .pressable({ if (enabled) onChange(!checked) }, role = Role.Switch, enabled = enabled)
            .padding(vertical = Space.m, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) c.ink else c.muted)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.width(Space.m))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onInk, checkedTrackColor = c.ink,
                uncheckedThumbColor = c.muted, uncheckedTrackColor = c.sunk, uncheckedBorderColor = c.hairline,
            ),
        )
    }
}

@Composable
fun LinkRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalUndo.current
    Row(
        Modifier.fillMaxWidth().clip(Radius.m).pressable(onClick).heightIn(min = 56.dp).padding(vertical = Space.m, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.sunk), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.m))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.muted)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalUndo.current.hairline))
}

@Composable
fun NumberedStep(n: Int, text: String, done: Boolean = false, onToggle: (() -> Unit)? = null) {
    val c = LocalUndo.current
    val bg by animateColorAsState(if (done) c.good else c.sunk, tween(200), label = "step")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.m)
            .then(if (onToggle != null) Modifier.pressable(onToggle, role = Role.Checkbox) else Modifier)
            .padding(vertical = Space.s, horizontal = Space.xs)
            .semantics { if (onToggle != null) contentDescription = (if (done) "Done: " else "Step $n: ") + text },
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else "$n", style = MaterialTheme.typography.labelMedium, color = if (done) c.onInk else c.ink, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(Space.m))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (done) c.muted else c.inkSoft, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}
