package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import lvluplife.ui.theme.LvlUpTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** FR-008 (T010): create a custom activity from Registrar and come back with it selected. Synthetic ports only. */
class CustomFromRecordTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val session = SessionController()
    private val today = LocalDate.now(ZoneOffset.ofHours(-3))
    private val recordSubmissions = Collections.synchronizedList(mutableListOf<RecordDraft>())

    private class Activities : CustomActivityFlowActivities {
        override val online = true
        val saved = Collections.synchronizedList(mutableListOf<CustomActivityOption>())
        val submitted = Collections.synchronizedList(mutableListOf<CustomActivityDraft>())
        var reject: ApiFailureCode? = null
        override suspend fun list(uid: String) = saved.toList()
        override suspend fun draft(uid: String): CustomActivityDraft? = null
        override suspend fun saveDraft(uid: String, draft: CustomActivityDraft) = Unit
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: CustomActivityDraft): OperationSnapshot {
            submitted += draft
            reject?.let { return OperationSnapshot(OperationRef(uid, intentId), PendingState.REJECTED, it) }
            // The server assigns the id; it is deliberately different from anything derived from the name.
            val id = "server-assigned-${saved.size + 1}"
            saved += CustomActivityOption(id, draft.name, draft.category, draft.primary, draft.secondary)
            val result = ObjectMapper().createObjectNode().put("activityId", id).put("revision", 1)
            return OperationSnapshot(OperationRef(uid, intentId), PendingState.CONFIRMED,
                receipt = RemoteReceipt(intentId, RemoteOutcome.CONFIRMED, Instant.EPOCH, result, null))
        }
        override suspend fun retry(ref: OperationRef): OperationSnapshot = error("No retry expected")
    }

    private fun mount(activities: Activities) {
        session.authenticate(SessionToken("synthetic-custom", "synthetic-custom-token"))
        val records = object : RecordFlowRecords {
            override val online = true
            override suspend fun catalog(uid: String) =
                listOf(RecordOption("meal", "Comida saludable", false, "Alimentación", "CON", null),
                    // A homonym of the created activity: selection must follow the confirmed id, not the name.
                    RecordOption("homonym", "Ajedrez rápido", true, "Juegos", "WIT", null)) +
                    activities.saved.map { RecordOption(it.id, it.name, true, it.category, it.primary, it.secondary) }
            override suspend fun draft(uid: String): RecordDraft? = null
            override suspend fun saveDraft(uid: String, draft: RecordDraft) = Unit
            override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
            override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot {
                recordSubmissions += draft
                return OperationSnapshot(OperationRef(uid, intentId), PendingState.CONFIRMED)
            }
            override suspend fun retry(ref: OperationRef) = OperationSnapshot(ref, PendingState.CONFIRMED)
        }
        val progress = RecordFlowProgress {
            RecordProgressView(10, "synthetic-cycle", today.toString(), 1, "novato", 1,
                listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").associateWith { RecordAttributeView(1, 0, 30) })
        }
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme {
            SessionHost(session) { MainTabs(session, "Ana", session::logout, records, progress, activities) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pj-record").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openCustomFromRecord(): String {
        compose.onNodeWithTag("pj-record").performScrollTo().performClick()
        compose.onNodeWithTag("record-date-1").performClick()
        val chosenDate = today.minusDays(1).toString()
        // FR-008: the action is at the end of the activity list.
        val list = compose.onNodeWithTag("activity-homonym").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("record-create-custom").fetchSemanticsNode().boundsInRoot
        assertTrue("«+ Crear actividad personalizada» va al final de la lista", action.top >= list.bottom)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("record-create-custom").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("record-create-custom").performScrollTo().assertTextEquals("Crear actividad personalizada").performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Ajedrez rápido")
        compose.onNodeWithText("Categoría").performTextReplacement("Juegos")
        compose.onNodeWithTag("custom-primary-INT").performClick()
        return chosenDate
    }

    @Test fun savedCustomActivityReturnsToRecordSelectedByConfirmedIdKeepingTheDate() {
        val activities = Activities(); mount(activities)
        val chosenDate = openCustomFromRecord()
        compose.onNodeWithText("Guardar definición").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("activity-server-assigned-1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("activity-server-assigned-1").assertIsSelected() }.isSuccess }
        compose.onNodeWithTag("activity-homonym").assertIsNotSelected()
        compose.onNodeWithTag("record-date-1").assertIsSelected()
        compose.onNodeWithTag("custom-outcome").assertTextEquals("Actividad personalizada guardada")
        compose.onNodeWithText("Horas").performTextReplacement("0")
        compose.onNodeWithText("Minutos").performTextReplacement("40")
        compose.onNodeWithTag("record-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { recordSubmissions.size == 1 }
        assertEquals(RecordDraft("server-assigned-1", chosenDate, "0", "40"), recordSubmissions.single())
    }

    @Test fun constitutionIsNeverOfferedAndARejectedDefinitionStaysOpenWithoutSelectingAnything() {
        val activities = Activities().apply { reject = ApiFailureCode.INVALID_INPUT }; mount(activities)
        openCustomFromRecord()
        // AC-69: CON cannot be chosen as primary or secondary.
        compose.onNodeWithTag("custom-primary-CON").assertDoesNotExist()
        compose.onNodeWithTag("custom-secondary-CON").assertDoesNotExist()
        compose.onNodeWithText("Guardar definición").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Revisá los datos de la actividad").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Guardar definición").assertExists()
        compose.onNodeWithTag("record-save").assertDoesNotExist()
        assertTrue(activities.saved.isEmpty()); assertEquals(1, activities.submitted.size)
        // Volver goes back to the record form with the previous selection and date untouched.
        compose.onNodeWithText("Volver").performScrollTo().performClick()
        compose.onNodeWithTag("record-save").assertExists()
        compose.onNodeWithTag("record-date-1").assertIsSelected()
        compose.onNodeWithTag("activity-homonym").assertIsNotSelected()
    }
}
