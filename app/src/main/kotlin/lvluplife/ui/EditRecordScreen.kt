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
fun CorrectionStatus(view:CorrectionView, controller:CorrectionController, online:Boolean) {
    view.message?.let { Text(it) }
    if(view.pending != null || view.unresolved) {
        Text("Confirmación pendiente")
        ActionButton(controller::retry,enabled=!view.busy && online && view.pending?.error !in setOf(ApiFailureCode.AUTH_REQUIRED,ApiFailureCode.FORBIDDEN)) { Text("Reintentar corrección") }
    }
}

@Composable
fun EditRecordScreen(view:CorrectionView,controller:CorrectionController,online:Boolean) {
    val draft=view.draft ?: return
    val keyboard=LocalSoftwareKeyboardController.current
    val locked=view.busy || view.pending != null || view.unresolved
    BackHandler(!view.busy && !view.unresolved) { controller.close() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Editar registro",style=MaterialTheme.typography.titleLarge)
        if(draft.hours == null) Text("Comida saludable")
        if(!online) Text("Sin conexión")
        if(draft.hours != null) ActivityPicker(view.options,draft.activityId,!locked) { controller.change(draft.copy(activityId=it)) }
        InputField(draft.date,{ controller.change(draft.copy(date=it)) },label={ Text("Fecha") },singleLine=true,enabled=!locked,modifier=Modifier.fillMaxWidth())
        if(draft.hours != null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            InputField(draft.hours,{ controller.change(draft.copy(hours=it)) },label={ Text("Horas") },singleLine=true,enabled=!locked,modifier=Modifier.weight(1f))
            InputField(draft.minutes ?: "",{ controller.change(draft.copy(minutes=it)) },label={ Text("Minutos") },singleLine=true,enabled=!locked,modifier=Modifier.weight(1f))
        }
        ActionButton({ keyboard?.hide();controller.submit() },enabled=!locked && online) { Text("Guardar cambios") }
        ActionTextButton(controller::close,enabled=!view.busy && !view.unresolved) { Text("Volver al historial") }
        CorrectionStatus(view,controller,online)
        if(view.busy) Text("Procesando corrección…")
    }
}
