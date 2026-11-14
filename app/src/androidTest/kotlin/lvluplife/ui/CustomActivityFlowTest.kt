package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.*
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real private Room and synthetic IO ports; no progression rules or remote service in this fixture. */
class CustomActivityFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val session = SessionController()
    private class Activities(val store: PendingStore) : CustomActivityFlowActivities {
        override var online by mutableStateOf(true)
        val submitted = Collections.synchronizedList(mutableListOf<Triple<String, String, CustomActivityDraft>>())
        val retries = Collections.synchronizedList(mutableListOf<OperationRef>())
        val reads = Collections.synchronizedList(mutableListOf<String>())
        val saved = Collections.synchronizedMap(mutableMapOf<String, MutableList<CustomActivityOption>>())
        var result = PendingState.CONFIRMED
        var recovery: PendingState? = null
        var error: ApiFailureCode? = null
        var listFails = false
        var delay: CompletableDeferred<Unit>? = null
        override suspend fun list(uid: String): List<CustomActivityOption> {
            reads += uid
            if (listFails) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
            return saved[uid]?.toList().orEmpty()
        }
        override suspend fun draft(uid: String) = withContext(Dispatchers.IO) {
            store.draftFor(uid)?.let { JSONObject(it.content).optJSONObject("customActivity") }?.let {
                CustomActivityDraft(it.getString("name"), it.getString("category"), it.getString("primary"),
                    it.optString("secondary").takeIf(String::isNotEmpty))
            }
        }
        override suspend fun saveDraft(uid: String, draft: CustomActivityDraft) = withContext(Dispatchers.IO) {
            val root = store.draftFor(uid)?.let { JSONObject(it.content) } ?: JSONObject()
            root.put("customActivity", encode(draft)); store.saveDraft(Draft(uid, root.toString()))
        }
        override suspend fun pending(uid: String) = withContext(Dispatchers.IO) {
            store.pendingFor(uid).filter { it.commandType == "CREATE_CUSTOM_ACTIVITY" && it.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED) }.map {
                val state = recovery.takeIf { online } ?: it.state
                if (state == PendingState.CONFIRMED) remember(uid, it.operationId, decode(JSONObject(it.envelope)))
                store.updateState(uid, it.operationId, state, error?.name)
                OperationSnapshot(OperationRef(uid, it.operationId), state, error)
            }
        }
        override suspend fun submit(uid: String, intentId: String, draft: CustomActivityDraft) = withContext(Dispatchers.IO) {
            check(online); UUID.fromString(intentId); submitted += Triple(uid, intentId, draft)
            store.prepare(PendingOperation(uid, intentId, "CREATE_CUSTOM_ACTIVITY", encode(draft).toString(), "synthetic-hash", null, PendingState.PREPARED))
            delay?.await()
            if (result == PendingState.CONFIRMED) remember(uid, intentId, draft)
            store.updateState(uid, intentId, result, error?.name)
            OperationSnapshot(OperationRef(uid, intentId), result, error)
        }
        override suspend fun retry(ref: OperationRef) = withContext(Dispatchers.IO) {
            retries += ref
            val original = store.pendingFor(ref.uid).single { it.operationId == ref.operationId }
            if (result == PendingState.CONFIRMED) remember(ref.uid, ref.operationId, decode(JSONObject(original.envelope)))
            store.updateState(ref.uid, ref.operationId, result, error?.name)
            OperationSnapshot(ref, result, error)
        }
        private fun remember(uid: String, id: String, draft: CustomActivityDraft) {
            val items = saved.getOrPut(uid) { mutableListOf() }
            if (items.none { it.id == id }) items += CustomActivityOption(id, draft.name.trim(), draft.category, draft.primary, draft.secondary)
        }
        private fun encode(d: CustomActivityDraft) = JSONObject().put("name", d.name).put("category", d.category).put("primary", d.primary).put("secondary", d.secondary ?: "")
        private fun decode(o: JSONObject) = CustomActivityDraft(o.getString("name"), o.getString("category"), o.getString("primary"), o.optString("secondary").takeIf(String::isNotEmpty))
    }
    private fun scenario(block: (Activities, String) -> Unit) {
        val name = "custom-flow-${UUID.randomUUID()}.db"
        val store = PendingDatabase.open(context, name)
        try { block(Activities(store), name) }
        finally { unmount(); store.close(); context.deleteDatabase(name) }
    }
    private fun unmount() { compose.runOnIdle { compose.activity.setContent {} }; compose.waitForIdle() }
    private fun mount(a: Activities) {
        if (session.current() == null) session.authenticate(SessionToken("synthetic-a", "synthetic-custom-token"))
        val records = object : RecordFlowRecords {
            override val online get() = a.online
            override suspend fun catalog(uid: String) = listOf(RecordOption("strength", "Entrenamiento de fuerza", true)) +
                a.saved[uid].orEmpty().map { RecordOption(it.id, it.name, true) }
            override suspend fun draft(uid: String): RecordDraft? = null
            override suspend fun saveDraft(uid: String, draft: RecordDraft) = Unit
            override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
            override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot {
                recordSubmissions += draft
                return OperationSnapshot(OperationRef(uid, intentId), PendingState.CONFIRMED, null)
            }
            override suspend fun retry(ref: OperationRef) = error("No record retry expected")
        }
        val progress = RecordFlowProgress { RecordProgressView(10, "synthetic-cycle", "2026-11-11", 1, "novato", 1,
            listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").associateWith { RecordAttributeView(1, 0, 30) }) }
        compose.runOnIdle { compose.activity.setContent { MaterialTheme { RecordFlow(session, records, progress, a) } } }
    }
    private val recordSubmissions = Collections.synchronizedList(mutableListOf<RecordDraft>())
    private fun expect(text: String) {
        compose.waitUntil(3_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun open() { expect("Crear actividad personalizada"); compose.onNodeWithText("Crear actividad personalizada").performClick() }
    private fun fill() {
        open()
        compose.onNodeWithText("Nombre").performTextReplacement("Práctica propia")
        compose.onNodeWithText("Categoría").performTextReplacement("Mi categoría libre")
        compose.onNodeWithTag("custom-primary-STR").performClick()
    }
    private fun send() = compose.onNodeWithText("Guardar definición").performScrollTo().performClick()
    private fun retained() {
        compose.onNodeWithText("Nombre").assertTextContains("Práctica propia")
        compose.onNodeWithText("Categoría").assertTextContains("Mi categoría libre")
        compose.onNodeWithTag("custom-primary-STR").assertIsSelected()
    }
    @Test fun createsWithoutSecondaryAndReusesSavedDefinitionForDuration() = scenario { a, _ ->
        mount(a); fill(); send(); expect("Actividad personalizada guardada")
        assertEquals(CustomActivityDraft("Práctica propia", "Mi categoría libre", "STR"), a.submitted.single().third)
        val id = a.saved["synthetic-a"]!!.single().id
        compose.onNodeWithText("Registrar actividad").performClick()
        compose.onNodeWithText("Práctica propia").performClick()
        compose.onNodeWithText("Fecha").performTextReplacement("2026-11-11")
        compose.onNodeWithText("Horas").performTextReplacement("0")
        compose.onNodeWithText("Minutos").performTextReplacement("30")
        compose.onNodeWithText("Guardar actividad").performScrollTo().performClick()
        compose.waitUntil(3_000) { recordSubmissions.size == 1 }
        assertEquals(id, recordSubmissions.single().activityId); assertEquals(1, a.submitted.size)
    }
    @Test fun acceptsDistinctOptionalSecondaryAndNeverOffersConstitution() = scenario { a, _ ->
        mount(a); fill(); compose.onNodeWithTag("custom-secondary-WIT").performScrollTo().performClick()
        compose.onNodeWithTag("custom-primary-CON").assertDoesNotExist(); compose.onNodeWithTag("custom-secondary-CON").assertDoesNotExist()
        compose.runOnIdle {
            compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        java.io.File(context.cacheDir, "t051-custom-form.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        send(); expect("Actividad personalizada guardada"); assertEquals("WIT", a.submitted.single().third.secondary)
    }
    @Test fun definitiveRejectionPreservesInputsAndCorrectionCreatesAnotherUuid() = scenario { a, _ ->
        a.result = PendingState.REJECTED; a.error = ApiFailureCode.INVALID_INPUT
        mount(a); fill(); send(); expect("Revisá los datos de la actividad"); retained()
        val original = a.submitted.single().second
        compose.onNodeWithText("Nombre").performTextReplacement("Práctica corregida")
        a.result = PendingState.CONFIRMED; a.error = null; send(); expect("Actividad personalizada guardada")
        assertEquals(2, a.submitted.size); assertNotEquals(original, a.submitted[1].second)
    }
    @Test fun offlineKeepsDraftAndDisablesNewCreation() = scenario { a, _ ->
        a.online = false; mount(a); fill(); expect("Sin conexión")
        compose.onNodeWithText("Guardar definición").performScrollTo().assertIsNotEnabled(); retained()
        compose.waitUntil(3_000) { a.store.draftFor("synthetic-a")?.content?.contains("Práctica propia") == true }
        assertTrue(a.submitted.isEmpty()); assertTrue(a.store.pendingFor("synthetic-a").isEmpty())
    }
    @Test fun doubleTapUsesOneUuidWhileResultIsOutstanding() = scenario { a, _ ->
        a.delay = CompletableDeferred(); mount(a); fill(); send()
        compose.onNodeWithText("Guardar definición").assertIsNotEnabled()
        compose.onNodeWithText("Guardar definición").performClick()
        compose.waitUntil(3_000) { a.submitted.size == 1 }; assertEquals(1, a.submitted.size)
        a.delay!!.complete(Unit); expect("Actividad personalizada guardada")
    }
    @Test fun unknownRetryPreservesExactOriginalIntentAndInputs() = scenario { a, _ ->
        a.result = PendingState.UNKNOWN; mount(a); fill(); send(); expect("Confirmación pendiente")
        val original = a.store.pendingFor("synthetic-a").single()
        a.result = PendingState.CONFIRMED
        compose.onNodeWithText("Reintentar definición").performScrollTo().performClick(); expect("Actividad personalizada guardada")
        assertEquals(listOf(OperationRef(original.uid, original.operationId)), a.retries)
        assertEquals(1, a.submitted.size); assertEquals(1, a.saved["synthetic-a"]!!.size)
        assertEquals(original.envelope, a.store.pendingFor("synthetic-a").single().envelope)
    }
    @Test fun recreationRecoversPendingFromReopenedRoomWithoutAnotherSubmit() = scenario { a, name ->
        a.result = PendingState.UNKNOWN; mount(a); fill(); send(); expect("Confirmación pendiente")
        val original = a.store.pendingFor("synthetic-a").single(); unmount()
        compose.activityRule.scenario.recreate()
        PendingDatabase.open(context, name).use { reopened ->
            val restored = Activities(reopened).apply { recovery = PendingState.CONFIRMED }
            mount(restored); expect("Actividad personalizada guardada")
            assertTrue(restored.submitted.isEmpty()); assertEquals(original.operationId, restored.saved["synthetic-a"]!!.single().id)
            unmount()
        }
    }
    @Test fun draftSurvivesRecreationAndDoesNotOverwriteOtherDraftNamespaces() = scenario { a, name ->
        a.store.saveDraft(Draft("synthetic-a", JSONObject().put("profileUsername", "Nombre conservado").put("recordDraft", JSONObject().put("minutes", "25")).toString()))
        mount(a); fill()
        compose.waitUntil(3_000) { a.store.draftFor("synthetic-a")?.content?.contains("Práctica propia") == true }
        unmount(); compose.activityRule.scenario.recreate()
        PendingDatabase.open(context, name).use { reopened ->
            mount(Activities(reopened)); open(); retained()
            val saved = JSONObject(reopened.draftFor("synthetic-a")!!.content)
            assertEquals("Nombre conservado", saved.getString("profileUsername")); assertEquals("25", saved.getJSONObject("recordDraft").getString("minutes"))
            unmount()
        }
    }
    @Test fun listErrorIsNotPresentedAsSuccessfulEmptyStateAndRetryRecovers() = scenario { a, _ ->
        a.listFails = true; mount(a); open(); expect("No se pudieron cargar tus actividades")
        compose.onNodeWithText("Todavía no tenés actividades propias").assertDoesNotExist()
        a.listFails = false; compose.onNodeWithText("Reintentar actividades propias").performClick()
        expect("Todavía no tenés actividades propias")
    }
    @Test fun accountChangeHidesOriginalDraftAndNeverRecoversItsPendingForAnotherOwner() = scenario { a, _ ->
        a.result = PendingState.UNKNOWN; mount(a); fill(); send(); expect("Confirmación pendiente")
        val original = a.store.pendingFor("synthetic-a").single()
        compose.runOnIdle { session.authenticate(SessionToken("synthetic-b", "synthetic-b-token")) }
        open(); compose.onNodeWithText("Nombre").assertTextContains("", substring = false)
        compose.onNodeWithText("Confirmación pendiente").assertDoesNotExist()
        assertTrue(a.retries.isEmpty()); assertEquals(original, a.store.pendingFor("synthetic-a").single())
        assertTrue(a.store.pendingFor("synthetic-b").isEmpty())
    }
}
