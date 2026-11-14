package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.Collections
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import lvluplife.data.SessionToken
import lvluplife.ui.theme.LvlUpTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Info tab (US6, FR-011/FR-012): rules, class catalogue, reset with confirmation and logout. Synthetic ports only. */
class InfoScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val session = SessionController()
    private val resets = Collections.synchronizedList(mutableListOf<String>())
    private val progress = RecordFlowProgress {
        RecordProgressView(10, "synthetic-cycle", "2026-11-14", 12, "guerrero", 1,
            listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").associateWith { RecordAttributeView(3, 10, 90) })
    }
    private val records = object : RecordFlowRecords {
        override val online = true
        override suspend fun catalog(uid: String) = listOf(RecordOption("meal", "Comida saludable", false))
        override suspend fun draft(uid: String): RecordDraft? = null
        override suspend fun saveDraft(uid: String, draft: RecordDraft) = Unit
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot = error("No record expected")
        override suspend fun retry(ref: OperationRef): OperationSnapshot = error("No retry expected")
    }
    private val reset = object : ResetFlowOperations {
        override val online = true
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, cycleId: String, confirmed: Boolean): OperationSnapshot {
            resets += cycleId; throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE)
        }
        override suspend fun retry(ref: OperationRef): OperationSnapshot = error("No retry expected")
    }

    private fun mountInfo() {
        session.authenticate(SessionToken("synthetic-info", "synthetic-info-token"))
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme {
            SessionHost(session) { MainTabs(session, "Ana", session::logout, records, progress, reset = reset) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("character-class").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isTab(SessionRoute.INFO)).performClick()
        compose.onNodeWithTag("info-xp").assertExists()
    }

    @Test fun rulesAreGroupedInCardsWithThePrdTexts() {
        mountInfo()
        compose.onNodeWithText("Cómo ganás XP").assertIsDisplayed()
        InfoRules.xp.forEach { compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("info-xp"))).performScrollTo().assertIsDisplayed() }
        // FR-015: Info explains what the quick buttons record and how to load another duration.
        InfoRules.quick.forEach { compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("info-quick"))).performScrollTo().assertIsDisplayed() }
        assertTrue(InfoRules.quick.any { "7 horas" in it } && InfoRules.quick.any { "+ Registrar actividad" in it })
        compose.onNodeWithText("Niveles y constancia").performScrollTo().assertIsDisplayed()
        InfoRules.levels.forEach { compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("info-levels"))).performScrollTo().assertIsDisplayed() }
        // Values that FR-012 ties to the current PRD (RF-20, RF-23, RF-37 and the XP rules section).
        val all = (InfoRules.xp + InfoRules.levels).joinToString("\n")
        for (fact in listOf("48 XP para STR, DEX, INT y WIT; 30 XP para MEN; 28 XP para CON", "1 XP por cada 5 minutos completos",
            "7 XP a CON por cada una de las primeras cuatro comidas", "3 XP a MEN por cada 60 minutos", "2/3 de la XP van al principal",
            "30 × N XP", "nivel máximo es 50", "pierde 5 XP por día", "I en los niveles 1 a 14, II en los niveles 15 a 34 y III en los niveles 35 a 50"))
            assertTrue("Falta «$fact» en las reglas", fact in all)
    }

    @Test fun classCatalogueOpensFromInfoAndComesBackToInfo() {
        mountInfo()
        compose.onNodeWithTag("info-open-classes").performScrollTo().assertTextEquals("Ver todas las clases").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Clases y etapas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nivel general 12").assertIsDisplayed()
        compose.onNodeWithText("Volver a Info").performClick()
        compose.onNodeWithTag("info-classes").assertExists()
        compose.onNodeWithText("Clases y etapas").assertDoesNotExist()
        compose.onNode(isTab(SessionRoute.INFO)).assertIsSelected()
    }

    @Test fun resetAsksForExplicitConfirmationAndCancelChangesNothing() {
        mountInfo()
        compose.onNodeWithTag("info-reset").performScrollTo()
        compose.onNodeWithText("Reiniciar vuelve tu personaje a nivel 1", substring = true).assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reset-open").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reset-open").performScrollTo().performClick()
        compose.onNodeWithText("¿Reiniciar tu personaje?").assertIsDisplayed()
        compose.onNodeWithText("Se conservan tu cuenta, nombre de usuario, actividades personalizadas e historial.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("¿Reiniciar tu personaje?").assertDoesNotExist()
        compose.runOnIdle { assertTrue("Cancelar no debe enviar el reinicio", resets.isEmpty()) }
    }

    @Test fun logoutFromInfoClosesTheSessionLikeFeature001() {
        mountInfo()
        compose.onNodeWithTag("info-logout").performScrollTo().assertTextEquals("Cerrar sesión").performClick()
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, session.current()) }
        compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab)).assertCountEquals(0)
    }
}
