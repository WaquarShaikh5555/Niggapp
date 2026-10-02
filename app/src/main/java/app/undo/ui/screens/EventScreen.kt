package app.undo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.undo.Graph
import app.undo.capture.Launch
import app.undo.capture.NotificationCache
import app.undo.capture.SettingsWatcher
import app.undo.engine.ActionKind
import app.undo.engine.ActionSpec
import app.undo.engine.EventStatus
import app.undo.engine.EventType
import app.undo.engine.Money
import app.undo.engine.Playbooks
import app.undo.engine.UndoEvent
import app.undo.ui.AppViewModel
import app.undo.ui.Fmt
import app.undo.ui.LocalSnack
import app.undo.ui.planContext
import app.undo.ui.rememberActionHost
import app.undo.ui.components.AppGlyph
import app.undo.ui.components.ButtonKind
import app.undo.ui.components.Eyebrow
import app.undo.ui.components.Hairline
import app.undo.ui.components.SectionTitle
import app.undo.ui.components.SkeletonEventCard
import app.undo.ui.components.StatusPill
import app.undo.ui.components.SurfaceCard
import app.undo.ui.components.TopBar
import app.undo.ui.components.UndoButton
import app.undo.ui.components.pressable
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space

@Composable
fun EventScreen(vm: AppViewModel, id: Long) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    val events by Graph.store.events.collectAsStateWithLifecycle()
    val tick by vm.capsTick
    val e = events?.firstOrNull { it.id == id }
    val host = rememberActionHost(vm)

    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.back() })
        if (e == null) {
            Column(Modifier.padding(Space.xl)) {
                if (events == null) SkeletonEventCard()
                else Text("This item is no longer in your history (it may have passed your retention window).", style = MaterialTheme.typography.bodyLarge, color = c.muted)
            }
        } else {
            EventBody(vm, e, tick, host)
        }
    }
}

@Composable
private fun EventBody(vm: AppViewModel, e: UndoEvent, tick: Int, host: app.undo.ui.ActionHost) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    run {
        val hasOriginal = NotificationCache.intentFor(e.extras["nkey"]) != null
        val plan = remember(e, tick) { Playbooks.forEvent(e, planContext(ctx, hasOriginal)) }

        // Finish a setting restore once the user comes back from granting "modify system settings".
        LaunchedEffect(tick) {
            if (vm.pendingRestore == e.id && SettingsWatcher.canWrite(ctx)) {
                vm.pendingRestore = null
                host.restoreSetting(e)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl),
        ) {
            // ---- Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(e.packageName, Fmt.typeIcon(e), size = 52.dp)
                Spacer(Modifier.width(Space.m))
                Column {
                    Eyebrow(listOfNotNull(Fmt.typeLabel(e.type), e.appLabel).joinToString(" · "))
                    Spacer(Modifier.height(Space.xs))
                    when (e.status) {
                        EventStatus.UNDONE -> ResolvedPill("Undone — verified by UNDO", c.good, c.goodBg)
                        EventStatus.NOT_A_MISTAKE -> ResolvedPill("Marked as not a mistake", c.muted, c.sunk)
                        EventStatus.OPEN -> StatusPill(plan.undoability, long = true)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Text(e.title, style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.semantics { heading() })

            if (e.type == EventType.PAYMENT_SENT && e.amountMinor != null) {
                Spacer(Modifier.height(Space.m))
                Text(Money.format(e.amountMinor, e.currency), style = MaterialTheme.typography.displayLarge, color = c.ink)
                e.counterparty?.let { Text("to $it", style = MaterialTheme.typography.titleMedium, color = c.inkSoft) }
            }

            // ---- What happened?
            SectionTitle("What happened")
            WhatHappened(e)

            // ---- When?
            SectionTitle("When")
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Schedule, null, tint = c.muted, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                Spacer(Modifier.width(Space.s))
                Column {
                    Text("${Fmt.whenLong(e.occurredAt)} · ${Fmt.ago(e.occurredAt)}", style = MaterialTheme.typography.bodyLarge, color = c.ink)
                    if (e.approxTime) {
                        Text("UNDO noticed this when it next ran — the exact time isn't known.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    e.expiresAt?.let { exp ->
                        Spacer(Modifier.height(Space.xs))
                        val closed = exp <= System.currentTimeMillis()
                        Text(
                            if (closed) "The undo window closed ${Fmt.date(exp)}" else "Undo window closes ${Fmt.date(exp)} at ${Fmt.time(exp)} — ${Fmt.until(exp)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (closed) c.muted else c.rewindText,
                        )
                    }
                }
            }

            // ---- Can it be undone?
            SectionTitle("Can it be undone?")
            Box(
                Modifier.fillMaxWidth().clip(Radius.l).background(c.statusBg(plan.undoability)).padding(Space.l),
            ) {
                Column {
                    Text(plan.verdictTitle, style = MaterialTheme.typography.headlineSmall, color = c.statusFg(plan.undoability))
                    Spacer(Modifier.height(Space.xs))
                    Text(plan.verdictBody, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                }
            }

            // ---- Best next step
            if (e.status == EventStatus.OPEN) {
                SectionTitle("Best next step")
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    plan.primary?.let { a ->
                        UndoButton(a.label, { host.run(a, e) }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = iconFor(a))
                    }
                    plan.secondary.forEach { a ->
                        UndoButton(a.label, { host.run(a, e) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = iconFor(a))
                    }
                    if (plan.hasFixFlow && plan.primary?.kind != ActionKind.HELP_ME_FIX && plan.secondary.none { it.kind == ActionKind.HELP_ME_FIX }) {
                        UndoButton("Help me fix it", { host.run(ActionSpec(ActionKind.HELP_ME_FIX, ""), e) }, Modifier.fillMaxWidth(), kind = ButtonKind.Accent, icon = Icons.Rounded.AutoFixHigh)
                    }
                }
                plan.hint()?.let {
                    Spacer(Modifier.height(Space.s))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            } else {
                Spacer(Modifier.height(Space.l))
                UndoButton("Reopen this", { Graph.store.setStatus(e.id, EventStatus.OPEN) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }

            if (plan.urgent.isNotEmpty() || plan.urgentTitle != null) {
                Spacer(Modifier.height(Space.xl))
                Box(Modifier.fillMaxWidth().clip(Radius.l).background(c.badBg).padding(Space.l)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = c.bad)
                            Spacer(Modifier.width(Space.s))
                            Text(plan.urgentTitle ?: "Urgent", style = MaterialTheme.typography.titleMedium, color = c.bad)
                        }
                        plan.urgentBody?.let {
                            Spacer(Modifier.height(Space.xs))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                        }
                        Spacer(Modifier.height(Space.m))
                        plan.urgent.forEach { a ->
                            UndoButton(a.label, { host.run(a, e) }, Modifier.fillMaxWidth().padding(bottom = Space.s), kind = ButtonKind.Secondary, icon = iconFor(a))
                        }
                    }
                }
            }

            plan.notes.forEach {
                Spacer(Modifier.height(Space.m))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }

            // ---- Housekeeping
            Spacer(Modifier.height(Space.xxl))
            Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (e.status == EventStatus.OPEN) {
                    UndoButton("Not a mistake", {
                        Graph.store.setStatus(e.id, EventStatus.NOT_A_MISTAKE)
                        snack("Got it — moved out of your way")
                        vm.back()
                    }, Modifier.weight(1f), kind = ButtonKind.Ghost, icon = Icons.Rounded.Block)
                }
                UndoButton("Delete", {
                    Graph.store.delete(e.id)
                    snack("Deleted from this phone")
                    vm.back()
                }, Modifier.weight(1f), kind = ButtonKind.Ghost, icon = Icons.Rounded.DeleteOutline)
            }
            Spacer(Modifier.navigationBarsPadding().height(Space.xl))
        }
    }
}

private fun app.undo.engine.Plan.hint(): String? {
    val p = primary ?: return null
    return if (p.kind == ActionKind.OPEN_APP) p.hint?.let { "Inside the app: $it" } else null
}

@Composable
private fun WhatHappened(e: UndoEvent) {
    val c = LocalUndo.current
    val ctx = LocalContext.current
    when (e.type) {
        EventType.NOTIFICATIONS_CLEARED, EventType.FILE_TRASHED, EventType.FILE_DELETED -> {
            if (e.type == EventType.NOTIFICATIONS_CLEARED) {
                Text("Tap one to reopen it.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(Space.s))
            }
            SurfaceCard(padding = Space.xs) {
                Column {
                    e.items.take(60).forEachIndexed { i, item ->
                        if (i > 0) Hairline(Modifier.padding(horizontal = Space.m))
                        val clickable = e.type == EventType.NOTIFICATIONS_CLEARED
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Radius.m)
                                .then(
                                    if (clickable) Modifier.pressable({
                                        val pi = NotificationCache.intentFor(item.ref)
                                        if (!Launch.pendingIntent(ctx, pi)) Launch.app(ctx, item.packageName)
                                    }) else Modifier,
                                )
                                .padding(Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.packageName != null) {
                                AppGlyph(item.packageName, Fmt.typeIcon(e), size = 32.dp)
                                Spacer(Modifier.width(Space.m))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                item.subtitle?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (clickable) Icon(Icons.Rounded.OpenInNew, "Open", tint = c.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (e.items.size > 60) Text("…and ${e.items.size - 60} more", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(Space.m))
                }
            }
        }
        else -> {
            val text = when (e.type) {
                EventType.PAYMENT_SENT -> buildString {
                    append("A payment notification")
                    e.appLabel?.let { append(" from $it") }
                    append(" reported money leaving your account")
                    e.extras["instrument"]?.takeIf { it.isNotBlank() }?.let { append(" by $it") }
                    append(".")
                    e.reference?.let { append(" Reference: $it.") }
                }
                EventType.NOTIFICATION_DISMISSED -> "You swiped this away. Here's what it said:"
                EventType.APP_UNINSTALLED -> "${e.appLabel ?: e.packageName} was removed from this phone." + (e.body?.let { " $it." } ?: "")
                EventType.SETTING_CHANGED -> "${e.title}. ${e.body ?: ""}".trim()
                EventType.SUBSCRIPTION_NOTICE -> "${e.appLabel ?: "An app"} sent a subscription or renewal notice:"
                else -> e.body ?: ""
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
            val quote = when (e.type) {
                EventType.NOTIFICATION_DISMISSED, EventType.SUBSCRIPTION_NOTICE, EventType.PAYMENT_SENT -> e.body
                else -> null
            }
            if (!quote.isNullOrBlank()) {
                Spacer(Modifier.height(Space.m))
                SurfaceCard(color = c.sunk) {
                    Column {
                        if (e.type == EventType.NOTIFICATION_DISMISSED) Text(e.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(quote, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                    }
                }
            }
        }
    }
}

fun iconFor(a: ActionSpec) = when (a.kind) {
    ActionKind.RESTORE_TRASH, ActionKind.RESTORE_SETTING -> Icons.Rounded.AutoFixHigh
    ActionKind.HELP_ME_FIX -> Icons.Rounded.AutoFixHigh
    ActionKind.OPEN_APP, ActionKind.OPEN_URL, ActionKind.OPEN_ORIGINAL, ActionKind.OPEN_PLAY_SUBSCRIPTIONS,
    ActionKind.REINSTALL, ActionKind.OPEN_SETTING_SCREEN, ActionKind.OPEN_NOTIFICATION_HISTORY -> Icons.Rounded.OpenInNew
    else -> null
}
