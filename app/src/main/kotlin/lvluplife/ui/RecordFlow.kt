package lvluplife.ui

import androidx.compose.runtime.*
import lvluplife.application.*

/** Form host for T042. Confirmed home/progress presentation is connected in T043. */
@Composable
fun RecordFlow(session: SessionController, records: RecordFlowRecords, progress: RecordFlowProgress) {
    val sessionView by session.state.collectAsState()
    key(sessionView.uid, sessionView.sessionId) {
        val scope = rememberCoroutineScope()
        val controller = remember(session, records) { RecordFormController(session, records, scope) }
        val view by controller.state.collectAsState()
        val online = records.online
        var recoverOnConnection by remember { mutableStateOf(false) }
        LaunchedEffect(controller) { controller.start() }
        LaunchedEffect(online, view.busy) {
            if (!online) recoverOnConnection = true
            else if (recoverOnConnection && !view.busy) {
                recoverOnConnection = false
                controller.start()
            }
        }
        if (sessionView.uid != null) RecordScreen(view, controller, online)
    }
}
