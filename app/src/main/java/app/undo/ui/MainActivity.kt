package app.undo.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.undo.Graph
import app.undo.capture.AppsWatcher
import app.undo.capture.MediaWatcher
import app.undo.capture.Watchers
import app.undo.ui.components.reducedMotion
import app.undo.ui.screens.CapabilitiesScreen
import app.undo.ui.screens.EventScreen
import app.undo.ui.screens.FixScreen
import app.undo.ui.screens.HistoryScreen
import app.undo.ui.screens.HomeScreen
import app.undo.ui.screens.MessagingScreen
import app.undo.ui.screens.OnboardingScreen
import app.undo.ui.screens.PanicScreen
import app.undo.ui.screens.SettingsScreen
import app.undo.ui.screens.TopicScreen
import app.undo.ui.screens.TrashScreen
import app.undo.ui.theme.LocalUndo
import app.undo.ui.theme.Radius
import app.undo.ui.theme.UndoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!Graph.prefs.current.onboarded && vm.stack.size == 1) vm.replace(Route.Onboarding)

        // Privacy controls: hide UNDO's content from the Recents thumbnail; optionally block screenshots.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                Graph.prefs.state.collect { p ->
                    if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!p.hideInRecents)
                    if (p.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            UndoTheme {
                UndoRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Watchers.start(this, "ui")
        val ctx = applicationContext
        Graph.store.scope.launch {
            AppsWatcher.diff(ctx)
            MediaWatcher.diff(ctx)
        }
    }

    override fun onResume() {
        super.onResume()
        Graph.purge()
        vm.bump()
    }

    override fun onStop() {
        super.onStop()
        Watchers.stop(this, "ui")
    }
}

@Composable
private fun UndoRoot(vm: AppViewModel) {
    val c = LocalUndo.current
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = remember {
        { msg: String ->
            scope.launch {
                snackHost.currentSnackbarData?.dismiss()
                snackHost.showSnackbar(msg)
            }
            Unit
        }
    }
    val reduce = reducedMotion()

    CompositionLocalProvider(LocalSnack provides show) {
        Box(Modifier.fillMaxSize().background(c.paper)) {
            BackHandler(enabled = vm.stack.size > 1) { vm.back() }
            val target = vm.current to vm.stack.size
            AnimatedContent(
                targetState = target,
                transitionSpec = {
                    val forward = targetState.second >= initialState.second
                    if (reduce) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    else if (forward) {
                        (slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it / 12 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(280)) { -it / 12 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280)) { it / 5 } + fadeOut(tween(160)))
                    }
                },
                label = "route",
            ) { (route, _) ->
                when (route) {
                    Route.Home -> HomeScreen(vm)
                    is Route.Event -> EventScreen(vm, route.id)
                    is Route.Fix -> FixScreen(vm, route.eventId, route.topic)
                    Route.Panic -> PanicScreen(vm)
                    is Route.TopicR -> TopicScreen(vm, route.topic)
                    is Route.Messaging -> MessagingScreen(vm, route.pkg)
                    Route.Trash -> TrashScreen(vm)
                    Route.Capabilities -> CapabilitiesScreen(vm)
                    Route.Settings -> SettingsScreen(vm)
                    Route.History -> HistoryScreen(vm)
                    Route.Onboarding -> OnboardingScreen(vm)
                }
            }
            SnackbarHost(
                snackHost,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp),
            ) { data ->
                Box(
                    Modifier
                        .clip(Radius.m)
                        .background(c.ink)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(data.visuals.message, color = c.onInk, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
