package lvluplife.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import lvluplife.application.AccessController
import lvluplife.application.ProfileLookup
import lvluplife.application.AccessAccounts
import lvluplife.application.SessionController
import lvluplife.data.GoogleSignIn
import lvluplife.application.RecordFlowRecords
import lvluplife.application.RecordFlowProgress
import lvluplife.application.CustomActivityFlowActivities

@Composable
fun AppNavigation(session: SessionController, google: GoogleSignIn, accounts: AccessAccounts, records: RecordFlowRecords? = null,
    progress: RecordFlowProgress? = null, customActivities: CustomActivityFlowActivities? = null) {
    val scope = rememberCoroutineScope()
    val controller = remember(session, google, accounts) { AccessController(session, google, accounts, scope) }
    val access by controller.state.collectAsState()
    val view by session.state.collectAsState()
    LaunchedEffect(controller) { controller.start() }
    when {
        view.uid == null -> AccessScreen(access.busy, access.error, controller::signIn)
        access.profile == ProfileLookup.Required -> ProfileScreen(access, controller)
        else -> SessionHost(session, onLogout = controller::logout) {
            (access.profile as? ProfileLookup.Existing)?.let {
                Text(it.username)
                if (records != null && progress != null && view.routes.lastOrNull() == lvluplife.application.SessionRoute.HOME)
                    RecordFlow(session, records, progress, customActivities)
            }
            if (access.profile == null) {
                Text(access.error ?: "Cargando…")
                if (access.error != null) Button(onClick = controller::retryLookup, enabled = !access.busy) { Text("Reintentar") }
            }
        }
    }
}
