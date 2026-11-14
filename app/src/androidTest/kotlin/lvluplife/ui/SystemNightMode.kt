package lvluplife.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

/** Simulates the system dark mode for the composition below, as the device setting would. */
@Composable
fun NightSystem(content: @Composable () -> Unit) {
    val night=Configuration(LocalConfiguration.current).apply {
        uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }
    CompositionLocalProvider(LocalConfiguration provides night,content=content)
}
