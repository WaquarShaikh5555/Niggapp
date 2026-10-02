package app.undo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.undo.Graph
import app.undo.capture.NotificationCache
import app.undo.data.Detector
import app.undo.engine.EventStatus
import app.undo.engine.PlanContext
import app.undo.engine.Playbooks
import app.undo.engine.Priority
import app.undo.engine.UndoEvent
import app.undo.ui.AppViewModel
import app.undo.ui.Caps
import app.undo.ui.Fmt
import app.undo.ui.Route
import app.undo.ui.components.AppGlyph
import app.undo.ui.components.ButtonKind
import app.undo.ui.components.Eyebrow
import app.undo.ui.components.RewindMark
import app.undo.ui.components.SectionTitle
import app.undo.ui.components.SkeletonEventCard
import app.undo.ui.components.StatusPill
import app.undo.ui.components.SurfaceCard
import app.undo.ui.components.UndoButton
import app.undo.ui.components.pressable
import app.undo.ui.planContext
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val events by Graph.store.events.collectAsStateWithLifecycle()
    val prefs by Graph.prefs.state.collectAsStateWithLifecycle()
    val tick by vm.capsTick
    val caps = remember(tick) { Caps.read(ctx) }
    val pctx = remember(tick) { planContext(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val ranked = remember(events, now) { events?.let { Priority.rank(it, now) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.xl, end = Space.xl, bottom = Space.xxxl),
    ) {
        item { Header(onSettings = { vm.go(Route.Settings) }) }
        item { PrivacyStrip() }
        if (prefs.paused) item {
            Column {
                Spacer(Modifier.height(Space.m))
                SurfaceCard(color = c.warnBg, onClick = { Graph.prefs.setPaused(false) }) {
                    Column {
                        Text("UNDO is paused", style = MaterialTheme.typography.titleMedium, color = c.warn)
                        Text("Nothing new is being noticed. Tap to resume.", style = MaterialTheme.typography.bodySmall, color = c.warn)
                    }
                }
            }
        }
        item {
            Column {
                Spacer(Modifier.height(Space.l))
                PanicCard { vm.go(Route.Panic) }
            }
        }
        if (!caps.listener) item {
          Column {
            Spacer(Modifier.height(Space.m))
            SurfaceCard(onClick = { vm.go(Route.Capabilities) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.guideBg), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Radar, null, tint = c.guide)
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text("UNDO is only half awake", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text("Turn on notification access to catch payments, renewals and notifications you swipe away.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Icon(Icons.Rounded.ArrowForward, null, tint = c.muted)
                }
            }
          }
        }

        if (ranked == null) {
            item { SectionTitle("Looking back…") }
            items(3) {
                Column {
                    SkeletonEventCard()
                    Spacer(Modifier.height(Space.m))
                }
            }
        } else {
            val (attention, earlier) = ranked
            if (attention.isNotEmpty()) {
                item { SectionTitle("Needs attention · ${attention.size}") }
                items(attention, key = { "a${it.id}" }) { e ->
                    Column {
                        EventCard(e, pctx, now, emphasized = true) { vm.go(Route.Event(e.id)) }
                        Spacer(Modifier.height(Space.m))
                    }
                }
            } else {
                item { CalmState(caps, prefs.enabled) }
            }
            val visible = earlier.filter { it.status != EventStatus.OPEN || Priority.score(it, now) >= Priority.VISIBLE_THRESHOLD }.take(5)
            if (earlier.isNotEmpty()) {
                item {
                    SectionTitle("Earlier") {
                        Text(
                            "All history",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.rewindText,
                            modifier = Modifier.clip(Radius.s).pressable({ vm.go(Route.History) }).padding(horizontal = Space.s, vertical = Space.xs),
                        )
                    }
                }
                items(visible, key = { "e${it.id}" }) { e ->
                    Column {
                        EventCard(e, pctx, now, emphasized = false) { vm.go(Route.Event(e.id)) }
                        Spacer(Modifier.height(Space.s))
                    }
                }
                if (visible.isEmpty()) item {
                    Text("${earlier.size} older item${if (earlier.size == 1) "" else "s"} faded out of view — they're in All history until your retention window ends.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }

        item {
          Column {
            SectionTitle("Tools")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ToolTile("Android trash", Icons.Rounded.AutoDelete, Modifier.weight(1f)) { vm.go(Route.Trash) }
                ToolTile("Detectors", Icons.Rounded.Radar, Modifier.weight(1f), badge = "${caps.detectorsOn}/${caps.detectorsTotal}") { vm.go(Route.Capabilities) }
                ToolTile("History", Icons.Rounded.History, Modifier.weight(1f)) { vm.go(Route.History) }
            }
          }
        }
        item {
          Column {
            Spacer(Modifier.height(Space.xxxl))
            Text(
                "UNDO either fixes what you just did, or tells you exactly what you can do next.",
                style = MaterialTheme.typography.titleLarge,
                color = c.muted,
            )
            Spacer(Modifier.height(Space.m))
            Text("Keeping ${retentionWords(prefs.retentionHours)} of history · on this phone only", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.navigationBarsPadding())
          }
        }
    }
}

fun retentionWords(h: Int): String = when {
    h < 24 -> "$h hour" + if (h == 1) "" else "s"
    h % 24 == 0 && h / 24 == 1 -> "24 hours"
    else -> "${h / 24} days"
}

@Composable
private fun Header(onSettings: () -> Unit) {
    val c = LocalUndo.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.l), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("UNDO", style = MaterialTheme.typography.displayMedium, color = c.ink, modifier = Modifier.semantics { heading() })
            Text("Ctrl+Z for your phone", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(c.surface).border(1.dp, c.hairline, CircleShape)
                .pressable(onSettings).semantics { contentDescription = "Settings" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Settings, null, tint = c.ink) }
    }
}

@Composable
private fun PrivacyStrip() {
    val c = LocalUndo.current
    Row(
        Modifier.padding(top = Space.m).clip(Radius.pill).background(c.sunk).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = c.good, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("On this phone only · no internet permission · no account", style = MaterialTheme.typography.labelMedium, color = c.inkSoft)
    }
}

@Composable
private fun PanicCard(onClick: () -> Unit) {
    val c = LocalUndo.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Radius.l)
            .background(c.ink)
            .pressable(onClick)
            .padding(Space.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Just did something?", color = c.rewind)
                Spacer(Modifier.height(Space.s))
                Text("Something went wrong?", style = MaterialTheme.typography.headlineLarge, color = c.onInk)
                Spacer(Modifier.height(Space.s))
                Text("Tell UNDO what happened. It fixes it, or tells you exactly what to do next.", style = MaterialTheme.typography.bodyMedium, color = c.onInk.copy(alpha = 0.78f))
            }
            Spacer(Modifier.width(Space.m))
            RewindMark(Modifier.size(64.dp), color = c.rewind, stroke = 7.dp)
        }
    }
}

@Composable
fun EventCard(e: UndoEvent, pctx: PlanContext, now: Long, emphasized: Boolean, onClick: () -> Unit) {
    val c = LocalUndo.current
    val plan = remember(e, pctx) {
        Playbooks.forEvent(e, pctx.copy(hasOriginalIntent = NotificationCache.intentFor(e.extras["nkey"]) != null))
    }
    SurfaceCard(onClick = onClick, padding = if (emphasized) Space.l else Space.m, modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.Top) {
            AppGlyph(e.packageName, Fmt.typeIcon(e), size = if (emphasized) 46.dp else 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(Fmt.typeLabel(e.type), e.appLabel?.takeIf { e.type.name.startsWith("NOTIF") || e.type.name.startsWith("SUB") || e.type.name.startsWith("PAY") }).joinToString(" · ") +
                        " · " + (if (e.approxTime) "noticed " else "") + Fmt.ago(e.occurredAt, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    e.title,
                    style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!e.body.isNullOrBlank() && emphasized) {
                    Spacer(Modifier.height(2.dp))
                    Text(e.body, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Space.s))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    when (e.status) {
                        EventStatus.UNDONE -> ResolvedPill("Undone ✓", c.good, c.goodBg)
                        EventStatus.NOT_A_MISTAKE -> ResolvedPill("Not a mistake", c.muted, c.sunk)
                        EventStatus.OPEN -> {
                            StatusPill(plan.undoability)
                            e.expiresAt?.takeIf { it > now }?.let {
                                Text(Fmt.until(it, now), style = MaterialTheme.typography.labelMedium, color = c.rewindText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResolvedPill(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(Radius.pill).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun CalmState(caps: Caps, enabled: Set<Detector>) {
    val c = LocalUndo.current
    Column {
    Spacer(Modifier.height(Space.xxl))
    SurfaceCard(color = c.sunk) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RewindMark(Modifier.size(36.dp), color = c.good, stroke = 4.dp)
                Spacer(Modifier.width(Space.m))
                Column {
                    Text("Nothing needs undoing", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Text("You're all good. UNDO is keeping watch for:", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Spacer(Modifier.height(Space.m))
            val watch = listOf(
                Triple("Payments & renewals", caps.listener, Detector.PAYMENTS),
                Triple("Swiped-away notifications", caps.listener, Detector.NOTIFICATIONS),
                Triple("Deleted photos & videos", caps.media == app.undo.capture.MediaWatcher.Access.FULL, Detector.FILES),
                Triple("Uninstalled apps", true, Detector.APPS),
                Triple("Changed settings", true, Detector.SETTINGS),
            )
            watch.forEach { (label, available, det) ->
                val on = available && det in enabled
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.good else c.hairline))
                    Spacer(Modifier.width(Space.s))
                    Text(
                        label + if (!available) " — needs access" else if (det !in enabled) " — off" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (on) c.inkSoft else c.muted,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ToolTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    val c = LocalUndo.current
    Column(
        modifier.clip(Radius.l).background(c.surface).border(1.dp, c.hairline, Radius.l).pressable(onClick).padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        Spacer(Modifier.height(Space.l))
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

