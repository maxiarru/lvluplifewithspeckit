package lvluplife.ui

import androidx.compose.runtime.*
import lvluplife.application.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Controllers shared by the session's tabs (progress, record form, custom activities and reset). */
class SessionControllers internal constructor(
    val records: RecordFlowRecords,
    val customActivities: CustomActivityFlowActivities?,
    val resetOperations: ResetFlowOperations?,
    val form: RecordFormController,
    val progress: ProgressController,
    val custom: CustomActivityController?,
    val reset: ResetController?,
)

/**
 * Creates the session's controllers once and keeps their refresh and recovery effects running.
 * Call it inside a `key(uid, sessionId)` block so a session change disposes them.
 */
@Composable
fun rememberSessionControllers(session: SessionController, records: RecordFlowRecords, progress: RecordFlowProgress,
    customActivities: CustomActivityFlowActivities? = null, reset: ResetFlowOperations? = null): SessionControllers {
    val scope = rememberCoroutineScope()
    val controller = remember(session, records) { RecordFormController(session, records, scope) }
    val home = remember(session, progress) { ProgressController(session, progress, scope) }
    val view by controller.state.collectAsState()
    val resetController = remember(session, reset) { reset?.let { ResetController(session,it,scope) } }
    val resetView = if (resetController != null) {
        val state by resetController.state.collectAsState()
        state
    } else null
    DisposableEffect(resetController) { onDispose { resetController?.dispose() } }
    LaunchedEffect(resetController) { resetController?.start() }
    LaunchedEffect(resetView?.settlement) { if ((resetView?.settlement ?: 0) > 0) {
        home.refresh()
        controller.refreshCatalog()
    } }
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
    LaunchedEffect(online, view.busy, customView?.busy, resetView?.busy, recoverOnConnection) {
        if (!online) recoverOnConnection = true
        else if (recoverOnConnection && !view.busy && customView?.busy != true && resetView?.busy != true) {
            recoverOnConnection = false
            controller.start()
            custom?.start()
            resetController?.start()
            home.refresh()
        }
    }
    return remember(controller, home, custom, resetController) {
        SessionControllers(records, customActivities, reset, controller, home, custom, resetController)
    }
}

/** Standalone host: creates its own session controllers. Inside the tabs, [MainTabs] shares them instead. */
@Composable
fun RecordFlow(session: SessionController, records: RecordFlowRecords, progress: RecordFlowProgress,
    customActivities: CustomActivityFlowActivities? = null, reset: ResetFlowOperations? = null) {
    val sessionView by session.state.collectAsState()
    key(sessionView.uid, sessionView.sessionId) {
        RecordFlowContent(session, rememberSessionControllers(session, records, progress, customActivities, reset))
    }
}

/** PJ content (home, record form and custom activities) over shared controllers. Classes and reset live in Info. */
@Composable
fun RecordFlowContent(session: SessionController, shared: SessionControllers, username: String? = null) {
    val sessionView by session.state.collectAsState()
    val records = shared.records
    val controller = shared.form
    val home = shared.progress
    val custom = shared.custom
    val resetController = shared.reset
    val homeView by home.state.collectAsState()
    val view by controller.state.collectAsState()
    val resetView = if (resetController != null) {
        val state by resetController.state.collectAsState()
        state
    } else null
    val customView = if (custom != null) {
        val state by custom.state.collectAsState()
        state
    } else null
    val online = records.online
    // FR-008: a definition created from Registrar comes back selected, by the id the server confirmed, keeping the date.
    var returnToRecord by remember { mutableStateOf(false) }
    LaunchedEffect(returnToRecord, customView?.open, customView?.createdActivityId, view.options, view.busy) {
        if (!returnToRecord || customView == null || customView.open) return@LaunchedEffect
        val created = customView.createdActivityId
        if (created == null) { if (customView.pending == null && !customView.unresolved) returnToRecord = false }
        else if (!view.busy && view.options.any { it.id == created }) {
            controller.change(view.draft.copy(activityId = created))
            custom?.consumeCreated()
            returnToRecord = false
        }
    }
    val resetLocked=resetView?.let { it.busy || it.pending != null || it.unresolved } == true
    if (sessionView.uid != null && resetView?.ready == false) {
        androidx.compose.material3.Text("Cargando reinicios…")
    } else if (sessionView.uid != null) Column(if(!view.open && customView?.open != true) Modifier.verticalScroll(rememberScrollState()) else Modifier) {
        if (customView?.open != true) {
            if (!view.open) {
                val locked = view.busy || view.pending != null || view.unresolved
                HomeScreen(homeView, home::refresh, username,
                    onRecord = if (!resetLocked && view.options.isNotEmpty()) controller::open else null, recordEnabled = !locked,
                    quickRecord = if (!resetLocked) ({ QuickRecordActions(view, controller, online && homeView.value != null) }) else null)
                // The reset lives in Info (FR-011); while one is unresolved, recording stays locked and PJ says why.
                if (resetLocked) androidx.compose.material3.Text("Hay un reinicio en curso. Revisalo en Info.",
                    Modifier.testTag("pj-reset-pending"))
            }
            if (!resetLocked) RecordScreen(view, controller, online, showOpenButton = false,
                onCreateCustom = custom?.let { c -> { returnToRecord = true; c.open() } }, createCustomEnabled = customView?.busy == false)
        }
        // Outcome of the last definition (also a recovered one) once its screen is closed, in Registrar or in PJ.
        if (customView?.open != true) customView?.message?.let { androidx.compose.material3.Text(it, Modifier.testTag("custom-outcome")) }
        // Custom activities are created from Registrar (FR-008); the screen shows while open or with an unresolved definition.
        if (!resetLocked && custom != null && customView != null && (customView.open || customView.pending != null || customView.unresolved))
            CustomActivityScreen(customView, custom, shared.customActivities?.online ?: online)
    }
}
