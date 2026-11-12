package lvluplife.ui

import androidx.compose.runtime.*
import lvluplife.application.*
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
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
        var catalogOpen by remember { mutableStateOf(false) }
        BackHandler(enabled=catalogOpen) { catalogOpen=false }
        val custom = remember(session, customActivities) { customActivities?.let { CustomActivityController(session, it, scope) } }
        val customView = if (custom != null) {
            val state by custom.state.collectAsState()
            state
        } else null
        DisposableEffect(custom) { onDispose { custom?.dispose() } }
        LaunchedEffect(custom) { custom?.start() }
        LaunchedEffect(customView?.settlement) { if ((customView?.settlement ?: 0) > 0) {
            controller.refreshCatalog()
            custom?.refreshList()
        } }
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
        LaunchedEffect(online, view.busy, customView?.busy, recoverOnConnection) {
            if (!online) recoverOnConnection = true
            else if (recoverOnConnection && !view.busy && customView?.busy != true) {
                recoverOnConnection = false
                controller.start()
                custom?.start()
                home.refresh()
            }
        }
        val confirmedLevel=homeView.value?.generalLevel
        if (sessionView.uid != null && catalogOpen && confirmedLevel!=null) {
            val catalog=remember(session,records) { ClassCatalogController(session,records,scope) }
            val catalogView by catalog.state.collectAsState()
            DisposableEffect(catalog) { onDispose { catalog.dispose() } }
            LaunchedEffect(catalog,online) { catalog.refresh() }
            ClassCatalogScreen(catalogView,confirmedLevel,homeView.stale,
                catalog::refresh,{ catalogOpen=false })
        } else if (sessionView.uid != null) Column {
            if (customView?.open != true) {
                if (!view.open) {
                    HomeScreen(homeView, home::refresh,{ catalogOpen=true })
                }
                RecordScreen(view, controller, online)
            }
            if (!view.open && custom != null && customView != null)
                CustomActivityScreen(customView, custom, customActivities?.online ?: online)
        }
    }
}
