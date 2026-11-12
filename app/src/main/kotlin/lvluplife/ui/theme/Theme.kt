package lvluplife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** Fixed, auditable palettes; opaque disabled colors retain readable labels. */
@Composable
fun LvlUpTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface) {
            val insets=if(android.os.Build.VERSION.SDK_INT>=35) Modifier.safeDrawingPadding() else Modifier
            Box(Modifier.fillMaxSize().then(insets),propagateMinConstraints=true) { content() }
        }
    }
}
@Composable
fun ActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    Button(onClick,modifier,enabled,colors=colors ?: ButtonDefaults.buttonColors(
        disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,disabledContentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable
fun ActionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick,modifier,enabled,colors=ButtonDefaults.textButtonColors(disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable
fun ActionOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick,modifier,enabled,colors=colors ?: ButtonDefaults.outlinedButtonColors(
        disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,disabledContentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val color=MaterialTheme.colorScheme
    FilterChip(selected,onClick,label,modifier.semantics {
        stateDescription=(if(selected) "Seleccionada" else "No seleccionada")+(if(!enabled) " · No disponible" else "")
    },enabled=enabled,leadingIcon=if(selected) ({ Text("✓") }) else null,
        colors=FilterChipDefaults.filterChipColors(labelColor=color.onSurfaceVariant,selectedLabelColor=color.onSecondaryContainer,
            disabledContainerColor=color.surfaceContainerHighest,
            disabledSelectedContainerColor=color.surfaceContainerHighest,disabledLabelColor=color.onSurface,
            disabledLeadingIconColor=color.onSurface))
}
@Composable
fun InputField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: @Composable (() -> Unit)? = null, singleLine: Boolean = false) {
    val color=MaterialTheme.colorScheme
    OutlinedTextField(value,onValueChange,modifier,enabled=enabled,label=label,singleLine=singleLine,
        colors=OutlinedTextFieldDefaults.colors(disabledTextColor=color.onSurface,disabledLabelColor=color.onSurface,
            disabledPlaceholderColor=color.onSurface,disabledBorderColor=color.onSurface,disabledLeadingIconColor=color.onSurface,
            disabledTrailingIconColor=color.onSurface,disabledSupportingTextColor=color.onSurface))
}
