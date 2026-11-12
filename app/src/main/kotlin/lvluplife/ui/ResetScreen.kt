package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import lvluplife.application.*

@Composable
fun ResetScreen(view: ResetView, controller: ResetController, cycleId: String?, online: Boolean) {
    val locked = view.busy || view.pending != null || view.unresolved
    Column {
        ActionOutlinedButton(onClick={ controller.open(cycleId) },enabled=view.ready && !locked && online && cycleId != null,
            colors=ButtonDefaults.outlinedButtonColors(disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor=MaterialTheme.colorScheme.onSurface),
            modifier=Modifier.testTag("reset-open")) { Text("Reiniciar progresión") }
        if (view.pending != null || view.unresolved) {
            Text("Confirmación pendiente")
            ActionButton(onClick=controller::retry,enabled=!view.busy && online && view.error !in ResetController.suspended,
                colors=ButtonDefaults.buttonColors(disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor=MaterialTheme.colorScheme.onSurface),
                modifier=Modifier.testTag("reset-retry")) { Text("Reintentar reinicio") }
        }
        view.message?.let { Text(it) }
        if(view.busy && view.ready) Text("Procesando reinicio…")
        if (locked && !online) Text("Sin conexión")
    }
    if (view.dialogCycle != null) AlertDialog(onDismissRequest=controller::cancel,
        title={ Text("¿Reiniciar tu personaje?") },
        text={ Text("Se reinicia tu progreso. Se conservan tu cuenta, nombre de usuario, actividades personalizadas e historial.") },
        confirmButton={ ActionButton(onClick=controller::confirm,enabled=!view.busy && online,
            modifier=Modifier.testTag("reset-confirm")) { Text("Reiniciar") } },
        dismissButton={ ActionTextButton(onClick=controller::cancel) { Text("Cancelar") } })
}
