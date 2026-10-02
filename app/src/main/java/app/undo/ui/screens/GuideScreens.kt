@file:OptIn(ExperimentalLayoutApi::class)

package app.undo.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.undo.Graph
import app.undo.capture.AppInfo
import app.undo.capture.Launch
import app.undo.capture.NotificationCache
import app.undo.capture.UsageContext
import app.undo.engine.ActionKind
import app.undo.engine.ActionSpec
import app.undo.engine.AppCatalog
import app.undo.engine.EventStatus
import app.undo.engine.EventType
import app.undo.engine.Playbooks
import app.undo.engine.Topic
import app.undo.ui.AppViewModel
import app.undo.ui.Fmt
import app.undo.ui.LocalSnack
import app.undo.ui.Route
import app.undo.ui.planContext
import app.undo.ui.rememberActionHost
import app.undo.ui.components.AppGlyph
import app.undo.ui.components.ButtonKind
import app.undo.ui.components.Eyebrow
import app.undo.ui.components.Hairline
import app.undo.ui.components.NumberedStep
import app.undo.ui.components.SectionTitle
import app.undo.ui.components.StatusPill
import app.undo.ui.components.SurfaceCard
import app.undo.ui.components.TopBar
import app.undo.ui.components.UndoButton
import app.undo.ui.components.pressable
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space

fun topicIcon(t: Topic): ImageVector = when (t) {
    Topic.WRONG_MESSAGE -> Icons.Rounded.Chat
    Topic.DELETED_FILE -> Icons.Rounded.DeleteSweep
    Topic.WRONG_PAYMENT -> Icons.Rounded.CreditCard
    Topic.FRAUD -> Icons.Rounded.GppMaybe
    Topic.SUBSCRIPTION -> Icons.Rounded.Autorenew
    Topic.DISMISSED -> Icons.Rounded.NotificationsOff
    Topic.SETTING -> Icons.Rounded.Tune
    Topic.UNINSTALLED -> Icons.Rounded.AppBlocking
    Topic.LOST_WORK -> Icons.Rounded.EditNote
}

fun topicFor(type: EventType): Topic = when (type) {
    EventType.PAYMENT_SENT -> Topic.WRONG_PAYMENT
    EventType.SUBSCRIPTION_NOTICE -> Topic.SUBSCRIPTION
    EventType.NOTIFICATION_DISMISSED, EventType.NOTIFICATIONS_CLEARED -> Topic.DISMISSED
    EventType.FILE_TRASHED, EventType.FILE_DELETED -> Topic.DELETED_FILE
    EventType.APP_UNINSTALLED -> Topic.UNINSTALLED
    EventType.SETTING_CHANGED -> Topic.SETTING
}

// ------------------------------------------------------------------ Panic: "Something went wrong?"

@Composable
fun PanicScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val events by Graph.store.events.collectAsStateWithLifecycle()
    val recentApps = remember { UsageContext.recentApps(ctx, 15 * 60_000L).take(3) }
    val recent = remember(events) {
        val now = System.currentTimeMillis()
        events.orEmpty().filter { it.status == EventStatus.OPEN && now - it.occurredAt < 30 * 60_000L }.take(3)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Eyebrow("Breathe. Most things can be fixed.", color = c.rewindText)
            Spacer(Modifier.height(Space.s))
            Text("What just happened?", style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })

            if (recent.isNotEmpty()) {
                SectionTitle("UNDO noticed, in the last 30 minutes")
                recent.forEach { e ->
                    SurfaceCard(onClick = { vm.go(Route.Event(e.id)) }, padding = Space.m, modifier = Modifier.padding(bottom = Space.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppGlyph(e.packageName, Fmt.typeIcon(e), size = 36.dp)
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Fmt.ago(e.occurredAt), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            Icon(Icons.Rounded.ArrowForward, null, tint = c.muted)
                        }
                    }
                }
            }

            if (recentApps.isNotEmpty()) {
                SectionTitle("You were just in")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    recentApps.forEach { (pkg, t) ->
                        val label = AppInfo.label(ctx, pkg) ?: pkg
                        Row(
                            Modifier.clip(Radius.pill).background(c.surface).pressable({
                                if (AppCatalog.messagingApps.containsKey(pkg)) vm.go(Route.Messaging(pkg))
                                else if (AppCatalog.paymentApps.containsKey(pkg)) vm.go(Route.TopicR(Topic.WRONG_PAYMENT))
                                else if (AppCatalog.galleries.containsKey(pkg)) vm.go(Route.TopicR(Topic.DELETED_FILE))
                                else Launch.app(ctx, pkg)
                            }).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppGlyph(pkg, Icons.Rounded.OpenInNew, size = 28.dp)
                            Spacer(Modifier.width(Space.s))
                            Text("$label · ${Fmt.ago(t)}", style = MaterialTheme.typography.labelLarge, color = c.ink)
                        }
                    }
                }
            }

            SectionTitle("Pick the closest")
            Topic.values().forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(Radius.m).pressable({ vm.go(Route.TopicR(t)) }).padding(vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(Radius.m).background(if (t == Topic.FRAUD) c.badBg else c.sunk), contentAlignment = Alignment.Center) {
                        Icon(topicIcon(t), null, tint = if (t == Topic.FRAUD) c.bad else c.ink)
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Icon(Icons.Rounded.ArrowForward, null, tint = c.muted)
                }
                Hairline()
            }
            Spacer(Modifier.height(Space.l))
            Text(
                "Something else? UNDO can't see inside other apps, so it can't reverse what happened there — but most apps have their own undo, trash or support page. Open the app and look for Trash, Archive, History or Help.",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }
}

// ------------------------------------------------------------------ Topic

@Composable
fun TopicScreen(vm: AppViewModel, topic: Topic) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val events by Graph.store.events.collectAsStateWithLifecycle()
    val tick by vm.capsTick
    val host = rememberActionHost(vm)
    val plan = remember(topic, tick) { Playbooks.forTopic(topic, planContext(ctx)) }
    val related = remember(events) { events.orEmpty().filter { topicFor(it.type) == topic && it.status == EventStatus.OPEN }.take(4) }

    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(Radius.m).background(c.sunk), contentAlignment = Alignment.Center) {
                    Icon(topicIcon(topic), null, tint = c.ink)
                }
                Spacer(Modifier.width(Space.m))
                StatusPill(plan.undoability, long = true)
            }
            Spacer(Modifier.height(Space.l))
            Text(topic.title, style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.semantics { heading() })

            if (related.isNotEmpty()) {
                SectionTitle("Is it one of these?")
                related.forEach { e ->
                    EventCard(e, planContext(ctx), System.currentTimeMillis(), emphasized = false) { vm.go(Route.Event(e.id)) }
                    Spacer(Modifier.height(Space.s))
                }
            }

            SectionTitle("Can it be undone?")
            Box(Modifier.fillMaxWidth().clip(Radius.l).background(c.statusBg(plan.undoability)).padding(Space.l)) {
                Column {
                    Text(plan.verdictTitle, style = MaterialTheme.typography.headlineSmall, color = c.statusFg(plan.undoability))
                    Spacer(Modifier.height(Space.xs))
                    Text(plan.verdictBody, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                }
            }

            if (topic == Topic.WRONG_MESSAGE) {
                val installed = remember { AppCatalog.messagingApps.filterKeys { AppInfo.isInstalled(ctx, it) } }
                SectionTitle("Which app?")
                if (installed.isEmpty()) Text("No supported chat apps found. Open the app you used and long-press the message — look for Delete for everyone or Unsend.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                installed.forEach { (pkg, g) ->
                    Row(
                        Modifier.fillMaxWidth().clip(Radius.m).pressable({ vm.go(Route.Messaging(pkg)) }).padding(vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppGlyph(pkg, Icons.Rounded.Chat, size = 40.dp)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                            Text(if (g.canUnsend) "${g.unsendLabel} · ${g.window}" else "Can't be unsent", style = MaterialTheme.typography.bodySmall, color = if (g.canUnsend) c.good else c.muted)
                        }
                        Icon(Icons.Rounded.ArrowForward, null, tint = c.muted)
                    }
                    Hairline()
                }
            }

            val actions = listOfNotNull(plan.primary) + plan.secondary
            if (actions.isNotEmpty()) {
                SectionTitle("Best next step")
                actions.forEachIndexed { i, a ->
                    UndoButton(a.label, { host.run(a, null, topic) }, Modifier.fillMaxWidth().padding(bottom = Space.s), kind = if (i == 0) ButtonKind.Primary else ButtonKind.Secondary, icon = iconFor(a))
                }
            }
            if (plan.urgent.isNotEmpty()) {
                plan.urgent.forEach { a -> UndoButton(a.label, { host.run(a, null, topic) }, Modifier.fillMaxWidth().padding(bottom = Space.s), kind = ButtonKind.Secondary, icon = iconFor(a)) }
            }
            if (plan.steps.isNotEmpty()) {
                SectionTitle("Step by step")
                plan.steps.forEachIndexed { i, s -> NumberedStep(i + 1, s) }
            }
            if (plan.hasFixFlow && actions.none { it.kind == ActionKind.HELP_ME_FIX }) {
                Spacer(Modifier.height(Space.m))
                UndoButton("Help me fix it", { host.run(ActionSpec(ActionKind.HELP_ME_FIX, ""), null, topic) }, Modifier.fillMaxWidth(), kind = ButtonKind.Accent, icon = Icons.Rounded.AutoFixHigh)
            }
            plan.notes.forEach {
                Spacer(Modifier.height(Space.m))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }
}

// ------------------------------------------------------------------ Messaging guide

@Composable
fun MessagingScreen(vm: AppViewModel, pkg: String) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    val guide = AppCatalog.messagingApps[pkg]
    val name = guide?.name ?: AppInfo.label(ctx, pkg) ?: "this app"
    val chats = remember(pkg) { NotificationCache.recentConversations(pkg) }

    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(pkg, Icons.Rounded.Chat, size = 52.dp)
                Spacer(Modifier.width(Space.m))
                StatusPill(if (guide?.canUnsend == true) app.undo.engine.Undoability.GUIDED else app.undo.engine.Undoability.NOT_UNDOABLE, long = true)
            }
            Spacer(Modifier.height(Space.l))
            Text(
                if (guide?.canUnsend == true) "Unsend it in $name" else "$name can't unsend",
                style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.s))
            Text(
                "UNDO can't delete messages for you — only $name can, and only you can tap the button. Here's exactly where it is.",
                style = MaterialTheme.typography.bodyMedium, color = c.inkSoft,
            )

            if (guide != null) {
                if (guide.canUnsend) {
                    Spacer(Modifier.height(Space.l))
                    Box(Modifier.fillMaxWidth().clip(Radius.l).background(c.warnBg).padding(Space.l)) {
                        Column {
                            Text("Time limit: ${guide.window}", style = MaterialTheme.typography.titleMedium, color = c.warn)
                            Text("Act quickly — after the window the option disappears.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        }
                    }
                }
                SectionTitle(if (guide.canUnsend) "How to ${guide.unsendLabel.lowercase()}" else "What you can do")
                guide.steps.forEachIndexed { i, s -> NumberedStep(i + 1, s) }
                guide.edit?.let {
                    SectionTitle("Rather fix than delete?")
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                }
                Spacer(Modifier.height(Space.m))
                Text(guide.caveat, style = MaterialTheme.typography.bodySmall, color = c.muted)
            } else {
                SectionTitle("Where to look")
                NumberedStep(1, "Open the chat and long-press the message.")
                NumberedStep(2, "Look for Delete for everyone, Unsend, Remove or Retract.")
                NumberedStep(3, "If there's no such option, the message can't be pulled back — send a short follow-up instead.")
            }

            if (chats.isNotEmpty()) {
                SectionTitle("Recent chats")
                Text("From notifications UNDO saw in the last day. Tap to jump straight in.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(Space.s))
                chats.forEach { ch ->
                    Row(
                        Modifier.fillMaxWidth().clip(Radius.m).pressable({
                            if (!Launch.pendingIntent(ctx, ch.contentIntent)) {
                                Launch.app(ctx, pkg)
                                snack("That chat link expired — opened $name")
                            }
                        }).padding(vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.conversation ?: ch.title ?: name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.ago(ch.postTime), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        Icon(Icons.Rounded.OpenInNew, "Open chat", tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                    Hairline()
                }
            }

            Spacer(Modifier.height(Space.xl))
            UndoButton("Open $name", { if (!Launch.app(ctx, pkg)) snack("$name isn't installed") }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = Icons.Rounded.OpenInNew)
            Spacer(Modifier.height(Space.s))
            UndoButton("Write a follow-up message", { vm.go(Route.Fix(null, Topic.WRONG_MESSAGE)) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Icons.Rounded.AutoFixHigh)
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }
}
