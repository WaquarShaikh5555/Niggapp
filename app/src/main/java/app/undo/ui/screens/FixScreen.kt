@file:OptIn(ExperimentalLayoutApi::class)

package app.undo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.undo.Graph
import app.undo.capture.Launch
import app.undo.engine.ActionKind
import app.undo.engine.Playbooks
import app.undo.engine.Topic
import app.undo.ui.AppViewModel
import app.undo.ui.LocalSnack
import app.undo.ui.planContext
import app.undo.ui.rememberActionHost
import app.undo.ui.components.ButtonKind
import app.undo.ui.components.Chip
import app.undo.ui.components.LinkRow
import app.undo.ui.components.NumberedStep
import app.undo.ui.components.SectionTitle
import app.undo.ui.components.TopBar
import app.undo.ui.components.UndoButton
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space

private val blank = Regex("\\[[^\\]]{1,40}]")

@Composable
fun FixScreen(vm: AppViewModel, eventId: Long?, topic: Topic?) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    val events by Graph.store.events.collectAsStateWithLifecycle()
    val prefs by Graph.prefs.state.collectAsStateWithLifecycle()
    val event = eventId?.let { id -> events?.firstOrNull { it.id == id } }
    val host = rememberActionHost(vm)
    val plan = remember(event, topic) {
        val pctx = planContext(ctx)
        event?.let { Playbooks.forEvent(it, pctx) } ?: topic?.let { Playbooks.forTopic(it, pctx) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Help me fix it", onBack = { vm.back() })
        if (plan == null) {
            Text("Nothing to fix here.", Modifier.padding(Space.xl), color = c.muted)
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl)) {
            Text(event?.title ?: topic?.title ?: "", style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Spacer(Modifier.height(Space.s))
            Row(
                Modifier.clip(Radius.m).background(c.sunk).padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = c.good, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Text("UNDO never sends anything or acts for you. You check it, edit it, and send it yourself.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
            }

            if (plan.steps.isNotEmpty()) {
                val done = remember(plan) { mutableStateListOf(*Array(plan.steps.size) { false }) }
                SectionTitle("Steps · ${done.count { it }} of ${plan.steps.size} done")
                plan.steps.forEachIndexed { i, step ->
                    NumberedStep(i + 1, step, done = done[i], onToggle = { done[i] = !done[i] })
                }
            }

            if (plan.templates.isNotEmpty()) {
                SectionTitle("Message")
                var selectedId by rememberSaveable(plan) { mutableStateOf(plan.templates.first().id) }
                var friendly by rememberSaveable { mutableStateOf(prefs.friendlyTone) }
                val template = plan.templates.firstOrNull { it.id == selectedId } ?: plan.templates.first()
                var text by rememberSaveable(selectedId, friendly) { mutableStateOf(if (friendly) template.friendly else template.formal) }

                if (plan.templates.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        plan.templates.forEach { t ->
                            Chip(t.label, selected = t.id == selectedId, onClick = { selectedId = t.id })
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Chip("Friendly", selected = friendly, onClick = { friendly = true; Graph.prefs.setFriendlyTone(true) })
                    Chip("Formal", selected = !friendly, onClick = { friendly = false; Graph.prefs.setFriendlyTone(false) })
                }
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 2000) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = { Text("Edit before sending") },
                    shape = Radius.m,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.ink, unfocusedBorderColor = c.hairline,
                        focusedLabelColor = c.ink, cursorColor = c.rewind,
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                    ),
                )
                val blanks = blank.findAll(text).count()
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (blanks > 0) "Fill in $blanks blank${if (blanks == 1) "" else "s"} in [brackets] before you send." else "Ready when you are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (blanks > 0) c.rewindText else c.good,
                )
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    UndoButton("Copy", { Launch.copy(ctx, "Message", text); snack("Copied — paste it where you need it") }, Modifier.weight(1f), kind = ButtonKind.Primary, icon = Icons.Rounded.ContentCopy)
                    UndoButton("Share…", { if (!Launch.share(ctx, text)) snack("No app to share with") }, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Share)
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    UndoButton("Text message", { if (!Launch.sms(ctx, text)) snack("No SMS app found") }, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Sms)
                    UndoButton("Email", { if (!Launch.email(ctx, event?.title ?: topic?.title ?: "", text)) snack("No email app found") }, Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Icons.Rounded.Email)
                }
            }

            val links = (listOfNotNull(plan.primary) + plan.secondary + plan.urgent).filter { it.kind != ActionKind.HELP_ME_FIX }.distinctBy { it.label }
            if (links.isNotEmpty()) {
                SectionTitle("Shortcuts")
                links.forEach { a -> LinkRow(a.label, a.hint, iconFor(a)) { host.run(a, event, topic) } }
            }
            Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
        }
    }
}
