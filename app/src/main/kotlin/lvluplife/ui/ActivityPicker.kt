package lvluplife.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import lvluplife.application.RecordOption

@Composable
fun ActivityPicker(options: List<RecordOption>, selected: String, enabled: Boolean, select: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(options, key = { it.id }) { option ->
            FilterChip(selected == option.id, { select(option.id) }, { Text(option.name) }, enabled = enabled)
        }
    }
}
