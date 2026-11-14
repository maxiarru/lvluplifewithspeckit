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
 * Navigation lives in [MainTabs]; this host only keeps the load and pending notices.
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
            Column(Modifier.padding(horizontal = padding).padding(top = 8.dp)) {
                view.loads.values.filterNot { compact && it is LoadState.Confirmed }.forEach { LoadStateMessage(it) }
                if (view.pending.any { it.state in setOf(lvluplife.data.PendingState.UNKNOWN, lvluplife.data.PendingState.SENDING, lvluplife.data.PendingState.RESOLVING) }) {
                    Text("Confirmación pendiente")
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content(view) }
        }
        }
    }
}

@Composable
fun LoadStateMessage(state: LoadState<*>) {
    Text(when (state) {
        LoadState.Loading -> "Cargando…"
        is LoadState.Confirmed -> "Datos confirmados"
        is LoadState.Stale -> "Datos desactualizados. Volvé a intentar."
        LoadState.Empty -> "Sin resultados"
        is LoadState.Error -> "No pudimos cargar los datos. Intentá de nuevo."
        is LoadState.Pending -> "Confirmación pendiente"
    })
}
