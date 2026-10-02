package app.undo.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.undo.Graph
import app.undo.capture.AppInfo
import app.undo.capture.Launch
import app.undo.capture.MediaWatcher
import app.undo.capture.TrashedMedia
import app.undo.capture.Watchers
import app.undo.engine.AppCatalog
import app.undo.engine.EventStatus
import app.undo.ui.AppViewModel
import app.undo.ui.Fmt
import app.undo.ui.LocalSnack
import app.undo.ui.rememberActionHost
import app.undo.ui.components.ButtonKind
import app.undo.ui.components.LinkRow
import app.undo.ui.components.SectionTitle
import app.undo.ui.components.Skeleton
import app.undo.ui.components.SurfaceCard
import app.undo.ui.components.TopBar
import app.undo.ui.components.UndoButton
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrashScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val snack = LocalSnack.current
    val host = rememberActionHost(vm)
    val tick by vm.capsTick
    var reload by remember { mutableIntStateOf(0) }
    val access = remember(tick, reload) { MediaWatcher.access(ctx) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Watchers.refresh(ctx)
        vm.bump()
        reload++
    }
    var trash by remember { mutableStateOf<List<TrashedMedia>?>(null) }
    LaunchedEffect(access, reload) {
        trash = null
        trash = withContext(Dispatchers.IO) { MediaWatcher.trashList(ctx) }
    }
    val selected = remember { mutableStateListOf<String>() }
    val galleries = remember { AppCatalog.galleries.filterKeys { AppInfo.isInstalled(ctx, it) } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Android trash", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f).padding(horizontal = Space.xl)) {
            item {
                Text(
                    "Photos and videos that apps moved to Android's system trash. They're kept for about 30 days, then deleted for good.",
                    style = MaterialTheme.typography.bodyMedium, color = c.inkSoft,
                )
            }
            when {
                !MediaWatcher.trashSupported -> item {
                    Column {
                        Spacer(Modifier.height(Space.l))
                        SurfaceCard(color = c.sunk) {
                            Text("Android's shared trash arrived in Android 11. On this phone (Android ${Build.VERSION.RELEASE}), check your gallery app's own bin below.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                        }
                    }
                }
                access == MediaWatcher.Access.NONE -> item {
                    Column {
                        Spacer(Modifier.height(Space.l))
                        SurfaceCard {
                            Column {
                                Text("Let UNDO see your photos & videos", style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Spacer(Modifier.height(Space.xs))
                                Text("Only to list what's in the trash and notice deletions. Pictures never leave this phone — UNDO has no internet permission.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                                Spacer(Modifier.height(Space.m))
                                UndoButton("Allow access", { permLauncher.launch(MediaWatcher.permissionsToRequest()) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                trash == null -> items(4) {
                    Row(Modifier.padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Skeleton(Modifier.size(56.dp))
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp))
                            Spacer(Modifier.height(6.dp))
                            Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
                        }
                    }
                }
                trash!!.isEmpty() -> item {
                    Column {
                        Spacer(Modifier.height(Space.l))
                        SurfaceCard(color = c.sunk) {
                            Column {
                                Text("Android's trash is empty", style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Text(
                                    if (access == MediaWatcher.Access.PARTIAL) "You've only shared some photos with UNDO, so it may not see everything." else "Some gallery apps keep a separate bin of their own — check below.",
                                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                                )
                            }
                        }
                    }
                }
                else -> {
                    item {
                        SectionTitle("${trash!!.size} in the trash") {
                            val all = selected.size == trash!!.size
                            Text(
                                if (all) "Clear" else "Select all",
                                style = MaterialTheme.typography.labelLarge, color = c.rewindText,
                                modifier = Modifier.clip(Radius.s).toggleable(all, role = Role.Checkbox) {
                                    selected.clear(); if (!all) selected.addAll(trash!!.map { it.ref })
                                }.padding(Space.xs),
                            )
                        }
                    }
                    items(trash!!, key = { it.ref }) { m ->
                        TrashRow(m, m.ref in selected) { on -> if (on) selected.add(m.ref) else selected.remove(m.ref) }
                    }
                }
            }
            item {
                Column {
                    if (access == MediaWatcher.Access.PARTIAL) {
                        Spacer(Modifier.height(Space.m))
                        LinkRow("Share more photos with UNDO", "You chose “selected photos” — UNDO only sees those.") { permLauncher.launch(MediaWatcher.permissionsToRequest()) }
                    }
                    SectionTitle("Gallery bins")
                    Text("Google Photos, Samsung Gallery and others keep their own bin that UNDO can't see. Restore from there:", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Spacer(Modifier.height(Space.s))
                    galleries.forEach { (pkg, g) ->
                        LinkRow("${g.name} bin", g.binPath, Icons.Rounded.OpenInNew) {
                            val ok = g.url?.let { Launch.url(ctx, it, pkg) } ?: false
                            if (!ok && !Launch.app(ctx, pkg)) snack("Couldn't open ${g.name}")
                        }
                    }
                    if (galleries.isEmpty()) Text("Open your gallery or files app and look for Trash, Bin or Recently deleted.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                    Spacer(Modifier.navigationBarsPadding().height(Space.xxl))
                }
            }
        }
        if (selected.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding().padding(Space.l)) {
                UndoButton(
                    "Restore ${selected.size} item${if (selected.size == 1) "" else "s"}",
                    {
                        val uris = selected.mapNotNull { MediaWatcher.uriFor(it) }
                        val refs = selected.toSet()
                        host.launchTrashRestore(uris) { n ->
                            if (n > 0) {
                                snack("Restored $n — they're back where they were")
                                // Mark matching events as genuinely undone once every item in them is back.
                                Graph.store.events.value.orEmpty()
                                    .filter { e -> e.status == EventStatus.OPEN && e.items.isNotEmpty() && e.items.all { it.ref in refs } }
                                    .forEach { Graph.store.setStatus(it.id, EventStatus.UNDONE) }
                            } else snack("Nothing was restored")
                            selected.clear()
                            reload++
                        }
                    },
                    Modifier.fillMaxWidth(), kind = ButtonKind.Accent, icon = Icons.Rounded.Restore,
                )
            }
        }
    }
}

@Composable
private fun TrashRow(m: TrashedMedia, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val c = LocalUndo.current
    val thumb by produceState<Bitmap?>(null, m.uri) {
        value = if (Build.VERSION.SDK_INT >= 29) withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(m.uri, Size(160, 160), null) }.getOrNull()
        } else null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.m)
            .toggleable(checked, remember { MutableInteractionSource() }, rememberRipple(), role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(Radius.s).background(c.sunk), contentAlignment = Alignment.Center) {
            val b = thumb
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(if (m.isVideo) Icons.Rounded.Movie else Icons.Rounded.Image, null, tint = c.muted)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(m.folder, MediaWatcher.humanSize(m.size), m.expiresAt?.let { "gone for good ${Fmt.until(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s))
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (checked) c.ink else c.surface)
                .border(1.5.dp, if (checked) c.ink else c.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = c.onInk, modifier = Modifier.size(16.dp))
        }
    }
}
