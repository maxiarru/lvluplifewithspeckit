package lvluplife.ui

import androidx.compose.runtime.*
import lvluplife.application.*
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Form host for T042. Confirmed home/progress presentation is connected in T043. */
@Composable
fun RecordFlow(session: SessionController, records: RecordFlowRecords, progress: RecordFlowProgress,
    customActivities: CustomActivityFlowActivities? = null) {
    val sessionView by session.state.collectAsState()
    key(sessionView.uid, sessionView.sessionId) {
        val scope = rememberCoroutineScope()
        val controller = remember(session, records) { RecordFormController(session, records, scope) }
        val home = remember(session, progress) { ProgressController(session, progress, scope) }
        val homeView by home.state.collectAsState()
        val view by controller.state.collectAsState()
        val online = records.online
        var recoverOnConnection by remember { mutableStateOf(false) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, home, controller) {
            var stopped = false
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) stopped = true
                if (event == Lifecycle.Event.ON_RESUME && stopped) {
                    stopped = false
                    recoverOnConnection = true
                    if (!records.online) home.refresh()
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(controller) { controller.start() }
        LaunchedEffect(home) { home.refresh() }
        LaunchedEffect(view.settlement) { if (view.settlement > 0) home.refresh() }
        LaunchedEffect(online, view.busy, recoverOnConnection) {
            if (!online) recoverOnConnection = true
            else if (recoverOnConnection && !view.busy) {
                recoverOnConnection = false
                controller.start()
                home.refresh()
            }
        }
        if (sessionView.uid != null) Column {
            if (!view.open) HomeScreen(homeView, home::refresh)
            RecordScreen(view, controller, online)
        }
    }
}
