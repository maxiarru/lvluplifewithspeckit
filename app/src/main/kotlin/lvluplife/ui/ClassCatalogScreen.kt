package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import lvluplife.application.ClassCatalogView

@Composable
fun ClassCatalogScreen(view: ClassCatalogView, generalLevel: Int, staleLevel: Boolean,
    refresh: () -> Unit, close: () -> Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Clases y etapas",style=MaterialTheme.typography.titleLarge)
            Text("Nivel general $generalLevel")
            ActionButton(onClick=close) { Text("Volver a principal") }
            if(staleLevel) Text("Usando el último nivel confirmado")
            val entries=view.entries
            val readable=entries!=null && entries.size==26 && entries.map { it.id }.toSet()==ClassAppearance.all.map { it.classId }.toSet() &&
                entries.sumOf { it.images.size }==76 && entries.all { entry -> entry.images.all {
                    ClassAppearance.binding(entry.id,it.stage)?.resourceId==it.resourceId
                } && entry.images.map { it.stage }==ClassAppearance.all.filter { it.classId==entry.id }.map { it.stage } }
            if(view.loading) Text("Cargando clases…")
            if(view.stale) Text("Mostrando el último catálogo confirmado")
            if(view.error!=null || (!view.loading && !readable)) {
                Text("No pudimos actualizar las clases")
                ActionButton(onClick=refresh,enabled=!view.loading) { Text("Reintentar clases") }
            }
            if(readable) {
                val rows=entries!!.flatMap { entry -> entry.images.map { entry to it } }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("class-catalog"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(rows,key={ (entry,image) -> "${entry.id}-${image.stage}" }) { (entry,image) ->
                        val unlocked=generalLevel>=image.minimumLevel
                        val status=if(unlocked) "Desbloqueada" else "Bloqueada"
                        val roman=listOf("I","II","III")[image.stage-1]
                        val tag="class-${entry.id}-stage-${image.stage}"
                        Card(Modifier.fillMaxWidth().testTag(tag).semantics { stateDescription=status }) {
                            Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Image(painterResource(ClassAppearance.binding(entry.id,image.stage)!!.drawable),
                                    "${entry.name} · Etapa $roman",Modifier.size(160.dp).testTag("$tag-image"),contentScale=ContentScale.Fit)
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                    Text("Etapa $roman")
                                    Text(status,Modifier.testTag("$tag-status"))
                                    if(!unlocked) Text("Desde nivel ${image.minimumLevel}",Modifier.testTag("$tag-minimum"),style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
