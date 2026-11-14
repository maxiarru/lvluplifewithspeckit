package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.PendingState
import lvluplife.data.SessionToken
import lvluplife.ui.theme.LvlUpTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Registrar opened from PJ (US4, T009). Synthetic ports: no network, Firebase or XP calculation. */
class RecordScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val session = SessionController()
    private val today = LocalDate.now(ZoneOffset.ofHours(-3))
    private val submissions = Collections.synchronizedList(mutableListOf<RecordDraft>())
    private val progress = RecordFlowProgress {
        RecordProgressView(10, "synthetic-cycle", today.toString(), 1, "novato", 1,
            listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").associateWith { RecordAttributeView(1, 0, 30) })
    }
    private val records = object : RecordFlowRecords {
        override val online = true
        override suspend fun catalog(uid: String) = listOf(
            RecordOption("strength", "Entrenamiento de fuerza", true, "Ejercicio", "STR", "CON"),
            RecordOption("meal", "Comida saludable", false, "Alimentación", "CON", null))
        override suspend fun draft(uid: String): RecordDraft? = null
        override suspend fun saveDraft(uid: String, draft: RecordDraft) = Unit
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot {
            submissions += draft
            return OperationSnapshot(OperationRef(uid, intentId), PendingState.CONFIRMED)
        }
        override suspend fun retry(ref: OperationRef) = OperationSnapshot(ref, PendingState.CONFIRMED)
    }

    private fun mount() {
        session.authenticate(SessionToken("synthetic-record", "synthetic-record-token"))
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme {
            SessionHost(session) { MainTabs(session, "Ana", session::logout, records, progress) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pj-record").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun savedActivityIsRecordedFromPjInThreeInteractions() {
        mount()
        var interactions = 0
        fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick(); interactions++ }
        tap("pj-record")
        tap("activity-meal")
        tap("record-save")
        compose.waitUntil(5_000) { submissions.size == 1 }
        assertEquals("RNF-04: como máximo tres interacciones", 3, interactions)
        assertEquals(RecordDraft("meal", today.toString(), "0", ""), submissions.single())
    }

    @Test fun chosenActivityShowsCategoryModeAndPreassignedAttributesReadOnly() {
        mount()
        compose.onNodeWithTag("pj-record").performScrollTo().performClick()
        compose.onNodeWithTag("activity-strength").performScrollTo().performClick()
        compose.onNodeWithTag("record-detail-category").assertTextEquals("Categoría: Ejercicio")
        compose.onNodeWithTag("record-detail-mode").assertTextEquals("Modalidad: por duración")
        compose.onNodeWithText("STR (principal)", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("· CON (secundario)", useUnmergedTree = true).assertIsDisplayed()
        // Each activity also shows its attribute codes as text, not only as color.
        compose.onNodeWithTag("activity-strength").assert(hasText("STR · CON"))
        compose.onNodeWithTag("activity-meal").performScrollTo().performClick()
        compose.onNodeWithTag("record-detail-category").assertTextEquals("Categoría: Alimentación")
        compose.onNodeWithTag("record-detail-mode").assertTextEquals("Modalidad: por ocurrencia")
        compose.onNodeWithText("CON (principal)", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("(secundario)", substring = true, useUnmergedTree = true).assertCountEquals(0)
        // Read only: the details offer no control to change attributes.
        compose.onNodeWithTag("record-details").onChildren().filter(hasClickAction()).assertCountEquals(0)
    }

    @Test fun datesAreHoyAyerAnteayerAndTheChosenOneIsSent() {
        mount()
        compose.onNodeWithTag("pj-record").performScrollTo().performClick()
        compose.onNodeWithTag("record-date-0").assert(hasText("Hoy")).assertIsSelected()
        compose.onNodeWithTag("record-date-1").assert(hasText("Ayer")).assertIsNotSelected()
        compose.onNodeWithTag("record-date-2").assert(hasText("Anteayer")).performClick().assertIsSelected()
        compose.onNodeWithTag("record-date-0").assertIsNotSelected()
        compose.onNodeWithTag("activity-meal").performScrollTo().performClick()
        compose.onNodeWithTag("record-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { submissions.size == 1 }
        assertEquals(today.minusDays(2).toString(), submissions.single().date)
    }

    @Test fun volverClosesRegistrarAndReturnsToPj() {
        mount()
        compose.onNodeWithTag("pj-record").performScrollTo().performClick()
        compose.onNodeWithTag("pj-record").assertDoesNotExist()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithTag("pj-record").assertExists()
        compose.onNode(isTab(SessionRoute.HOME)).assertIsSelected()
    }
}
