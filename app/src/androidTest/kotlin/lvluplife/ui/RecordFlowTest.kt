package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI intent/recovery contracts. Real private Room, synthetic ports: no network, Firebase or XP calculation. */
class RecordFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val session = SessionController()
    private fun progress(revision: Long = 10, xp: Int = 0) = RecordProgressView(revision, "synthetic-cycle", "2026-11-11", 1,
        "novato", 1, listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").associateWith { RecordAttributeView(1, if (it == "STR") xp else 0) })
    private class Records(val store: PendingStore) : RecordFlowRecords, java.io.Closeable {
        private var closed = false
        override fun close() { if (!closed) { closed = true; store.close() } }
        override var online by mutableStateOf(true)
        val submissions = Collections.synchronizedList(mutableListOf<Pair<String, RecordDraft>>())
        val retries = Collections.synchronizedList(mutableListOf<OperationRef>())
        var response = PendingState.CONFIRMED
        var error: ApiFailureCode? = null
        var delay: CompletableDeferred<Unit>? = null
        override suspend fun catalog(uid: String) = listOf(RecordOption("strength", "Entrenamiento de fuerza", true), RecordOption("meal", "Comida saludable", false))
        override suspend fun draft(uid: String): RecordDraft? = withContext(Dispatchers.IO) { store.draftFor(uid)?.let {
            val value = JSONObject(it.content)
            RecordDraft(value.getString("activityId"), value.getString("date"), value.getString("hours"), value.getString("minutes"))
        } }
        override suspend fun saveDraft(uid: String, draft: RecordDraft) = withContext(Dispatchers.IO) { store.saveDraft(Draft(uid, encode(draft))) }
        override suspend fun pending(uid: String) = withContext(Dispatchers.IO) { store.pendingFor(uid).filter { it.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED) }
            .map { OperationSnapshot(OperationRef(uid, it.operationId), it.state, it.safeError?.let(ApiFailureCode::valueOf)) }
        }
        override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot = withContext(Dispatchers.IO) {
            check(online); submissions += intentId to draft
            // Fixture IO boundary: keep exactly the submitted intent's UUID; never coalesce by equal payload.
            UUID.fromString(intentId)
            store.prepare(PendingOperation(uid, intentId, "CREATE_RECORD", encode(draft), "synthetic-hash", "synthetic-cycle", PendingState.PREPARED))
            delay?.await()
            store.updateState(uid, intentId, response, error?.name)
            OperationSnapshot(OperationRef(uid, intentId), response, error)
        }
        override suspend fun retry(ref: OperationRef): OperationSnapshot {
            retries += ref
            return OperationSnapshot(ref, response, error)
        }
        private fun encode(draft: RecordDraft) = JSONObject().put("activityId", draft.activityId).put("date", draft.date)
            .put("hours", draft.hours).put("minutes", draft.minutes).toString()
    }
    private fun scenario(block: (Records, String) -> Unit) {
        val name = "record-flow-${UUID.randomUUID()}.db"
        try { Records(PendingDatabase.open(context, name)).use {
            try { block(it, name) } finally { unmount() }
        } }
        finally { context.deleteDatabase(name) }
    }
    private fun unmount() { compose.runOnIdle { compose.activity.setContent {} }; compose.waitForIdle() }
    private fun mount(records: Records, progress: RecordFlowProgress = RecordFlowProgress { progress() }) {
        if (session.current() == null) session.authenticate(SessionToken("synthetic-a", "synthetic-record-token"))
        compose.runOnIdle { compose.activity.setContent { MaterialTheme { RecordFlow(session, records, progress) } } }
    }
    private fun expect(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun form() { expect("Registrar actividad"); compose.onNodeWithText("Registrar actividad").performClick() }
    private fun fill() {
        form(); compose.onNodeWithText("Entrenamiento de fuerza").performClick()
        compose.onNodeWithText("Fecha").performTextReplacement("2026-11-11")
        compose.onNodeWithText("Horas").performTextReplacement("0")
        compose.onNodeWithText("Minutos").performTextReplacement("30")
    }
    private fun send() = compose.onNodeWithText("Guardar actividad").performClick()

    @Test fun doubleTapKeepsOneIntentAndDisablesSendingUntilDefinitiveReply() = scenario { r, _ ->
        r.delay = CompletableDeferred(); mount(r); fill(); send()
        compose.onNodeWithText("Guardar actividad").assertIsNotEnabled()
        compose.waitUntil(5_000) { r.submissions.size == 1 }
        compose.onNodeWithText("Guardar actividad").performClick()
        assertEquals(1, r.submissions.size)
        r.delay!!.complete(Unit); expect("Actividad guardada")
        assertEquals(1, r.store.pendingFor("synthetic-a").size)
    }
    @Test fun intentionalEqualActivityAfterConfirmationUsesAnotherUuid() = scenario { r, _ ->
        mount(r); fill(); send(); expect("Actividad guardada")
        fill(); send(); expect("Actividad guardada")
        compose.waitUntil(5_000) { r.submissions.size == 2 }
        assertNotEquals(r.submissions[0].first, r.submissions[1].first)
        assertEquals(r.submissions[0].second, r.submissions[1].second)
    }
    @Test fun offlineDraftIsRetainedWithoutSendingAnyNewActivity() = scenario { r, _ ->
        r.online = false; mount(r); fill()
        expect("Sin conexión"); compose.onNodeWithText("Guardar actividad").assertIsNotEnabled()
        compose.waitUntil(5_000) { r.store.draftFor("synthetic-a") != null }
        assertTrue(r.submissions.isEmpty()); assertTrue(r.store.pendingFor("synthetic-a").isEmpty())
    }
    @Test fun activityRecreationRestoresDraftFromReopenedRoom() = scenario { r, name ->
        mount(r); fill()
        compose.waitUntil(5_000) { r.store.draftFor("synthetic-a")?.content?.contains("30") == true }
        unmount(); r.close()
        compose.activityRule.scenario.recreate()
        PendingDatabase.open(context, name).use { reopened ->
            mount(Records(reopened)); form(); compose.onNodeWithText("Minutos").assertTextContains("30")
            unmount()
        }
    }
    @Test fun pendingAfterRecreationResumesOriginalUuidRatherThanCreatingAnActivity() = scenario { r, name ->
        r.response = PendingState.UNKNOWN; mount(r); fill(); send(); expect("Confirmación pendiente")
        val original = r.store.pendingFor("synthetic-a").single()
        unmount(); r.close()
        compose.activityRule.scenario.recreate()
        PendingDatabase.open(context, name).use { reopened ->
            val restored = Records(reopened).apply { response = PendingState.UNKNOWN }
            mount(restored); expect("Confirmación pendiente")
            compose.onNodeWithText("Reintentar operación").performClick()
            compose.waitUntil(5_000) { restored.retries.size == 1 }
            assertEquals(original.operationId, restored.retries.single().operationId)
            assertTrue(restored.submissions.isEmpty()); assertEquals(original, reopened.pendingFor("synthetic-a").single())
            unmount()
        }
    }
    @Test fun unknownLookupKeepsDraftAndNeverDisplaysOptimisticXp() = scenario { r, _ ->
        r.response = PendingState.UNKNOWN; mount(r); fill(); send(); expect("Confirmación pendiente")
        compose.onNodeWithText("Reintentar operación").performClick()
        expect("Confirmación pendiente")
        assertNotNull(r.store.draftFor("synthetic-a")); assertEquals(1, r.submissions.size)
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("0 XP")
    }
    @Test fun unauthorizedResultSuspendsRetryUntilAuthenticationWithoutDroppingEnvelope() = scenario { r, _ ->
        r.response = PendingState.UNKNOWN; r.error = ApiFailureCode.AUTH_REQUIRED
        mount(r); fill(); send(); expect("Volvé a iniciar sesión para resolver la operación")
        compose.onNodeWithText("Reintentar operación").assertIsNotEnabled()
        assertEquals(1, r.store.pendingFor("synthetic-a").size); assertTrue(r.retries.isEmpty())
    }
    @Test fun forbiddenResultStopsAutomaticRetryAndPreservesPendingEnvelope() = scenario { r, _ ->
        r.response = PendingState.UNKNOWN; r.error = ApiFailureCode.FORBIDDEN
        mount(r); fill(); send(); expect("Acceso denegado. La operación sigue pendiente.")
        compose.onNodeWithText("Reintentar operación").assertIsNotEnabled()
        assertEquals(1, r.store.pendingFor("synthetic-a").size); assertTrue(r.retries.isEmpty())
    }
    @Test fun functionalRejectionRetainsInputAndCorrectionCreatesANewIntent() = scenario { r, _ ->
        r.response = PendingState.REJECTED; r.error = ApiFailureCode.INVALID_INPUT
        mount(r); fill(); send(); expect("Revisá los datos ingresados.")
        compose.onNodeWithText("Minutos").assertTextContains("30")
        val old = r.store.pendingFor("synthetic-a").single()
        r.response = PendingState.CONFIRMED; r.error = null
        compose.onNodeWithText("Minutos").performTextReplacement("35"); send(); expect("Actividad guardada")
        assertNotEquals(r.submissions[0].first, r.submissions[1].first)
        assertEquals(old, r.store.pendingFor("synthetic-a").single { it.operationId == old.operationId })
    }
    @Test fun mealDoesNotRequestDurationComponents() = scenario { r, _ ->
        mount(r); form(); compose.onNodeWithText("Comida saludable").performClick()
        compose.onNodeWithText("Horas").assertDoesNotExist(); compose.onNodeWithText("Minutos").assertDoesNotExist()
        send(); expect("Actividad guardada"); assertEquals("meal", r.submissions.single().second.activityId)
    }
    @Test fun homeShowsSixAttributesAndGeneralLevelWithoutGeneralXpBar() = scenario { r, _ ->
        mount(r); expect("Registrar actividad")
        for (attribute in listOf("STR", "DEX", "INT", "WIT", "MEN", "CON"))
            compose.onNodeWithTag("attribute-$attribute-xp").assertTextEquals("0 XP")
        expect("Nivel general 1"); compose.onNodeWithTag("general-xp-bar").assertDoesNotExist()
        compose.onNodeWithTag("character-class").assertTextContains("Novato")
    }
    @Test fun loadingDoesNotPretendInitialZeroProgressIsConfirmed() = scenario { r, _ ->
        val delayed = CompletableDeferred<RecordProgressView>()
        mount(r, RecordFlowProgress { delayed.await() }); expect("Cargando progreso…")
        compose.onNodeWithTag("attribute-STR-xp").assertDoesNotExist()
        delayed.complete(progress(12, 12)); compose.waitUntil(5_000) { compose.onAllNodesWithTag("attribute-STR-xp").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("12 XP")
    }
    @Test fun olderProgressCannotReplaceANewerConfirmedRevision() = scenario { r, _ ->
        val calls = AtomicInteger()
        mount(r, RecordFlowProgress { if (calls.incrementAndGet() == 1) progress(12, 12) else progress(2, 6) })
        expect("Registrar actividad"); compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("12 XP")
        compose.onNodeWithText("Actualizar progreso").performClick()
        compose.waitUntil(5_000) { calls.get() >= 2 }
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("12 XP")
    }
    @Test fun queryFailureKeepsVisibleConfirmedProgressMarkedStale() = scenario { r, _ ->
        val calls = AtomicInteger()
        mount(r, RecordFlowProgress { if (calls.incrementAndGet() == 1) progress(12, 12) else throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) })
        expect("Registrar actividad"); compose.onNodeWithText("Actualizar progreso").performClick()
        expect("Mostrando el último progreso confirmado")
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("12 XP")
    }
    @Test fun resolvingAnOldReceiptReloadsCurrentProgressWithoutRegressingItsRevision() = scenario { r, _ ->
        val id = UUID.randomUUID().toString()
        r.store.prepare(PendingOperation("synthetic-a", id, "CREATE_RECORD", "{}", "synthetic-hash", "synthetic-old-cycle", PendingState.UNKNOWN))
        val calls = AtomicInteger()
        mount(r, RecordFlowProgress { calls.incrementAndGet(); progress(12, 12) })
        expect("Confirmación pendiente")
        compose.onNodeWithText("Reintentar operación").performClick()
        compose.waitUntil(5_000) { r.retries.size == 1 && calls.get() >= 2 }
        assertEquals(id, r.retries.single().operationId)
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("12 XP")
    }
    @Test fun lateProgressFromPreviousAccountCannotReplaceTheNewAccountsView() = scenario { r, _ ->
        val old = CompletableDeferred<RecordProgressView>(); val started = CompletableDeferred<Unit>()
        mount(r, RecordFlowProgress { uid -> if (uid == "synthetic-a") { started.complete(Unit); old.await() } else progress(1, 0) })
        compose.waitUntil(5_000) { started.isCompleted }
        compose.runOnIdle { session.authenticate(SessionToken("synthetic-b", "synthetic-token-b")) }
        expect("Registrar actividad"); old.complete(progress(99, 999))
        compose.waitForIdle(); compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("0 XP")
    }
    @Test fun initialQueryFailureOffersRetryWithoutInventingEmptyOrConfirmedProgress() = scenario { r, _ ->
        val calls = AtomicInteger()
        mount(r, RecordFlowProgress { if (calls.incrementAndGet() == 1) throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) else progress(12, 12) })
        expect("No se pudo obtener el progreso")
        compose.onNodeWithTag("attribute-STR-xp").assertDoesNotExist()
        compose.onNodeWithText("Reintentar consulta").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("attribute-STR-xp").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("12 XP")
    }
    @Test fun anotherAccountNeverSeesOrResumesTheOriginalAccountsPendingOperation() = scenario { r, _ ->
        val id = UUID.randomUUID().toString()
        r.store.prepare(PendingOperation("synthetic-a", id, "CREATE_RECORD", "{}", "synthetic-hash", "synthetic-cycle", PendingState.UNKNOWN))
        session.authenticate(SessionToken("synthetic-b", "synthetic-token-b"))
        mount(r); expect("Registrar actividad")
        compose.onNodeWithText("Confirmación pendiente").assertDoesNotExist()
        assertTrue(r.retries.isEmpty()); assertTrue(r.store.pendingFor("synthetic-b").isEmpty())
        assertEquals(id, r.store.pendingFor("synthetic-a").single().operationId)
    }
}
