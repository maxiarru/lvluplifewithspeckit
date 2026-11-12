package lvluplife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import lvluplife.data.HistoryPeriod

@Composable
fun HistoryScreen(view: HistoryView, controller: HistoryController, online: Boolean,
    corrections: CorrectionController? = null, correctionView: CorrectionView? = null, footer: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((period,label) in listOf(HistoryPeriod.day to "Día",HistoryPeriod.week to "Semana",HistoryPeriod.month to "Mes"))
                FilterChip(view.selection.period == period,{ controller.period(period) },{ Text(label) })
        }
        OutlinedTextField(view.dateInput,controller::date,label={ Text("Fecha del período") },singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(controller::consult) { Text("Consultar período") }
        if (!online) Text("Sin conexión")
        if (view.loading) Text(if(view.page == null) "Cargando historial…" else "Actualizando historial…")
        if (view.stale) Text("Mostrando el último historial confirmado")
        if (view.error != null) {
            Text(when(view.error) {
                ApiFailureCode.INVALID_INPUT -> "Revisá la fecha del período"
                ApiFailureCode.AUTH_REQUIRED -> "Volvé a iniciar sesión para consultar el historial"
                else -> "No se pudo cargar el historial"
            })
            TextButton(controller::refresh,enabled=!view.loading) { Text("Reintentar historial") }
        }
        view.page?.let { page ->
            Text("Desde ${page.periodStart} hasta ${page.periodEndExclusive} (sin incluir)",style=MaterialTheme.typography.labelSmall)
            if(page.items.isEmpty()) Text("No hay registros en este período")
            for(item in page.items) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text(item.name,style=MaterialTheme.typography.titleMedium)
                    Text(item.date)
                    Text(item.durationMinutes?.let { "$it minutos" } ?: "Realizada")
                    if(item.canCorrect && corrections != null && correctionView != null) Row {
                        val unlocked = !correctionView.busy && correctionView.pending == null && !correctionView.unresolved
                        TextButton({ corrections.open(item) },enabled=unlocked,modifier=Modifier.testTag("history-edit-${item.id}")) { Text("Editar") }
                        TextButton({ corrections.askDelete(item) },enabled=unlocked && online,modifier=Modifier.testTag("history-delete-${item.id}")) { Text("Eliminar") }
                    }
                }
            }
            TextButton(controller::refresh,enabled=!view.loading) { Text("Actualizar historial") }
        }
        footer()
    }
}
