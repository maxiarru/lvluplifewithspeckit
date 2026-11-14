package lvluplife.ui

import lvluplife.ui.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import lvluplife.application.RecordOption

/** Vertical list of activities; each one shows its attribute codes next to the name (text, not only color). */
@Composable
fun ActivityPicker(options: List<RecordOption>, selected: String, enabled: Boolean, select: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (option in options) {
            ChoiceChip(selected == option.id, { select(option.id) }, {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(option.name, Modifier.weight(1f, fill = false))
                    listOfNotNull(option.primary, option.secondary).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
                }
            }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("activity-${option.id}"))
        }
    }
}
