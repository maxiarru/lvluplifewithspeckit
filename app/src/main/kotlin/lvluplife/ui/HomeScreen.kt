package lvluplife.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import lvluplife.application.ProgressView

@Composable
fun HomeScreen(view: ProgressView, refresh: () -> Unit, openClasses: (() -> Unit)? = null) {
    Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val value = view.value
            if (value == null) {
                Text(if (view.loading) "Cargando progreso…" else "No se pudo obtener el progreso")
            } else {
                val name = value.className ?: if (value.classId == "novato") "Novato" else "Clase no disponible"
                val stage = listOf("I", "II", "III").getOrNull(value.stage - 1) ?: "?"
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    ClassAppearance.binding(value.classId,value.stage)?.let {
                        Image(painterResource(it.drawable), "$name · Etapa $stage",
                            Modifier.size(88.dp).testTag("character-appearance"),contentScale=ContentScale.Fit)
                    } ?: Text("Apariencia no disponible")
                    Column(Modifier.weight(1f)) {
                        Text(name, Modifier.testTag("character-class"),style=MaterialTheme.typography.titleMedium)
                        Text("Etapa $stage",Modifier.testTag("character-stage"))
                        Text("Nivel general ${value.generalLevel}",Modifier.testTag("character-level"))
                    }
                }
                Text("Progreso del ${value.businessDate}", style = MaterialTheme.typography.labelSmall)
                for (pair in listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").chunked(2)) Row(Modifier.fillMaxWidth()) {
                    for (id in pair) Column(Modifier.weight(1f)) {
                        val attribute = value.attributes.getValue(id)
                        Text("$id · Nivel ${attribute.level}", style = MaterialTheme.typography.labelMedium)
                        Text("${attribute.xp} XP", Modifier.testTag("attribute-$id-xp"))
                    }
                }
                if (view.stale) Text("Mostrando el último progreso confirmado")
                if (view.loading) Text("Actualizando progreso…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = refresh, enabled = !view.loading,colors=ButtonDefaults.buttonColors(
                        disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor=MaterialTheme.colorScheme.onSurface)) { Text("Actualizar progreso") }
                    if (openClasses != null) Button(onClick = openClasses) { Text("Clases") }
                }
            }
            if (view.error != null) Button(onClick = refresh) { Text("Reintentar consulta") }
        }
    }
}
