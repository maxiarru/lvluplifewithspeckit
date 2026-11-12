package lvluplife.ui

import androidx.compose.runtime.*
import lvluplife.application.*

@Composable
fun HistoryFlow(session: SessionController, history: HistoryFlowHistory, corrections: HistoryFlowCorrections? = null) {
    val sessionView by session.state.collectAsState()
    key(sessionView.uid,sessionView.sessionId) {
        val scope = rememberCoroutineScope()
        val controller = remember(session,history) { HistoryController(session,history,scope) }
        val view by controller.state.collectAsState()
        val online = corrections?.online ?: history.online
        var refreshOnConnection by remember { mutableStateOf(false) }
        LaunchedEffect(controller) { controller.refresh() }
        LaunchedEffect(online) {
            if(!online) refreshOnConnection=true
            else if(refreshOnConnection) { refreshOnConnection=false;controller.refresh() }
        }
        if(sessionView.uid != null) HistoryScreen(view,controller,online)
    }
}
