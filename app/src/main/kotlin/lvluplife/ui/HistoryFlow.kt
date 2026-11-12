package lvluplife.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import lvluplife.application.*

@Composable
fun HistoryFlow(session: SessionController, history: HistoryFlowHistory, corrections: HistoryFlowCorrections? = null) {
    val sessionView by session.state.collectAsState()
    key(sessionView.uid,sessionView.sessionId) {
        val scope = rememberCoroutineScope()
        val controller = remember(session,history) { HistoryController(session,history,scope) }
        val correction = remember(session,corrections) { corrections?.let { CorrectionController(session,it,scope) } }
        val view by controller.state.collectAsState()
        val correctionView = if(correction != null) { val state by correction.state.collectAsState();state } else null
        val online = corrections?.online ?: history.online
        var refreshOnConnection by remember { mutableStateOf(false) }
        DisposableEffect(correction) { onDispose { correction?.dispose() } }
        LaunchedEffect(controller) { controller.refresh() }
        LaunchedEffect(correction) { correction?.start() }
        LaunchedEffect(correctionView?.settlement) { if((correctionView?.settlement ?: 0)>0)controller.refresh() }
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            var stopped=false
            val observer=LifecycleEventObserver { _,event ->
                if(event==Lifecycle.Event.ON_STOP)stopped=true
                if(event==Lifecycle.Event.ON_RESUME && stopped) { stopped=false;refreshOnConnection=true }
            }
            lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(online,correctionView?.busy,refreshOnConnection) {
            if(!online) refreshOnConnection=true
            else if(refreshOnConnection && correctionView?.busy != true) { refreshOnConnection=false;controller.refresh();correction?.start() }
        }
        if(sessionView.uid != null) {
            if(correctionView != null && !correctionView.ready) Text("Cargando correcciones…")
            else if(correction != null && correctionView?.draft != null) EditRecordScreen(correctionView,correction,online)
            else HistoryScreen(view,controller,online,correction,correctionView) {
                if(correction != null && correctionView != null)CorrectionStatus(correctionView,correction,online)
            }
            correctionView?.confirmingDelete?.let {
                AlertDialog(onDismissRequest={ correction?.cancelDelete() },title={ Text("Eliminar registro") },
                    text={ Text("Se quitará del historial y se recalculará tu progreso.") },
                    confirmButton={ TextButton({ correction?.delete() },enabled=online && !correctionView.busy) { Text("Confirmar eliminación") } },
                    dismissButton={ TextButton({ correction?.cancelDelete() }) { Text("Cancelar") } })
            }
        }
    }
}
