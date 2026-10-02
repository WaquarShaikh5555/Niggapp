@file:OptIn(ExperimentalLayoutApi::class)

package app.undo.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.undo.Graph
import app.undo.capture.Alerts
import app.undo.capture.Launch
import app.undo.capture.MediaWatcher
import app.undo.capture.NotificationCache
import app.undo.capture.Watchers
import app.undo.data.Detector
import app.undo.engine.EventStatus
import app.undo.engine.EventType
import app.undo.engine.UndoEvent
import app.undo.ui.AppViewModel
import app.undo.ui.Caps
import app.undo.ui.Fmt
import app.undo.ui.LocalSnack
import app.undo.ui.Route
import app.undo.ui.components.ButtonKind
import app.undo.ui.components.Chip
import app.undo.ui.components.Eyebrow
import app.undo.ui.components.Hairline
import app.undo.ui.components.LinkRow
import app.undo.ui.components.RewindMark
import app.undo.ui.components.SectionTitle
import app.undo.ui.components.SurfaceCard
import app.undo.ui.components.ToggleRow
import app.undo.ui.components.TopBar
import app.undo.ui.components.UndoButton
import app.undo.ui.components.reducedMotion
import app.undo.ui.components.pressable
import app.undo.ui.planContext
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space

// ------------------------------------------------------------------ Capabilities

@Composable
fun CapabilitiesScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    val tick by vm.capsTick
    val caps = remember(tick) { Caps.read(ctx) }
    val media = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Watchers.refresh(ctx); vm.bump()
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Graph.prefs.setAlerts(true); Alerts.ensureChannel(ctx) } else snack("Alerts stay off — you can change this in Android settings")
        vm.bump()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("What UNDO can see", onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Text(
                "Each switch unlocks one kind of undo. Everything is optional, everything stays on this phone, and you can turn any of it off in Android settings at any time.",
                style = MaterialTheme.typography.bodyMedium, color = c.inkSoft,
            )
            Spacer(Modifier.height(Space.l))

            if (!app.undo.BuildConfig.HAS_LISTENER) {
                SurfaceCard(color = c.sunk, modifier = Modifier.padding(bottom = Space.m)) {
                    Column {
                        Text("Notification access · not in UNDO Lite", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Lite leaves out notification access so it installs without being blocked by Play Protect. You lose automatic payment, renewal and swiped-notification detection — but every guide in “Something went wrong?” still works, including payment recovery.",
                            style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
                        )
                    }
                }
            } else CapCard(
                title = "Notification access",
                on = caps.listener,
                unlocks = "Payments, renewals and subscription warnings · Reopen notifications you swiped away · Detect Do Not Disturb changes",
                never = "Never reads one-time codes or promos, never replies, never dismisses anything.",
                action = "Turn on",
            ) { if (!Launch.notificationAccess(ctx)) snack("Couldn't open notification access settings") }
            if (app.undo.BuildConfig.HAS_LISTENER && !caps.listener && Build.VERSION.SDK_INT >= 33) {
                SurfaceCard(color = c.guideBg, modifier = Modifier.padding(bottom = Space.m)) {
                    Column {
                        Text("Greyed out? That's Android protecting you", style = MaterialTheme.typography.titleSmall, color = c.guide)
                        Spacer(Modifier.height(Space.xs))
                        Text("Apps installed outside the Play Store need one extra step: open UNDO's App info, tap ⋮ (top right) → Allow restricted settings, then come back and turn on notification access.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        Spacer(Modifier.height(Space.s))
                        UndoButton("Open App info", { Launch.appDetails(ctx) }, kind = ButtonKind.Ghost)
                    }
                }
            }
            CapCard(
                title = "Photos & videos",
                on = caps.media == MediaWatcher.Access.FULL,
                partial = caps.media == MediaWatcher.Access.PARTIAL,
                unlocks = "Notice deleted photos and videos · Restore them from Android's trash in one tap",
                never = "Never uploads, copies or analyses your pictures — only names, folders and sizes.",
                action = if (caps.media == MediaWatcher.Access.PARTIAL) "Allow all" else "Allow",
            ) { media.launch(MediaWatcher.permissionsToRequest()) }
            CapCard(
                title = "Modify system settings",
                on = caps.writeSettings,
                unlocks = "Switch auto-rotate, brightness and screen timeout back for you",
                never = "Only ever writes a value back after you tap Restore.",
                action = "Allow",
            ) { Launch.writeSettings(ctx) }
            CapCard(
                title = "Usage access",
                on = caps.usage,
                unlocks = "“You were just in…” — suggests the right fix based on the app you just left",
                never = "Only reads which apps were opened recently. Never what you did inside them.",
                action = "Allow",
            ) { if (!Launch.usageAccess(ctx)) snack("Usage access isn't available on this phone") }
            CapCard(
                title = "Alerts",
                on = caps.alerts,
                unlocks = "A quiet heads-up when something important happens — like a payment or a deleted photo",
                never = "Hidden on the lock screen. Off by default.",
                action = "Turn on",
            ) {
                if (Build.VERSION.SDK_INT >= 33 && !Alerts.permissionGranted(ctx)) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { Graph.prefs.setAlerts(true); Alerts.ensureChannel(ctx); vm.bump() }
            }

            SectionTitle("Always available")
            Text("Uninstalled apps and changed settings need no permission at all — UNDO notices them whenever it runs.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            SectionTitle("If UNDO stops noticing things")
            Text("Some phones aggressively close background apps. If events go missing, set UNDO's battery usage to Unrestricted in App info → Battery.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            Spacer(Modifier.height(Space.s))
            LinkRow("Open App info") { Launch.appDetails(ctx) }
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }
}

@Composable
private fun CapCard(title: String, on: Boolean, unlocks: String, never: String, action: String, partial: Boolean = false, onEnable: () -> Unit) {
    val c = LocalUndo.current
    SurfaceCard(modifier = Modifier.padding(bottom = Space.m)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (on) c.good else if (partial) c.warn else c.muted,
                )
                Spacer(Modifier.width(Space.s))
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                Text(
                    if (on) "On" else if (partial) "Partly" else "Off",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) c.good else if (partial) c.warn else c.muted,
                )
            }
            Spacer(Modifier.height(Space.s))
            Text(unlocks, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            Spacer(Modifier.height(Space.xs))
            Text(never, style = MaterialTheme.typography.bodySmall, color = c.muted)
            if (!on) {
                Spacer(Modifier.height(Space.m))
                UndoButton(action, onEnable, kind = ButtonKind.Primary)
            }
        }
    }
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    val prefs by Graph.prefs.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            SectionTitle("Keep history for")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(1 to "1 hour", 24 to "24 hours", 72 to "3 days", 168 to "7 days").forEach { (h, label) ->
                    Chip(label, selected = prefs.retentionHours == h, onClick = { Graph.prefs.setRetention(h); Graph.purge() })
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text("Older items are deleted automatically. UNDO is for recent mistakes, not a diary.", style = MaterialTheme.typography.bodySmall, color = c.muted)

            SectionTitle("Noticing")
            ToggleRow("Pause UNDO", "Stop noticing anything new until you resume", prefs.paused, { Graph.prefs.setPaused(it) })
            Hairline()
            Detector.values().forEach { d ->
                val needsListener = d == Detector.PAYMENTS || d == Detector.SUBSCRIPTIONS || d == Detector.NOTIFICATIONS
                val lite = needsListener && !app.undo.BuildConfig.HAS_LISTENER
                ToggleRow(
                    d.title, if (lite) "Full edition only — Lite has no notification access" else d.description,
                    d in prefs.enabled && !lite, { Graph.prefs.setDetector(d, it) }, enabled = !prefs.paused && !lite,
                )
            }
            Spacer(Modifier.height(Space.s))
            LinkRow("Permissions & what UNDO can see") { vm.go(Route.Capabilities) }

            SectionTitle("Alerts")
            ToggleRow("Heads-up for important events", "Payments, deleted media, subscription renewals. Hidden on the lock screen.", prefs.alerts && Alerts.permissionGranted(ctx), { on ->
                if (on && !Alerts.permissionGranted(ctx)) vm.go(Route.Capabilities) else { Graph.prefs.setAlerts(on); if (on) Alerts.ensureChannel(ctx) }
            })

            SectionTitle("Privacy")
            ToggleRow("Hide in Recents", "Blank UNDO's preview in the app switcher", prefs.hideInRecents, { Graph.prefs.setHideInRecents(it) }, enabled = Build.VERSION.SDK_INT >= 33)
            ToggleRow("Block screenshots", "Stops screenshots and screen recording of UNDO", prefs.blockScreenshots, { Graph.prefs.setBlockScreenshots(it) })

            SectionTitle("Message tone")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Chip("Friendly", selected = prefs.friendlyTone, onClick = { Graph.prefs.setFriendlyTone(true) })
                Chip("Formal", selected = !prefs.friendlyTone, onClick = { Graph.prefs.setFriendlyTone(false) })
            }

            SectionTitle("Your data")
            Text("Everything lives in UNDO's private storage on this phone. It's excluded from backups and device transfers, and UNDO has no internet permission.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(Space.m))
            UndoButton("Delete everything", { confirmDelete = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Icons.Rounded.DeleteForever)

            SectionTitle("About")
            Text("UNDO ${app.undo.BuildConfig.VERSION_NAME} · Ctrl+Z for your phone", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text("UNDO either fixes what you just did, or tells you exactly what you can do next. It never claims to reverse something it can't, and never sends anything for you.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(Space.s))
            LinkRow("Open-source licences", "Space Grotesk (SIL OFL 1.1), AndroidX & Jetpack Compose (Apache 2.0)") {
                license = runCatching { ctx.assets.open("licenses/SpaceGrotesk-OFL.txt").bufferedReader().use { it.readText() } }.getOrNull()
                    ?.plus("\n\nAndroidX and Jetpack Compose are licensed under the Apache License 2.0 — apache.org/licenses/LICENSE-2.0")
            }
            LinkRow("Show the welcome tour again") { vm.go(Route.Onboarding) }
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            shape = Radius.l,
            title = { Text("Delete everything?", color = c.ink, style = MaterialTheme.typography.headlineSmall) },
            text = { Text("All events, notification copies and snapshots UNDO kept on this phone will be erased. This can't be undone — not even by UNDO.", color = c.inkSoft, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                UndoButton("Delete", {
                    Graph.store.deleteEverything()
                    NotificationCache.clear()
                    Graph.prefs.resetBaselines()
                    confirmDelete = false
                    snack("Everything is gone")
                }, kind = ButtonKind.Accent)
            },
            dismissButton = { UndoButton("Keep", { confirmDelete = false }, kind = ButtonKind.Ghost) },
        )
    }
    license?.let { text ->
        AlertDialog(
            onDismissRequest = { license = null },
            containerColor = c.surface,
            shape = Radius.l,
            title = { Text("Licences", color = c.ink) },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
            },
            confirmButton = { UndoButton("Close", { license = null }, kind = ButtonKind.Ghost) },
        )
    }
}

// ------------------------------------------------------------------ History

private enum class HistoryFilter(val label: String, val match: (UndoEvent) -> Boolean) {
    ALL("All", { true }),
    MONEY("Money", { it.type == EventType.PAYMENT_SENT || it.type == EventType.SUBSCRIPTION_NOTICE }),
    NOTIFS("Notifications", { it.type == EventType.NOTIFICATION_DISMISSED || it.type == EventType.NOTIFICATIONS_CLEARED }),
    FILES("Files", { it.type == EventType.FILE_TRASHED || it.type == EventType.FILE_DELETED }),
    APPS("Apps", { it.type == EventType.APP_UNINSTALLED }),
    SETTINGS("Settings", { it.type == EventType.SETTING_CHANGED }),
    RESOLVED("Resolved", { it.status != EventStatus.OPEN }),
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val events by Graph.store.events.collectAsStateWithLifecycle()
    val prefs by Graph.prefs.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val pctx = remember { planContext(ctx) }
    val now = remember(events) { System.currentTimeMillis() }
    val grouped = remember(events, filter) {
        events.orEmpty().filter(filter.match).sortedByDescending { it.occurredAt }.groupBy { Fmt.dayHeader(it.occurredAt) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("History", onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Text("The last ${retentionWords(prefs.retentionHours)}. Older items are deleted automatically.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(Space.m))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                HistoryFilter.values().forEach { f -> Chip(f.label, selected = f == filter, onClick = { filter = f }) }
            }
            if (events != null && grouped.isEmpty()) {
                Spacer(Modifier.height(Space.xxl))
                Text("Nothing here.", style = MaterialTheme.typography.titleMedium, color = c.muted)
            }
            grouped.forEach { (day, list) ->
                SectionTitle(day)
                list.forEach { e ->
                    EventCard(e, pctx, now, emphasized = false) { vm.go(Route.Event(e.id)) }
                    Spacer(Modifier.height(Space.s))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }
}

// ------------------------------------------------------------------ Onboarding

private data class Slide(val eyebrow: String, val title: String, val body: String, val points: List<String>)

private val slides = listOf(
    Slide(
        "Ctrl+Z for your phone", "Undo, or know exactly what to do next",
        "Deleted a photo? Paid the wrong person? Swiped away something important? UNDO either fixes it, or shows you the fastest honest way to recover.",
        listOf("Restores deleted photos & videos from Android's trash", "Brings back notifications you swiped away", "Switches changed settings back"),
    ),
    Slide(
        "Honest by design", "It never pretends",
        "Some things can't be reversed by any app — a sent payment, a message on someone else's phone. UNDO tells you so, then gives you the exact steps, contacts and ready-to-send messages.",
        listOf("Never sends anything for you", "Never acts without your tap", "No guesses dressed up as AI"),
    ),
    Slide(
        "Private by design", "Your phone. Your data.",
        "No account, no cloud, no internet permission. UNDO only keeps a short recent history — you choose how long, and can wipe it any time.",
        listOf("Stored on this phone only", "Excluded from backups", "Asks for each permission only when it's needed"),
    ),
)

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val c = LocalUndo.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val reduce = reducedMotion()
    val finish = {
        Graph.prefs.setOnboarded()
        if (vm.stack.size > 1) vm.back() else vm.replace(Route.Home)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("UNDO", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
            if (step < slides.lastIndex) {
                Text(
                    "Skip", style = MaterialTheme.typography.labelLarge, color = c.muted,
                    modifier = Modifier.clip(Radius.s).pressable({ finish() }).padding(Space.s),
                )
            }
        }
        Column(Modifier.weight(1f).widthIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(Space.xxl))
            RewindMark(Modifier.size(88.dp))
            Spacer(Modifier.height(Space.xxl))
            AnimatedContent(
                targetState = step,
                transitionSpec = { if (reduce) fadeIn() togetherWith fadeOut() else fadeIn() togetherWith fadeOut() },
                label = "slide",
            ) { i ->
                val s = slides[i]
                Column {
                    Eyebrow(s.eyebrow, color = c.rewindText)
                    Spacer(Modifier.height(Space.s))
                    Text(s.title, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Space.m))
                    Text(s.body, style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                    Spacer(Modifier.height(Space.l))
                    s.points.forEach {
                        Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.rewind))
                            Spacer(Modifier.width(Space.m))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            slides.indices.forEach { i ->
                Box(Modifier.height(6.dp).width(if (i == step) 24.dp else 6.dp).clip(Radius.pill).background(if (i == step) c.ink else c.hairline))
            }
        }
        Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            if (step > 0) UndoButton("Back", { step-- }, Modifier.weight(1f), kind = ButtonKind.Ghost)
            if (step < slides.lastIndex) UndoButton("Next", { step++ }, Modifier.weight(1f), kind = ButtonKind.Primary)
            else UndoButton("Start using UNDO", { finish() }, Modifier.weight(1f), kind = ButtonKind.Accent)
        }
    }
}
