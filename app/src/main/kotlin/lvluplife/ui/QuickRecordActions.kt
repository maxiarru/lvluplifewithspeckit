package lvluplife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset
import lvluplife.application.*
import lvluplife.ui.theme.*

/** Three explicit gestures for a duration; no XP, implicit submit or remembered duration. */
@Composable
fun QuickRecordActions(view: RecordFormView, controller: RecordFormController, enabled: Boolean) {
    var activity by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf<Int?>(null) }
    var durationInput by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    val unlocked=enabled && !view.busy && view.pending == null && !view.unresolved
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        for((id,label) in listOf("meal" to "Comida","strength" to "Entrenar","sleep" to "Dormir")) {
            ActionButton(onClick={ activity=id;duration=null;durationInput="";date=LocalDate.now(ZoneOffset.ofHours(-3)).toString() },
                enabled=unlocked && view.options.any { it.id==id },modifier=Modifier.weight(1f).testTag("quick-$id")) { Text(label) }
        }
    }
    activity?.let { id ->
        val meal=id=="meal"
        val focus=remember(id) { FocusRequester() }
        AlertDialog(onDismissRequest={ activity=null },title={ Text(when(id) {
            "meal" -> "Registrar comida saludable"
            "sleep" -> "Registrar sueño"
            else -> "Registrar entrenamiento"
        }) },text={ Column {
            Text("Hoy · $date")
            if(meal) Text("Una comida saludable")
            else {
                InputField(durationInput,{ value ->
                    durationInput=value
                    duration=Regex("([0-9]{1,2}):([0-5][0-9])").matchEntire(value)?.let { match ->
                        val total=match.groupValues[1].toInt()*60+match.groupValues[2].toInt()
                        total.takeIf { it in 1..1440 }
                    }
                },label={ Text("Duración (h:mm)") },singleLine=true,enabled=unlocked,
                    modifier=Modifier.focusRequester(focus).testTag("quick-duration-input"))
                LaunchedEffect(focus) { focus.requestFocus() }
                if(durationInput.isNotEmpty() && duration==null) Text("Ingresá una duración de 0:01 a 24:00")
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    for(minutes in if(id=="sleep") listOf(360,420,480) else listOf(30,60,90))
                        ChoiceChip(duration==minutes,{ duration=minutes;durationInput="${minutes/60}:${(minutes%60).toString().padStart(2,'0')}" },
                            { Text(if(minutes>=60 && minutes%60==0) "${minutes/60} h" else "$minutes min") },
                            modifier=Modifier.testTag("quick-duration-$minutes"),enabled=unlocked)
                }
            }
        } },confirmButton={ ActionButton(onClick={
            controller.quick(id,if(meal) null else duration,date)
            activity=null
        },enabled=unlocked && (meal || duration!=null),modifier=Modifier.testTag("quick-save")) { Text("Guardar") } },
            dismissButton={ ActionTextButton({ activity=null }) { Text("Cancelar") } })
    }
}
