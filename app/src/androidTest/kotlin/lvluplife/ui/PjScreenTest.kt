package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** PJ tab (US3, FR-007) with synthetic confirmed progress: no network, Firebase or XP calculation. */
class PjScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ids = listOf("STR", "DEX", "INT", "WIT", "MEN", "CON")
    private fun progress(classId: String = "guerrero", className: String? = "Guerrero", stage: Int = 2, level: Int = 12) =
        RecordProgressView(20, "synthetic-cycle", "2026-11-14", level, classId, stage,
            ids.mapIndexed { index, id -> id to RecordAttributeView(index + 3, index * 10, 30 * (index + 3)) }.toMap(),
            className = className)

    private fun mount(content: @Composable () -> Unit) {
        // Same scroll container as RecordFlowContent, which hosts PJ inside the tab.
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) { content() }
        } } }
        compose.waitForIdle()
    }

    @Test fun confirmedProgressShowsGreetingCutoutClassStageLevelSixAttributesAndActions() {
        val records = AtomicInteger(); val refreshes = AtomicInteger()
        mount { HomeScreen(ProgressView(progress(), loading = false), { refreshes.incrementAndGet() }, "Ana",
            onRecord = { records.incrementAndGet() }) }
        compose.onNodeWithTag("pj-username").assertTextEquals("Ana").assertIsDisplayed()
        compose.onNodeWithText("Hola,", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("character-appearance").assertContentDescriptionEquals("Guerrero · Etapa II").assertIsDisplayed()
        compose.onNodeWithTag("character-class").assertTextEquals("Guerrero")
        compose.onNodeWithTag("character-stage").assertTextEquals("Etapa II")
        compose.onNodeWithTag("character-level").assertTextEquals("Nivel general 12")
        ids.forEachIndexed { index, id ->
            compose.onNodeWithTag("attribute-$id").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("attribute-$id-level", useUnmergedTree = true).assertTextEquals("Nivel ${index + 3}")
            compose.onNodeWithTag("attribute-$id-xp", useUnmergedTree = true).assertTextEquals("${index * 10} XP")
            compose.onNode(hasText(id) and hasAnyAncestor(hasTestTag("attribute-$id")), useUnmergedTree = true).assertExists()
        }
        // The «+» is decorative: the action is announced as «Registrar actividad».
        compose.onNodeWithTag("pj-record").performScrollTo().assertTextEquals("Registrar actividad").performClick()
        compose.onNodeWithText("Actualizar progreso").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, records.get()); assertEquals(1, refreshes.get()) }
    }

    @Test fun novatoShowsStageOneCutout() {
        mount { HomeScreen(ProgressView(progress("novato", null, 1, 1), loading = false), {}, "Ana", onRecord = {}) }
        compose.onNodeWithTag("character-class").assertTextEquals("Novato")
        compose.onNodeWithTag("character-stage").assertTextEquals("Etapa I")
        compose.onNodeWithTag("character-appearance").assertContentDescriptionEquals("Novato · Etapa I").assertIsDisplayed()
    }

    @Test fun longUsernameIsCutOnOneLineWithoutMovingTheCutout() {
        fun layout(name: String): Pair<Float, Float> {
            mount { HomeScreen(ProgressView(progress(), loading = false), {}, name, onRecord = {}) }
            val greeting = compose.onNodeWithTag("pj-greeting").fetchSemanticsNode().boundsInRoot
            val image = compose.onNodeWithTag("character-appearance").fetchSemanticsNode().boundsInRoot
            return greeting.height to image.top
        }
        val (shortHeight, shortTop) = layout("Ana")
        val long = "Aventurera_de_nombre_extremadamente_largo_para_una_pantalla_de_360dp"
        val (longHeight, longTop) = layout(long)
        assertEquals("El saludo largo debe ocupar una sola línea", shortHeight, longHeight, 1f)
        assertEquals("El saludo largo no debe desplazar la imagen", shortTop, longTop, 1f)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val username = compose.onNodeWithTag("pj-username").fetchSemanticsNode().boundsInRoot
        assertTrue("El nombre no debe salirse de la pantalla", username.right <= root.right + 1f)
    }

    @Test fun staleAndErrorKeepFeature001NoticesAndRefreshAction() {
        mount { HomeScreen(ProgressView(progress(), loading = false, stale = true, error = ApiFailureCode.NETWORK_ERROR), {}, "Ana", onRecord = {}) }
        compose.onNodeWithText("Mostrando el último progreso confirmado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reintentar consulta").performScrollTo().assertIsDisplayed()
        mount { HomeScreen(ProgressView(loading = false, error = ApiFailureCode.NETWORK_ERROR), {}, "Ana", onRecord = {}) }
        compose.onNodeWithText("No se pudo obtener el progreso").assertIsDisplayed()
        compose.onNodeWithText("Reintentar consulta").assertIsDisplayed()
        compose.onNodeWithTag("pj-record").assertIsDisplayed()
        compose.onNodeWithTag("attribute-STR").assertDoesNotExist()
    }
}
