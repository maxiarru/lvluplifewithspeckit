package lvluplife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import lvluplife.application.*
import lvluplife.data.ApiFailureCode

@Composable
fun CustomActivityScreen(view: CustomActivityView, controller: CustomActivityController, online: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val locked = view.busy || view.pending != null || view.unresolved
    BackHandler(view.open && !view.busy) { controller.close() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (view.open) {
            Text("Crear actividad personalizada", style = MaterialTheme.typography.titleLarge)
            if (!online) Text("Sin conexión")
            OutlinedTextField(view.draft.name, { controller.change(view.draft.copy(name = it)) }, label = { Text("Nombre") },
                singleLine = true, enabled = !locked, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(view.draft.category, { controller.change(view.draft.copy(category = it)) }, label = { Text("Categoría") },
                singleLine = true, enabled = !locked, modifier = Modifier.fillMaxWidth())
            Text("Atributo principal")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (id in listOf("STR", "DEX", "INT", "WIT", "MEN")) FilterChip(view.draft.primary == id,
                    { controller.change(view.draft.copy(primary = id, secondary = view.draft.secondary.takeUnless { it == id })) },
                    { Text(id) }, enabled = !locked, modifier = Modifier.testTag("custom-primary-$id"))
            }
            Text("Atributo secundario (opcional)")
            FilterChip(view.draft.secondary == null, { controller.change(view.draft.copy(secondary = null)) }, { Text("Sin secundario") }, enabled = !locked)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (id in listOf("STR", "DEX", "INT", "WIT", "MEN")) FilterChip(view.draft.secondary == id,
                    { controller.change(view.draft.copy(secondary = id)) }, { Text(id) }, enabled = !locked && id != view.draft.primary,
                    modifier = Modifier.testTag("custom-secondary-$id"))
            }
            Button({ keyboard?.hide(); controller.submit() }, enabled = !locked && online) { Text("Guardar definición") }
            TextButton(controller::close, enabled = !view.busy) { Text("Volver") }
            if (view.listError) {
                Text("No se pudieron cargar tus actividades")
                TextButton(controller::refreshList) { Text("Reintentar actividades propias") }
            } else if (!view.listLoaded) Text("Cargando actividades propias…")
            else if (view.items.isEmpty()) Text("Todavía no tenés actividades propias")
            else Text("${view.items.size} actividades propias disponibles para registrar")
        } else if (!view.busy) Button(controller::open) { Text("Crear actividad personalizada") }
        else Text("Cargando actividades propias…")
        view.message?.let { Text(it) }
        if (view.pending != null || view.unresolved) {
            Text("Confirmación pendiente")
            Button(controller::retry, enabled = !view.busy && online && view.pending?.error !in setOf(ApiFailureCode.AUTH_REQUIRED, ApiFailureCode.FORBIDDEN)) {
                Text("Reintentar definición")
            }
        }
    }
}
