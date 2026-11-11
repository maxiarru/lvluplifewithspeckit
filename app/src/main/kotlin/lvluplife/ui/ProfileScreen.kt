package lvluplife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lvluplife.application.AccessController
import lvluplife.application.AccessView

@Composable
fun ProfileScreen(view: AccessView, controller: AccessController) {
    BackHandler { controller.logout() }
    Column(Modifier.padding(24.dp)) {
        Text("Elegí tu nombre de usuario", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = view.username, onValueChange = controller::changeName,
            label = { Text("Nombre de usuario") }, singleLine = true,
            enabled = !view.busy && view.pending == null && !view.unresolvedIntent)
        Text("Entre 1 y 15 letras, números o guiones bajos.")
        view.error?.let { Text(it) }
        if (view.pending != null || view.unresolvedIntent) {
            Text("Confirmación pendiente")
            Button(onClick = controller::retryPending, enabled = !view.busy) { Text("Reintentar") }
        } else Button(onClick = controller::submit, enabled = !view.busy) { Text("Crear personaje") }
        Button(onClick = controller::logout) { Text("Cerrar sesión") }
    }
}
