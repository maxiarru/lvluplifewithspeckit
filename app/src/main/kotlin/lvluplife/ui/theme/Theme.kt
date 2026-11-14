package lvluplife.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lvluplife.R

/** «Arcade vivo» palette (feature 002, FR-001). Values change only to keep FR-003 contrast. */
object ArcadeColors {
    val background = Color(0xFFEAF1FC)
    val card = Color(0xFFFFFFFF)
    val text = Color(0xFF14213D)
    val muted = Color(0xFF5A6785)
    val primary = Color(0xFF2F5BD3)
    val onPrimary = Color(0xFFFFFFFF)
    val accent = Color(0xFFFFC531)
    val track = Color(0xFFD6E0F3)
    val nav = Color(0xFFD9E4F8)
}

/** Bundled variable fonts (FR-002): Fredoka for titles, numbers and buttons; Nunito for text. */
object LvlUpFonts {
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun weighted(resource: Int, vararg weights: FontWeight) = FontFamily(weights.map {
        Font(resource, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    })
    val display = weighted(R.font.fredoka, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
    val text = weighted(R.font.nunito, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
}

private val arcadeScheme = lightColorScheme(
    primary = ArcadeColors.primary, onPrimary = ArcadeColors.onPrimary,
    primaryContainer = ArcadeColors.nav, onPrimaryContainer = ArcadeColors.text,
    secondary = ArcadeColors.primary, onSecondary = ArcadeColors.onPrimary,
    secondaryContainer = ArcadeColors.accent, onSecondaryContainer = ArcadeColors.text,
    tertiary = ArcadeColors.accent, onTertiary = ArcadeColors.text,
    tertiaryContainer = ArcadeColors.accent, onTertiaryContainer = ArcadeColors.text,
    background = ArcadeColors.background, onBackground = ArcadeColors.text,
    surface = ArcadeColors.background, onSurface = ArcadeColors.text,
    surfaceVariant = ArcadeColors.track, onSurfaceVariant = ArcadeColors.muted,
    surfaceTint = ArcadeColors.primary,
    surfaceBright = ArcadeColors.card, surfaceDim = ArcadeColors.background,
    surfaceContainerLowest = ArcadeColors.card, surfaceContainerLow = ArcadeColors.card,
    surfaceContainer = ArcadeColors.card, surfaceContainerHigh = ArcadeColors.card,
    surfaceContainerHighest = ArcadeColors.card,
    outline = ArcadeColors.muted, outlineVariant = ArcadeColors.track,
)

private val arcadeTypography = Typography().run {
    fun TextStyle.display(weight: FontWeight) = copy(fontFamily = LvlUpFonts.display, fontWeight = weight)
    fun TextStyle.body() = copy(fontFamily = LvlUpFonts.text)
    Typography(
        displayLarge = displayLarge.display(FontWeight.Bold), displayMedium = displayMedium.display(FontWeight.Bold),
        displaySmall = displaySmall.display(FontWeight.Bold),
        headlineLarge = headlineLarge.display(FontWeight.Bold), headlineMedium = headlineMedium.display(FontWeight.Bold),
        headlineSmall = headlineSmall.display(FontWeight.SemiBold),
        titleLarge = titleLarge.display(FontWeight.SemiBold), titleMedium = titleMedium.display(FontWeight.SemiBold),
        titleSmall = titleSmall.display(FontWeight.SemiBold),
        bodyLarge = bodyLarge.body(), bodyMedium = bodyMedium.body(), bodySmall = bodySmall.body(),
        labelLarge = labelLarge.display(FontWeight.SemiBold), labelMedium = labelMedium.display(FontWeight.Medium),
        labelSmall = labelSmall.display(FontWeight.Medium),
    )
}

/** Rounded shapes: 14 dp for buttons and chips, 16 dp for cards. */
private val arcadeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)
private val buttonShape = RoundedCornerShape(14.dp)

/** Always the light «Arcade vivo» scheme, even when the system is in dark mode (FR-004). */
@Composable
fun LvlUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=arcadeScheme,typography=arcadeTypography,shapes=arcadeShapes) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface) {
            val insets=if(android.os.Build.VERSION.SDK_INT>=35) Modifier.safeDrawingPadding() else Modifier
            Box(Modifier.fillMaxSize().then(insets),propagateMinConstraints=true) { content() }
        }
    }
}
@Composable
fun ActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    Button(onClick,modifier,enabled,shape=buttonShape,colors=colors ?: ButtonDefaults.buttonColors(
        disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,disabledContentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable
fun ActionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick,modifier,enabled,shape=buttonShape,colors=ButtonDefaults.textButtonColors(disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable
fun ActionOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick,modifier,enabled,shape=buttonShape,colors=colors ?: ButtonDefaults.outlinedButtonColors(
        disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerHighest,disabledContentColor=MaterialTheme.colorScheme.onSurface),content=content)
}
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val color=MaterialTheme.colorScheme
    FilterChip(selected,onClick,label,modifier.semantics {
        stateDescription=(if(selected) "Seleccionada" else "No seleccionada")+(if(!enabled) " · No disponible" else "")
    },enabled=enabled,leadingIcon=if(selected) ({ Text("✓") }) else null,shape=buttonShape,
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
