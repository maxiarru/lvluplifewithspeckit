package lvluplife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lvluplife.application.SessionController
import lvluplife.application.SessionRoute
import lvluplife.application.SessionView

/**
 * No saved private back stack: a session change disposes all account-owned composition state.
 * Screens own their loading feedback; this host keeps exceptional and pending notices.
 */
@Composable
fun SessionHost(controller: SessionController, content: @Composable (SessionView) -> Unit = {}) {
    val view by controller.state.collectAsState()
    val canGoBack = view.uid != null && (view.routes.size > 1 || view.routes.single() != SessionRoute.HOME)
    BackHandler(enabled = canGoBack) { controller.back() }
    key(view.uid, view.sessionId) {
        BoxWithConstraints {
        val compact=maxHeight<480.dp
        val padding=if(compact)12.dp else 24.dp
        if (view.uid == null) {
            Column(Modifier.padding(padding)) {
                Text("Lvl Up Life")
                Text("Iniciá sesión para continuar")
            }
        } else Column(Modifier.fillMaxSize()) {
            val notices = view.loads.values.filterNot {
                it is LoadState.Loading || it is LoadState.Confirmed
            }.distinctBy { it::class }
            val pending = view.pending.any {
                it.state in setOf(lvluplife.data.PendingState.UNKNOWN, lvluplife.data.PendingState.SENDING, lvluplife.data.PendingState.RESOLVING)
            } && notices.none { it is LoadState.Pending }
            if (notices.isNotEmpty() || pending) {
                Column(Modifier.padding(horizontal = padding).padding(top = 8.dp)) {
                    notices.forEach { LoadStateMessage(it) }
                    if (pending) Text("Confirmación pendiente")
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content(view) }
        }
        }
    }
}

@Composable
fun LoadStateMessage(state: LoadState<*>) {
    val message = when (state) {
        LoadState.Loading, is LoadState.Confirmed -> return
        is LoadState.Stale -> "Datos desactualizados. Volvé a intentar."
        LoadState.Empty -> "Sin resultados"
        is LoadState.Error -> "No pudimos cargar los datos. Intentá de nuevo."
        is LoadState.Pending -> "Confirmación pendiente"
    }
    Text(message)
}
