package lvluplife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset
import lvluplife.application.*
import lvluplife.ui.theme.*

/** Duration the quick «Dormir» button records (FR-015). Info explains it; other durations go through Registrar. */
internal const val QUICK_SLEEP_MINUTES = 7 * 60

/**
 * Quick record (FR-015): one tap records, for today, a healthy meal or 7 hours of sleep. Only the activity and
 * the duration are sent; the server assigns XP. The form draft of «+ Registrar actividad» is not touched (FR-016).
 */
@Composable
fun QuickRecordActions(view: RecordFormView, controller: RecordFormController, enabled: Boolean) {
    val unlocked = enabled && !view.busy && view.pending == null && !view.unresolved
    fun today() = LocalDate.now(ZoneOffset.ofHours(-3)).toString()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Registro rápido", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(onClick = { controller.quick("meal", null, today()) },
                enabled = unlocked && view.options.any { it.id == "meal" }, modifier = Modifier.weight(1f).testTag("quick-meal")) {
                Text("Comida")
            }
            ActionButton(onClick = { controller.quick("sleep", QUICK_SLEEP_MINUTES, today()) },
                enabled = unlocked && view.options.any { it.id == "sleep" }, modifier = Modifier.weight(1f).testTag("quick-sleep")) {
                Text("Dormir")
                Text(" · 7 h")
            }
        }
    }
}
