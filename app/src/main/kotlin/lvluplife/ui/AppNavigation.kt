package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lvluplife.application.AccessController
import lvluplife.application.ProfileLookup
import lvluplife.application.AccessAccounts
import lvluplife.application.SessionController
import lvluplife.data.GoogleSignIn
import lvluplife.application.RecordFlowRecords
import lvluplife.application.RecordFlowProgress
import lvluplife.application.CustomActivityFlowActivities
import lvluplife.application.HistoryFlowHistory
import lvluplife.application.HistoryFlowCorrections
import lvluplife.application.ResetFlowOperations

@Composable
fun AppNavigation(session: SessionController, google: GoogleSignIn, accounts: AccessAccounts, records: RecordFlowRecords? = null,
    progress: RecordFlowProgress? = null, customActivities: CustomActivityFlowActivities? = null, history: HistoryFlowHistory? = null,
    corrections: HistoryFlowCorrections? = null, reset: ResetFlowOperations? = null) {
    val scope = rememberCoroutineScope()
    val controller = remember(session, google, accounts) { AccessController(session, google, accounts, scope) }
    val access by controller.state.collectAsState()
    val view by session.state.collectAsState()
    LaunchedEffect(controller) { controller.start() }
    when {
        view.uid == null -> AccessScreen(access.busy, access.error, controller::signIn)
        access.profile == ProfileLookup.Required -> ProfileScreen(access, controller)
        else -> SessionHost(session) {
            when (val profile = access.profile) {
                is ProfileLookup.Existing -> MainTabs(session, profile.username, controller::logout,
                    records, progress, customActivities, history, corrections, reset)
                null -> Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(access.error ?: "Cargando…")
                    if (access.error != null) {
                        ActionButton(onClick = controller::retryLookup, enabled = !access.busy) { Text("Reintentar") }
                        ActionButton(onClick = controller::logout) { Text("Cerrar sesión") }
                    }
                }
                else -> Unit
            }
        }
    }
}
