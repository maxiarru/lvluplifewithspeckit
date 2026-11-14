package lvluplife.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lvluplife.application.ProgressView
import lvluplife.application.RecordAttributeView
import lvluplife.ui.theme.*

/** Order and display names of the attributes, as in the «Arcade vivo» mockup. */
internal val attributeNames = linkedMapOf(
    "STR" to "Fuerza", "DEX" to "Destreza", "CON" to "Constitución",
    "INT" to "Intelecto", "WIT" to "Ingenio", "MEN" to "Descanso",
)

/**
 * Atributos tab (US5, FR-009/FR-010): one card per attribute with code, name, level and XP. The bar and its
 * «X / Y XP al siguiente nivel» text use the xpRequired the server publishes; at level 50 it shows «Nivel máximo».
 */
@Composable
fun AttributesScreen(view: ProgressView, refresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tus atributos", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        val value = view.value
        if (value == null) {
            Text(if (view.loading) "Cargando progreso…" else "No se pudo obtener el progreso")
        } else {
            for ((id, name) in attributeNames) AttributeCard(id, name, value.attributes.getValue(id))
            if (view.stale) Text("Mostrando el último progreso confirmado")
            if (view.loading) Text("Actualizando progreso…")
        }
        if (view.error != null) ActionButton(onClick = refresh, enabled = !view.loading) { Text("Reintentar consulta") }
    }
}

@Composable
private fun AttributeCard(id: String, name: String, attribute: RecordAttributeView) {
    val color = AttributeColors.of(id)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ArcadeColors.card).padding(14.dp)
        .testTag("attributes-$id"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$id · $name", Modifier.weight(1f).testTag("attributes-$id-name"), color = color.text,
                style = MaterialTheme.typography.titleMedium)
            Text("LVL ${attribute.level}", Modifier.testTag("attributes-$id-level"), color = ArcadeColors.text,
                style = MaterialTheme.typography.titleLarge)
        }
        val required = attribute.xpRequired
        if (required == null) {
            // RF-24/RF-25: level 50 has no next level, so there is no progress bar.
            Text("Nivel máximo", Modifier.testTag("attributes-$id-max"), color = ArcadeColors.text,
                style = MaterialTheme.typography.titleSmall)
            Text("${attribute.xp} XP", Modifier.testTag("attributes-$id-xp"), color = ArcadeColors.muted,
                style = MaterialTheme.typography.bodyMedium)
        } else {
            // Presentation only: both values come from the server; the bar never carries the information alone.
            val fraction = (attribute.xp.toFloat() / required).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(ArcadeColors.track)
                .testTag("attributes-$id-bar")
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(attribute.xp.toFloat(), 0f..required.toFloat()) }) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color.fill))
            }
            Text("${attribute.xp} / $required XP al siguiente nivel", Modifier.testTag("attributes-$id-xp"),
                color = ArcadeColors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
