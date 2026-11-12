package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import lvluplife.application.*
import lvluplife.data.ApiFailureCode

@Composable
fun RecordScreen(view: RecordFormView, controller: RecordFormController, online: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val locked = view.busy || view.pending != null || view.unresolved
    BackHandler(enabled = view.open && !view.busy) { controller.close() }
    Column(Modifier.fillMaxWidth().then(if(view.open) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!online) Text("Sin conexión")
        if (view.open) {
            Text("Registrar actividad", style = MaterialTheme.typography.titleLarge)
            ActivityPicker(view.options, view.draft.activityId, !locked) { controller.change(view.draft.copy(activityId = it)) }
            InputField(value = view.draft.date, onValueChange = { controller.change(view.draft.copy(date = it)) },
                label = { Text("Fecha") }, singleLine = true, enabled = !locked, modifier = Modifier.fillMaxWidth())
            if (view.options.firstOrNull { it.id == view.draft.activityId }?.duration == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(value = view.draft.hours, onValueChange = { controller.change(view.draft.copy(hours = it)) },
                    label = { Text("Horas") }, singleLine = true, enabled = !locked, modifier = Modifier.weight(1f))
                InputField(value = view.draft.minutes, onValueChange = { controller.change(view.draft.copy(minutes = it)) },
                    label = { Text("Minutos") }, singleLine = true, enabled = !locked, modifier = Modifier.weight(1f))
            }
            ActionButton(onClick = { keyboard?.hide(); controller.submit() }, enabled = !locked && online) { Text("Guardar actividad") }
            ActionTextButton(onClick = controller::close, enabled = !view.busy) { Text("Volver") }
        } else if (view.options.isNotEmpty()) ActionButton(onClick = controller::open, enabled = !locked) { Text("Registrar actividad") }
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
