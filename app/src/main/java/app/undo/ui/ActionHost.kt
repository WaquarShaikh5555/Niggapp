package app.undo.ui

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.undo.Graph
import app.undo.capture.Launch
import app.undo.capture.MediaWatcher
import app.undo.capture.NotificationCache
import app.undo.capture.RestoreResult
import app.undo.capture.SettingsWatcher
import app.undo.engine.ActionKind
import app.undo.engine.ActionSpec
import app.undo.engine.EventStatus
import app.undo.engine.EventType
import app.undo.engine.Topic
import app.undo.engine.UndoEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalSnack = compositionLocalOf<(String) -> Unit> { {} }

/**
 * Runs recovery actions. Rule: UNDO only reports success after re-checking the real system state
 * (trash restored, setting reverted). Anything consequential happens in another app or behind an
 * Android confirmation dialog, after the user presses the final button.
 */
class ActionHost(
    private val ctx: Context,
    private val vm: AppViewModel,
    private val snack: (String) -> Unit,
    private val scope: CoroutineScope,
    private val launchSender: (IntentSender, (Boolean) -> Unit) -> Unit,
) {
    fun run(spec: ActionSpec, event: UndoEvent? = null, topic: Topic? = null) {
        when (spec.kind) {
            ActionKind.RESTORE_TRASH -> restoreTrash(event ?: return)
            ActionKind.OPEN_TRASH -> vm.go(Route.Trash)
            ActionKind.RESTORE_SETTING -> restoreSetting(event ?: return)
            ActionKind.OPEN_SETTING_SCREEN -> if (!Launch.settingsScreen(ctx, spec.arg)) snack("Couldn't open Settings on this phone")
            ActionKind.OPEN_ORIGINAL -> {
                val pi = NotificationCache.intentFor(spec.arg ?: event?.extras?.get("nkey"))
                if (!Launch.pendingIntent(ctx, pi)) {
                    val opened = Launch.app(ctx, event?.packageName)
                    snack(if (opened) "The original link had expired — opened the app instead" else "That link has expired")
                }
            }
            ActionKind.OPEN_APP -> if (!Launch.app(ctx, spec.arg)) snack("That app isn't installed or can't be opened")
            ActionKind.OPEN_URL -> if (spec.arg == null || !Launch.url(ctx, spec.arg, spec.hint)) snack("No app here can open that link")
            ActionKind.REINSTALL -> if (spec.arg == null || !Launch.playStore(ctx, spec.arg)) snack("Couldn't open the Play Store")
            ActionKind.DIAL -> if (spec.arg == null || !Launch.dial(ctx, spec.arg)) snack("No phone app available")
            ActionKind.HELP_ME_FIX -> vm.go(Route.Fix(event?.id, topic ?: spec.arg?.let { runCatching { Topic.valueOf(it) }.getOrNull() }))
            ActionKind.OPEN_PLAY_SUBSCRIPTIONS -> if (!Launch.playSubscriptions(ctx)) snack("Couldn't open Google Play")
            ActionKind.OPEN_MESSAGING_GUIDE -> spec.arg?.let { vm.go(Route.Messaging(it)) }
            ActionKind.COPY_TEXT -> {
                Launch.copy(ctx, "UNDO", spec.arg ?: event?.let { copyText(it) } ?: "")
                snack("Copied")
            }
            ActionKind.OPEN_NOTIFICATION_HISTORY -> if (!Launch.notificationHistory(ctx)) snack("Couldn't open notification history")
            ActionKind.OPEN_CAPABILITIES -> vm.go(Route.Capabilities)
            ActionKind.OPEN_TOPIC -> spec.arg?.let { runCatching { Topic.valueOf(it) }.getOrNull() }?.let { vm.go(Route.TopicR(it)) }
        }
    }

    private fun copyText(e: UndoEvent): String = when (e.type) {
        EventType.NOTIFICATIONS_CLEARED -> e.items.joinToString("\n\n") { listOfNotNull(it.title, it.subtitle).joinToString("\n") }
        else -> listOfNotNull(e.appLabel, e.title, e.body).joinToString("\n")
    }

    private fun restoreTrash(e: UndoEvent) {
        val uris = e.items.mapNotNull { it.ref }.mapNotNull { MediaWatcher.uriFor(it) }
        val sender = MediaWatcher.restoreRequest(ctx, uris)
        if (sender == null) {
            snack("Android's trash isn't available here — check your gallery's bin")
            return
        }
        launchSender(sender) { ok ->
            scope.launch {
                val restored = withContext(Dispatchers.IO) { MediaWatcher.countRestored(ctx, uris) }
                when {
                    restored == uris.size -> {
                        Graph.store.setStatus(e.id, EventStatus.UNDONE)
                        snack(if (restored == 1) "Restored — checked, it's back" else "All $restored restored — checked, they're back")
                    }
                    restored > 0 -> snack("Restored $restored of ${uris.size}. The rest may have expired.")
                    ok -> snack("Android didn't restore anything — the items may have expired")
                    else -> snack("Nothing changed")
                }
            }
        }
    }

    fun restoreSetting(e: UndoEvent) {
        scope.launch {
            when (SettingsWatcher.restore(ctx, e)) {
                RestoreResult.DONE -> snack("Switched back — checked, it worked")
                RestoreResult.NEEDS_WRITE_PERMISSION -> {
                    vm.pendingRestore = e.id
                    if (Launch.writeSettings(ctx)) snack("Allow UNDO to change system settings, then come back — it'll finish the job")
                    else snack("This phone doesn't allow that — opening Settings instead").also { Launch.settingsScreen(ctx, e.extras["key"]) }
                }
                RestoreResult.OPENED_SETTINGS -> snack("Android doesn't let apps change this — switch it back on this screen")
                RestoreResult.FAILED -> snack("Android didn't allow that change")
            }
        }
    }

    fun launchTrashRestore(uris: List<android.net.Uri>, after: (Int) -> Unit) {
        val sender = MediaWatcher.restoreRequest(ctx, uris) ?: run { snack("Android's trash isn't available here"); return }
        launchSender(sender) { _ ->
            scope.launch {
                val n = withContext(Dispatchers.IO) { MediaWatcher.countRestored(ctx, uris) }
                after(n)
            }
        }
    }
}

@Composable
fun rememberActionHost(vm: AppViewModel): ActionHost {
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        pending[0]?.invoke(res.resultCode == Activity.RESULT_OK)
        pending[0] = null
    }
    return remember(ctx, vm) {
        ActionHost(ctx, vm, snack, scope) { sender, cb ->
            pending[0] = cb
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}
