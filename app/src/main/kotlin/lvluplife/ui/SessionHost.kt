package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
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

/** No saved private back stack: a session change disposes all account-owned composition state. */
@Composable
fun SessionHost(controller: SessionController, onLogout: () -> Unit = { controller.logout() }, content: @Composable (SessionView) -> Unit = {}) {
    val view by controller.state.collectAsState()
    BackHandler(enabled = view.routes.size > 1) { controller.back() }
    key(view.uid, view.sessionId) {
        BoxWithConstraints {
        val compact=maxHeight<480.dp
        Column(Modifier.padding(if(compact)12.dp else 24.dp)) {
            if(!compact || view.uid==null) Text("Lvl Up Life")
            if (view.uid == null) {
                Text("Iniciá sesión para continuar")
            } else {
                if(compact) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(view.routes.last()==SessionRoute.HISTORY) "Tu historial" else "Lvl Up Life",Modifier.weight(1f))
                    if(view.routes.last()==SessionRoute.HISTORY) ActionButton({ controller.back() }) { Text("Volver") }
                    else ActionButton({ controller.navigate(SessionRoute.HISTORY) }) { Text("Historial") }
                    ActionButton(onClick=onLogout) { Text("Cerrar sesión") }
                } else if (view.routes.last() == SessionRoute.HISTORY) {
                    Text("Tu historial")
                    ActionButton(onClick = { controller.back() }) { Text("Volver") }
                } else {
                    ActionButton(onClick = { controller.navigate(SessionRoute.HISTORY) }) { Text("Historial") }
                }
                view.loads.values.filterNot { compact && it is LoadState.Confirmed }.forEach { LoadStateMessage(it) }
                if (view.pending.any { it.state in setOf(lvluplife.data.PendingState.UNKNOWN, lvluplife.data.PendingState.SENDING, lvluplife.data.PendingState.RESOLVING) }) {
                    Text("Confirmación pendiente")
                }
                Column(Modifier.weight(1f, fill = false)) { content(view) }
                if(!compact) ActionButton(onClick = onLogout) { Text("Cerrar sesión") }
            }
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
