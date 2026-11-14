package lvluplife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lvluplife.application.*
import lvluplife.ui.theme.*

/**
 * Rule texts shown in Info (FR-012). Each line is checked against the current PRD: RF-13, RF-17, RF-18, RF-20 and the
 * XP rules section for XP; RF-23, RF-24, RF-27, RF-30, RF-32 to RF-34 and RF-37 for levels. They are fixed texts:
 * the app does not compute anything with them.
 */
internal object InfoRules {
    val xp = listOf(
        "Actividades por duración, excepto Dormir: 1 XP por cada 5 minutos completos.",
        "Dormir: 3 XP a MEN por cada 60 minutos de sueño de la misma fecha.",
        "Comida saludable: 7 XP a CON por cada una de las primeras cuatro comidas del día.",
        "Con dos atributos, 2/3 de la XP van al principal y 1/3 al secundario.",
        "Topes por día: 48 XP para STR, DEX, INT y WIT; 30 XP para MEN; 28 XP para CON.",
        "Los topes se cuentan por la fecha de la actividad. Podés registrar hoy, ayer o anteayer.",
    )
    /** FR-015: what the PJ quick buttons send. */
    val quick = listOf(
        "«Comida» registra una comida saludable para hoy con un toque.",
        "«Dormir» registra 7 horas de sueño para hoy con un toque.",
        "Si dormiste otra cantidad, o fue otro día, usá «+ Registrar actividad».",
    )
    val levels = listOf(
        "Un atributo en nivel N sube al juntar 30 × N XP. El nivel máximo es 50.",
        "Tu nivel general sube con las subidas de tus atributos, hasta el nivel 50.",
        "Desde el séptimo día seguido sin actividad, un atributo pierde 5 XP por día, sin bajar de nivel ni de 0 XP.",
        "Etapas de tu personaje: I en los niveles 1 a 14, II en los niveles 15 a 34 y III en los niveles 35 a 50.",
    )
}

/**
 * Info tab (US6, FR-011): rule cards, the class catalogue (which comes back to Info), the progression reset with
 * explicit confirmation (RF-41, RF-43) and logout.
 */
@Composable
fun InfoScreen(session: SessionController, shared: SessionControllers?, onLogout: () -> Unit) {
    var catalogOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = catalogOpen) { catalogOpen = false }
    val progressView = shared?.progress?.state?.collectAsState()?.value
    val confirmedLevel = progressView?.value?.generalLevel
    if (catalogOpen && shared != null && confirmedLevel != null) {
        val scope = rememberCoroutineScope()
        val catalog = remember(session, shared.records) { ClassCatalogController(session, shared.records, scope) }
        val catalogView by catalog.state.collectAsState()
        DisposableEffect(catalog) { onDispose { catalog.dispose() } }
        LaunchedEffect(catalog, shared.records.online) { catalog.refresh() }
        ClassCatalogScreen(catalogView, confirmedLevel, progressView.stale, catalog::refresh) { catalogOpen = false }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Info", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text("Reglas del juego, clases y tu personaje.", color = ArcadeColors.muted)
        InfoCard("Cómo ganás XP", "info-xp") { InfoRules.xp.forEach { Text(it, color = ArcadeColors.text) } }
        InfoCard("Registro rápido", "info-quick") { InfoRules.quick.forEach { Text(it, color = ArcadeColors.text) } }
        InfoCard("Niveles y constancia", "info-levels") { InfoRules.levels.forEach { Text(it, color = ArcadeColors.text) } }
        InfoCard("Clases", "info-classes") {
            Text("26 clases, cada una con sus etapas desbloqueadas o bloqueadas según tu nivel general.", color = ArcadeColors.text)
            ActionButton(onClick = { catalogOpen = true }, enabled = confirmedLevel != null,
                modifier = Modifier.testTag("info-open-classes")) { Text("Ver todas las clases") }
            if (shared != null && confirmedLevel == null) Text("Disponible cuando se confirme tu progreso.", color = ArcadeColors.text)
        }
        val reset = shared?.reset
        if (shared != null && reset != null) InfoCard("Tu personaje", "info-reset") {
            Text("Reiniciar vuelve tu personaje a nivel 1. Se conservan tu cuenta, tu nombre de usuario, tus actividades " +
                "personalizadas y tu historial.", color = ArcadeColors.text)
            val resetView by reset.state.collectAsState()
            val online = shared.resetOperations?.online ?: shared.records.online
            if (!resetView.ready) Text("Cargando reinicios…", color = ArcadeColors.text)
            else ResetScreen(resetView, reset, progressView?.value?.cycleId, online)
            // Explains the disabled button; with an unresolved reset, ResetScreen already says it.
            val locked = resetView.busy || resetView.pending != null || resetView.unresolved
            if (!online && !locked) Text("Sin conexión", color = ArcadeColors.text)
        }
        ActionButton(onClick = onLogout, modifier = Modifier.testTag("info-logout")) { Text("Cerrar sesión") }
    }
}

@Composable
private fun InfoCard(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ArcadeColors.card).padding(14.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.semantics { heading() }, color = ArcadeColors.text, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
