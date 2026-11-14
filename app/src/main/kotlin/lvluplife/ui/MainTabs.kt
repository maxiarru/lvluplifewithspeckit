package lvluplife.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lvluplife.application.*
import lvluplife.ui.theme.*

/** Visible label of each tab, in the bar order (FR-005). */
fun tabLabel(route: SessionRoute): String = when (route) {
    SessionRoute.HOME -> "PJ"
    SessionRoute.ATTRIBUTES -> "Atributos"
    SessionRoute.INFO -> "Info"
    SessionRoute.HISTORY -> "Historial"
    else -> error("Not a tab: $route")
}

/**
 * Session tabs: PJ, Atributos, Info and Historial over a fixed bottom bar.
 * The controllers are created once per session here, so switching tabs never asks for progress again.
 */
@Composable
fun MainTabs(session: SessionController, username: String, onLogout: () -> Unit,
    records: RecordFlowRecords? = null, progress: RecordFlowProgress? = null,
    customActivities: CustomActivityFlowActivities? = null, history: HistoryFlowHistory? = null,
    corrections: HistoryFlowCorrections? = null, reset: ResetFlowOperations? = null) {
    val view by session.state.collectAsState()
    if (view.uid == null) return
    val tab = view.routes.lastOrNull { it in SessionRoute.tabs } ?: SessionRoute.HOME
    key(view.uid, view.sessionId) {
        val shared = if (records != null && progress != null)
            rememberSessionControllers(session, records, progress, customActivities, reset) else null
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                when (tab) {
                    SessionRoute.HOME -> if (shared != null) RecordFlowContent(session, shared, username)
                        else Text(username, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    // Same confirmed progress as PJ: opening the tab does not ask the server again.
                    SessionRoute.ATTRIBUTES -> if (shared != null) {
                        val progressView by shared.progress.state.collectAsState()
                        AttributesScreen(progressView, shared.progress::refresh)
                    } else TabTitle(tabLabel(SessionRoute.ATTRIBUTES))
                    SessionRoute.INFO -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TabTitle(tabLabel(SessionRoute.INFO))
                        ActionButton(onClick = onLogout) { Text("Cerrar sesión") }
                    }
                    SessionRoute.HISTORY -> history?.let { HistoryFlow(session, it, corrections) }
                    else -> Unit
                }
            }
            TabBar(tab, session::selectTab)
        }
    }
}

@Composable
private fun TabTitle(text: String) {
    Text(text, Modifier.padding(vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
}

/** The active tab has the accent background, bold text and an underline indicator, and is exposed as selected (FR-006). */
@Composable
private fun TabBar(selected: SessionRoute, onSelect: (SessionRoute) -> Unit) {
    Row(Modifier.fillMaxWidth().background(ArcadeColors.card).padding(horizontal = 8.dp, vertical = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SessionRoute.tabs.forEach { route ->
            val active = route == selected
            Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
                .background(if (active) ArcadeColors.accent else Color.Transparent)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(route) })
                .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(tabLabel(route), color = ArcadeColors.text, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center)
                if (active) Box(Modifier.padding(top = 4.dp).width(24.dp).height(3.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(ArcadeColors.text).testTag("tab-indicator"))
            }
        }
    }
}
