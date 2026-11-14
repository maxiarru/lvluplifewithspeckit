package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lvluplife.application.ProgressView
import lvluplife.application.RecordAttributeView

/** Attribute order of the PJ mosaic. */
private val pjAttributes = listOf("STR", "DEX", "INT", "WIT", "MEN", "CON")

/**
 * PJ tab (US3, FR-007): greeting, large cutout of the current class and stage, class, stage and general level,
 * a mosaic of the six attributes, «+ Registrar actividad» and «Actualizar progreso». It only shows confirmed
 * server values: no progression is computed here.
 */
@Composable
fun HomeScreen(view: ProgressView, refresh: () -> Unit, openClasses: (() -> Unit)? = null,
    username: String? = null, onRecord: (() -> Unit)? = null, recordEnabled: Boolean = true) {
    Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            username?.let { Greeting(it) }
            val value = view.value
            // Recording does not depend on the progress read, so the action stays available without it.
            val record: @Composable () -> Unit = {
                if (onRecord != null) PrimaryActionButton(onClick = onRecord, enabled = recordEnabled,
                    modifier = Modifier.testTag("pj-record")) {
                    // The «+» is decorative: screen readers announce «Registrar actividad».
                    Text("+ ", Modifier.clearAndSetSemantics {})
                    Text("Registrar actividad")
                }
            }
            if (value == null) {
                Text(if (view.loading) "Cargando progreso…" else "No se pudo obtener el progreso")
                record()
            } else {
                val name = value.className ?: if (value.classId == "novato") "Novato" else "Clase no disponible"
                val stage = listOf("I", "II", "III").getOrNull(value.stage - 1) ?: "?"
                ClassAppearance.binding(value.classId,value.stage)?.let {
                    Image(painterResource(it.drawable), "$name · Etapa $stage",
                        Modifier.fillMaxWidth().height(220.dp).testTag("character-appearance"),contentScale=ContentScale.Fit)
                } ?: Text("Apariencia no disponible")
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, Modifier.testTag("character-class").semantics { heading() },style=MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Etapa $stage",Modifier.testTag("character-stage"),style=MaterialTheme.typography.titleMedium)
                        Text("Nivel general ${value.generalLevel}",Modifier.testTag("character-level"),style=MaterialTheme.typography.titleMedium)
                    }
                    Text("Progreso del ${value.businessDate}", style = MaterialTheme.typography.labelSmall)
                }
                for (row in pjAttributes.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (id in row) AttributeTile(id, value.attributes.getValue(id), Modifier.weight(1f))
                }
                if (view.stale) Text("Mostrando el último progreso confirmado")
                if (view.loading) Text("Actualizando progreso…")
                record()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(onClick = refresh, enabled = !view.loading, modifier = Modifier.weight(1f)) { Text("Actualizar progreso") }
                    if (openClasses != null) ActionButton(onClick = openClasses) { Text("Clases") }
                }
            }
            if (view.error != null) ActionButton(onClick = refresh) { Text("Reintentar consulta") }
        }
    }
}

/** «Hola,» and the username as separate texts; a long name is cut with an ellipsis and never moves the image. */
@Composable
private fun Greeting(username: String) {
    Row(Modifier.fillMaxWidth().testTag("pj-greeting"), verticalAlignment = Alignment.CenterVertically) {
        Text("Hola, ", style = MaterialTheme.typography.titleLarge)
        Text(username, Modifier.testTag("pj-username"), style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One mosaic tile: code in its attribute color, level, a small bar and the XP. The code is always written (FR-003). */
@Composable
private fun AttributeTile(id: String, attribute: RecordAttributeView, modifier: Modifier) {
    val color = AttributeColors.of(id)
    Column(modifier.clip(MaterialTheme.shapes.medium).background(ArcadeColors.card).padding(10.dp).testTag("attribute-$id"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(id, color = color.text, style = MaterialTheme.typography.titleMedium)
        Text("Nivel ${attribute.level}", Modifier.testTag("attribute-$id-level"), color = ArcadeColors.text,
            style = MaterialTheme.typography.labelMedium)
        // Presentation only: xp and xpRequired are published by the server; at level 50 the bar is full.
        val fraction = attribute.xpRequired?.let { (attribute.xp.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
        Box(Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall).background(ArcadeColors.track)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color.fill))
        }
        Text("${attribute.xp} XP", Modifier.testTag("attribute-$id-xp"), color = ArcadeColors.text,
            style = MaterialTheme.typography.bodySmall)
    }
}
