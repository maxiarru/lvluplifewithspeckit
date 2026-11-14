package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.concurrent.atomic.AtomicInteger
import lvluplife.MainActivity
import lvluplife.application.ProgressView
import lvluplife.application.RecordAttributeView
import lvluplife.application.RecordProgressView
import lvluplife.data.ApiFailureCode
import lvluplife.ui.theme.LvlUpTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Atributos tab (US5, FR-009/FR-010) with synthetic server values: the app never derives xpRequired. */
class AttributesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    // xpRequired values are arbitrary on purpose: the screen must show what the server publishes.
    private val attributes = mapOf(
        "STR" to RecordAttributeView(6, 95, 180), "DEX" to RecordAttributeView(5, 60, 151),
        "CON" to RecordAttributeView(9, 200, 270), "INT" to RecordAttributeView(4, 30, 121),
        "WIT" to RecordAttributeView(49, 1400, 1470), "MEN" to RecordAttributeView(50, 0, null),
    )
    private fun mount(view: ProgressView, refresh: () -> Unit = {}) {
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme { AttributesScreen(view, refresh) } } }
        compose.waitForIdle()
    }
    private fun confirmed() = ProgressView(RecordProgressView(20, "synthetic-cycle", "2026-11-14", 30, "guerrero", 2, attributes),
        loading = false)

    @Test fun sixCardsShowCodeNameLevelAndServerPublishedProgress() {
        mount(confirmed())
        val names = mapOf("STR" to "Fuerza", "DEX" to "Destreza", "CON" to "Constitución", "INT" to "Intelecto", "WIT" to "Ingenio")
        for ((id, name) in names) {
            val value = attributes.getValue(id)
            compose.onNodeWithTag("attributes-$id").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("attributes-$id-name").assertTextEquals("$id · $name")
            compose.onNodeWithTag("attributes-$id-level").assertTextEquals("LVL ${value.level}")
            compose.onNodeWithTag("attributes-$id-xp").assertTextEquals("${value.xp} / ${value.xpRequired} XP al siguiente nivel")
            compose.onNodeWithTag("attributes-$id-bar").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(value.xp.toFloat(), 0f..value.xpRequired!!.toFloat())))
            compose.onNodeWithTag("attributes-$id-max").assertDoesNotExist()
        }
    }

    @Test fun levelFiftyShowsMaximumLevelWithoutABar() {
        mount(confirmed())
        compose.onNodeWithTag("attributes-MEN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("attributes-MEN-name").assertTextEquals("MEN · Descanso")
        compose.onNodeWithTag("attributes-MEN-level").assertTextEquals("LVL 50")
        compose.onNodeWithTag("attributes-MEN-max").assertTextEquals("Nivel máximo")
        compose.onNodeWithTag("attributes-MEN-xp").assertTextEquals("0 XP")
        compose.onNodeWithTag("attributes-MEN-bar").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(5)
    }

    @Test fun loadingErrorAndStaleKeepFeature001Notices() {
        mount(ProgressView(loading = true))
        compose.onNodeWithText("Cargando progreso…").assertIsDisplayed()
        compose.onNodeWithTag("attributes-STR").assertDoesNotExist()
        val retries = AtomicInteger()
        mount(ProgressView(loading = false, error = ApiFailureCode.NETWORK_ERROR)) { retries.incrementAndGet() }
        compose.onNodeWithText("No se pudo obtener el progreso").assertIsDisplayed()
        compose.onNodeWithText("Reintentar consulta").performClick()
        compose.runOnIdle { assertEquals(1, retries.get()) }
        mount(confirmed().copy(stale = true, error = ApiFailureCode.NETWORK_ERROR))
        compose.onNodeWithText("Mostrando el último progreso confirmado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("attributes-STR-xp").performScrollTo().assertTextEquals("95 / 180 XP al siguiente nivel")
    }
}
