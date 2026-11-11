package lvluplife.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AccessScreen(busy: Boolean, error: String?, onSignIn: () -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text("Lvl Up Life", style = MaterialTheme.typography.headlineMedium)
        Text("Iniciá sesión para continuar")
        error?.let { Text(it) }
        Button(onClick = onSignIn, enabled = !busy) { Text("Continuar con Google") }
    }
}
