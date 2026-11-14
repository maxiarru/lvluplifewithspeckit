package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import lvluplife.data.HistoryPeriod

/** Historial tab with the «Arcade vivo» style (FR-013): same filters, edit and delete rules as feature 001. */
@Composable
fun HistoryScreen(view: HistoryView, controller: HistoryController, online: Boolean,
    corrections: CorrectionController? = null, correctionView: CorrectionView? = null, footer: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tu historial", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((period,label) in listOf(HistoryPeriod.day to "Día",HistoryPeriod.week to "Semana",HistoryPeriod.month to "Mes"))
                ChoiceChip(view.selection.period == period,{ controller.period(period) },{ Text(label) },modifier=Modifier.weight(1f))
        }
        InputField(view.dateInput,controller::date,label={ Text("Fecha del período") },singleLine=true,modifier=Modifier.fillMaxWidth())
        ActionButton(controller::consult,Modifier.fillMaxWidth()) { Text("Consultar período") }
        if (!online) Text("Sin conexión")
        if (view.loading) Text(if(view.page == null) "Cargando historial…" else "Actualizando historial…")
        if (view.stale) Text("Mostrando el último historial confirmado")
        if (view.error != null) {
            Text(when(view.error) {
                ApiFailureCode.INVALID_INPUT -> "Revisá la fecha del período"
                ApiFailureCode.AUTH_REQUIRED -> "Volvé a iniciar sesión para consultar el historial"
                else -> "No se pudo cargar el historial"
            })
            ActionTextButton(controller::refresh,enabled=!view.loading) { Text("Reintentar historial") }
        }
        view.page?.let { page ->
            Text("Desde ${page.periodStart} hasta ${page.periodEndExclusive} (sin incluir)",style=MaterialTheme.typography.labelSmall)
            if(page.items.isEmpty()) Text("No hay registros en este período")
            for(item in page.items) Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ArcadeColors.card)
                .padding(12.dp).testTag("history-item-${item.id}"),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(item.name,Modifier.weight(1f),color=ArcadeColors.text,style=MaterialTheme.typography.titleMedium)
                    Text(item.durationMinutes?.let { "$it minutos" } ?: "Realizada",color=ArcadeColors.text,
                        style=MaterialTheme.typography.labelLarge)
                }
                Text(item.date,color=ArcadeColors.muted,style=MaterialTheme.typography.bodyMedium)
                if(item.canCorrect && corrections != null && correctionView != null) Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    val unlocked = !correctionView.busy && correctionView.pending == null && !correctionView.unresolved
                    ActionTextButton({ corrections.open(item) },enabled=unlocked,modifier=Modifier.testTag("history-edit-${item.id}")) { Text("Editar") }
                    ActionTextButton({ corrections.askDelete(item) },enabled=unlocked && online,modifier=Modifier.testTag("history-delete-${item.id}")) { Text("Eliminar") }
                }
            }
            ActionTextButton(controller::refresh,enabled=!view.loading) { Text("Actualizar historial") }
        }
        footer()
    }
}
