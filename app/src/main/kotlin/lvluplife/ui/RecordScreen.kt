package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset
import lvluplife.application.*
import lvluplife.data.ApiFailureCode

/** Hoy, Ayer and Anteayer in the business time zone, as ISO dates. The server still validates the window. */
internal fun recordDates(today: LocalDate): List<Pair<String, String>> =
    listOf("Hoy", "Ayer", "Anteayer").mapIndexed { offset, label -> label to today.minusDays(offset.toLong()).toString() }

/** Category, mode and preassigned attributes of the chosen activity (AC-06). Read only: nothing here changes XP. */
@Composable
private fun ActivityDetails(option: RecordOption) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ArcadeColors.card).padding(12.dp)
        .testTag("record-details"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        option.category?.let { Text("Categoría: $it", Modifier.testTag("record-detail-category"), color = ArcadeColors.text) }
        Text("Modalidad: " + if (option.duration) "por duración" else "por ocurrencia", Modifier.testTag("record-detail-mode"),
            color = ArcadeColors.text)
        option.primary?.let { primary ->
            Row(Modifier.testTag("record-detail-attributes"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Atributos:", color = ArcadeColors.text)
                Text("$primary (principal)", color = AttributeColors.of(primary).text)
                option.secondary?.let { Text("· $it (secundario)", color = AttributeColors.of(it).text) }
            }
        }
    }
}

@Composable
fun RecordScreen(view: RecordFormView, controller: RecordFormController, online: Boolean, showOpenButton: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    val locked = view.busy || view.pending != null || view.unresolved
    BackHandler(enabled = view.open && !view.busy) { controller.close() }
    Column(Modifier.fillMaxWidth().then(if(view.open) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!online) Text("Sin conexión")
        if (view.open) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Registrar actividad", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                ActionTextButton(onClick = controller::close, enabled = !view.busy) { Text("Volver") }
            }
            Text("Fecha", style = MaterialTheme.typography.labelLarge)
            val today = LocalDate.now(ZoneOffset.ofHours(-3))
            val dates = recordDates(today)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dates.forEachIndexed { offset, (label, date) ->
                    ChoiceChip(view.draft.date == date, { controller.change(view.draft.copy(date = date)) }, { Text(label) },
                        enabled = !locked, modifier = Modifier.weight(1f).testTag("record-date-$offset"))
                }
            }
            // A restored draft may carry an older date: it is shown, never silently replaced.
            if (dates.none { it.second == view.draft.date }) Text("Fecha del borrador: ${view.draft.date}. Elegí Hoy, Ayer o Anteayer.")
            Text("Actividad", style = MaterialTheme.typography.labelLarge)
            ActivityPicker(view.options, view.draft.activityId, !locked) { controller.change(view.draft.copy(activityId = it)) }
            view.options.firstOrNull { it.id == view.draft.activityId }?.let { ActivityDetails(it) }
            if (view.options.firstOrNull { it.id == view.draft.activityId }?.duration == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(value = view.draft.hours, onValueChange = { controller.change(view.draft.copy(hours = it)) },
                    label = { Text("Horas") }, singleLine = true, enabled = !locked, modifier = Modifier.weight(1f))
                InputField(value = view.draft.minutes, onValueChange = { controller.change(view.draft.copy(minutes = it)) },
                    label = { Text("Minutos") }, singleLine = true, enabled = !locked, modifier = Modifier.weight(1f))
            }
            PrimaryActionButton(onClick = { keyboard?.hide(); controller.submit() }, enabled = !locked && online,
                modifier = Modifier.testTag("record-save")) { Text("Guardar actividad") }
        } else if (view.options.isNotEmpty()) {
            // Inside PJ the «+ Registrar actividad» button belongs to HomeScreen.
            if (showOpenButton) ActionButton(onClick = controller::open, enabled = !locked) { Text("Registrar actividad") }
        }
        else if (view.busy) Text("Cargando actividades…")
        else ActionTextButton(onClick = controller::start) { Text("Reintentar actividades") }
        view.message?.let { Text(it) }
        if(view.busy && view.options.isNotEmpty()) Text("Procesando actividad…")
        if (view.pending != null || view.unresolved) {
            Text("Confirmación pendiente")
            ActionButton(onClick = controller::retry, enabled = !view.busy && online &&
                view.pending?.error !in setOf(ApiFailureCode.AUTH_REQUIRED, ApiFailureCode.FORBIDDEN)) { Text("Reintentar operación") }
        }
    }
}
