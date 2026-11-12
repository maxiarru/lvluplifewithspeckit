package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
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

/** Real private Room, synthetic query/mutation ports. No XP engine, HTTP or Firebase in the fixture. */
class HistoryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val session = SessionController()
    private class Fixture(val store: PendingStore) : HistoryFlowHistory, HistoryFlowCorrections {
        override var online by mutableStateOf(true)
        val rows = Collections.synchronizedMap(mutableMapOf<String,List<HistoryItem>>())
        val loads = Collections.synchronizedList(mutableListOf<Pair<String,HistorySelection>>())
        val edits = Collections.synchronizedList(mutableListOf<Triple<String,String,CorrectionDraft>>())
        val deletes = Collections.synchronizedList(mutableListOf<Triple<String,String,HistoryItem>>())
        val retries = Collections.synchronizedList(mutableListOf<OperationRef>())
        var failLoad = false
        var loadGate: CompletableDeferred<HistoryPage>? = null
        var mutationGate: CompletableDeferred<Unit>? = null
        var result = PendingState.CONFIRMED
        var error: ApiFailureCode? = null
        var recovered: PendingState? = null
        val current = HistoryItem("record-one","cycle-current","strength","Entrenamiento de fuerza","2026-11-11",30,
            Instant.parse("2026-11-11T14:59:00Z"),1,true)
        init { rows["synthetic-a"]=listOf(current);rows["synthetic-b"]=emptyList() }
        override suspend fun load(uid: String, selection: HistorySelection): HistoryPage {
            loads += uid to selection
            if(failLoad) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
            loadGate?.let { return it.await() }
            return HistoryPage(10,"2026-11-11","2026-11-12",rows[uid].orEmpty().toList())
        }
        override suspend fun draft(uid: String): CorrectionDraft? = withContext(Dispatchers.IO) {
            store.draftFor(uid)?.let { JSONObject(it.content).optJSONObject("correctionDraft") }?.let(::decode)
        }
        override suspend fun saveDraft(uid: String, draft: CorrectionDraft) = withContext(Dispatchers.IO) {
            val root=store.draftFor(uid)?.let { JSONObject(it.content) } ?: JSONObject()
            root.put("correctionDraft",encode(draft));store.saveDraft(Draft(uid,root.toString()))
        }
        override suspend fun pending(uid: String) = withContext(Dispatchers.IO) {
            store.pendingFor(uid).filter { it.commandType in setOf("EDIT_RECORD","DELETE_RECORD") && it.state !in setOf(PendingState.CONFIRMED,PendingState.REJECTED) }.map {
                val state=recovered.takeIf { online } ?: it.state
                store.updateState(uid,it.operationId,state,error?.name)
                OperationSnapshot(OperationRef(uid,it.operationId),state,error)
            }
        }
        override suspend fun edit(uid: String,intentId: String,draft: CorrectionDraft) = withContext(Dispatchers.IO) {
            check(online);UUID.fromString(intentId);edits += Triple(uid,intentId,draft)
            store.prepare(PendingOperation(uid,intentId,"EDIT_RECORD",encode(draft).toString(),"synthetic-hash",draft.cycleId,PendingState.PREPARED))
            mutationGate?.await()
            if(result==PendingState.CONFIRMED) rows[uid]=rows[uid].orEmpty().map { if(it.id==draft.recordId) it.copy(version=it.version+1,date=draft.date,
                durationMinutes=if(draft.hours==null) null else draft.hours.toInt()*60+draft.minutes!!.toInt()) else it }
            store.updateState(uid,intentId,result,error?.name);OperationSnapshot(OperationRef(uid,intentId),result,error)
        }
        override suspend fun delete(uid: String,intentId: String,item: HistoryItem) = withContext(Dispatchers.IO) {
            check(online);UUID.fromString(intentId);deletes += Triple(uid,intentId,item)
            val value=JSONObject().put("recordId",item.id).put("cycleId",item.cycleId).put("expectedVersion",item.version)
            store.prepare(PendingOperation(uid,intentId,"DELETE_RECORD",value.toString(),"synthetic-hash",item.cycleId,PendingState.PREPARED))
            mutationGate?.await()
            if(result==PendingState.CONFIRMED) rows[uid]=rows[uid].orEmpty().filterNot { it.id==item.id }
            store.updateState(uid,intentId,result,error?.name);OperationSnapshot(OperationRef(uid,intentId),result,error)
        }
        override suspend fun retry(ref: OperationRef) = withContext(Dispatchers.IO) {
            retries += ref;store.updateState(ref.uid,ref.operationId,result,error?.name);OperationSnapshot(ref,result,error)
        }
        private fun encode(d: CorrectionDraft)=JSONObject().put("recordId",d.recordId).put("cycleId",d.cycleId).put("expectedVersion",d.expectedVersion)
            .put("activityId",d.activityId).put("date",d.date).put("hours",d.hours ?: JSONObject.NULL).put("minutes",d.minutes ?: JSONObject.NULL)
        private fun decode(o: JSONObject)=CorrectionDraft(o.getString("recordId"),o.getString("cycleId"),o.getLong("expectedVersion"),o.getString("activityId"),o.getString("date"),
            o.get("hours").takeUnless { it==JSONObject.NULL }?.toString(),o.get("minutes").takeUnless { it==JSONObject.NULL }?.toString())
    }
    private fun scenario(block:(Fixture,String)->Unit) {
        val name="history-flow-${UUID.randomUUID()}.db";val store=PendingDatabase.open(context,name)
        try { block(Fixture(store),name) } finally { unmount();store.close();context.deleteDatabase(name) }
    }
    private fun unmount() { compose.runOnIdle { compose.activity.setContent {} };compose.waitForIdle() }
    private fun mount(f:Fixture) {
        if(session.current()==null)session.authenticate(SessionToken("synthetic-a","synthetic-history-token"))
        compose.runOnIdle { compose.activity.setContent { MaterialTheme {
            SessionHost(session) { view -> if(view.routes.lastOrNull()==SessionRoute.HISTORY) HistoryFlow(session,f,f) }
        } } }
    }
    private fun expect(text:String) {
        compose.waitUntil(3_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun open() {
        expect("Historial");compose.onNodeWithText("Historial").performClick()
        expect("Día")
    }
    private fun edit() { expect("Entrenamiento de fuerza");compose.onNodeWithTag("history-edit-record-one").performScrollTo().performClick() }
    private fun change() { edit();compose.onNodeWithText("Minutos").performTextReplacement("45") }
    private fun send()=compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()

    @Test fun successfulEmptyPeriodIsExplicitAndDifferentFromLoading() = scenario { f,_ ->
        f.rows["synthetic-a"]=emptyList();mount(f);open();expect("No hay registros en este período")
        compose.onNodeWithText("No se pudo cargar el historial").assertDoesNotExist();assertTrue(f.loads.isNotEmpty())
    }
    @Test fun initialQueryErrorNeverBecomesEmptyAndRetryRecoversRows() = scenario { f,_ ->
        f.failLoad=true;mount(f);open();expect("No se pudo cargar el historial")
        compose.onNodeWithText("No hay registros en este período").assertDoesNotExist()
        f.failLoad=false;compose.onNodeWithText("Reintentar historial").performClick();expect("Entrenamiento de fuerza")
    }
    @Test fun refreshFailureRetainsOwnRowsMarkedStale() = scenario { f,_ ->
        mount(f);open();expect("Entrenamiento de fuerza");f.failLoad=true
        compose.onNodeWithText("Actualizar historial").performClick();expect("Mostrando el último historial confirmado")
        expect("Entrenamiento de fuerza");compose.onNodeWithText("No hay registros en este período").assertDoesNotExist()
    }
    @Test fun periodAndDateSelectionArePassedToQueryInsteadOfLocallyFilteringPartialRows() = scenario { f,_ ->
        mount(f);open();compose.onNodeWithText("Semana").performClick()
        compose.onNodeWithText("Fecha del período").performTextReplacement("2026-11-09")
        compose.onNodeWithText("Consultar período").performScrollTo().performClick()
        compose.waitUntil(3_000) { f.loads.any { it.second==HistorySelection(HistoryPeriod.week,"2026-11-09") } }
        compose.onNodeWithText("Mes").performClick()
        compose.waitUntil(3_000) { f.loads.any { it.second.period==HistoryPeriod.month } }
    }
    @Test fun latePreviousPeriodResponseCannotReplaceNewSelection() = scenario { f,_ ->
        val gate=CompletableDeferred<HistoryPage>();f.loadGate=gate;mount(f);open()
        compose.waitUntil(3_000) { f.loads.isNotEmpty() };f.loadGate=null
        f.rows["synthetic-a"]=listOf(f.current.copy(name="Resultado mensual"))
        compose.onNodeWithText("Mes").performClick();expect("Resultado mensual")
        gate.complete(HistoryPage(9,"2026-11-11","2026-11-12",listOf(f.current.copy(name="Respuesta diaria vieja"))))
        compose.waitForIdle();compose.onNodeWithText("Respuesta diaria vieja").assertDoesNotExist()
    }
    @Test fun previousCycleRowsRemainVisibleButDoNotOfferCorrections() = scenario { f,_ ->
        f.rows["synthetic-a"]=listOf(f.current.copy(cycleId="cycle-previous",canCorrect=false))
        mount(f);open();expect("Entrenamiento de fuerza")
        compose.onNodeWithTag("history-edit-record-one").assertDoesNotExist();compose.onNodeWithTag("history-delete-record-one").assertDoesNotExist()
        java.io.File(context.cacheDir,"t061-history.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun editingSendsOriginalIdentityVersionAndKeepsOneIntentDuringDoubleTap() = scenario { f,_ ->
        f.mutationGate=CompletableDeferred();mount(f);open();change();send()
        compose.onNodeWithText("Guardar cambios").assertIsNotEnabled();compose.onNodeWithText("Guardar cambios").performClick()
        compose.waitUntil(3_000) { f.edits.size==1 }
        val draft=f.edits.single().third;assertEquals("record-one",draft.recordId);assertEquals("cycle-current",draft.cycleId)
        assertEquals(1L,draft.expectedVersion);assertEquals("45",draft.minutes)
        f.mutationGate!!.complete(Unit);expect("Cambios guardados");assertEquals(1,f.edits.size)
    }
    @Test fun mealDateCorrectionNeverAsksForDurationFields() = scenario { f,_ ->
        f.rows["synthetic-a"]=listOf(f.current.copy(activityId="meal",name="Comida saludable",durationMinutes=null))
        mount(f);open();expect("Comida saludable");compose.onNodeWithTag("history-edit-record-one").performScrollTo().performClick()
        compose.onNodeWithText("Horas").assertDoesNotExist();compose.onNodeWithText("Minutos").assertDoesNotExist()
        compose.onNodeWithText("Fecha").performTextReplacement("2026-11-10");send();expect("Cambios guardados")
        assertNull(f.edits.single().third.hours);assertNull(f.edits.single().third.minutes)
    }
    @Test fun definitiveRejectionRetainsInputsAndCorrectionGetsANewUuid() = scenario { f,_ ->
        f.result=PendingState.REJECTED;f.error=ApiFailureCode.INVALID_INPUT
        mount(f);open();change();send();expect("Revisá los datos de la corrección")
        compose.onNodeWithText("Minutos").assertTextContains("45");val original=f.edits.single().second
        compose.onNodeWithText("Minutos").performTextReplacement("40");f.result=PendingState.CONFIRMED;f.error=null
        send();expect("Cambios guardados");assertNotEquals(original,f.edits.last().second)
    }
    @Test fun versionConflictShowsFreshWinnerAndDoesNotAutomaticallyOverwriteIt() = scenario { f,_ ->
        mount(f);open();change();f.rows["synthetic-a"]=listOf(f.current.copy(name="Versión vigente",durationMinutes=35,version=2))
        f.result=PendingState.REJECTED;f.error=ApiFailureCode.RECORD_STALE;send();expect("El registro cambió. Revisá los datos actuales.")
        expect("Versión vigente");assertEquals(1,f.edits.size);assertEquals(1L,f.edits.single().third.expectedVersion)
        assertEquals(2L,f.rows["synthetic-a"]!!.single().version)
    }
    @Test fun deletedConflictShowsRemovalAndNeverRecreatesRecord() = scenario { f,_ ->
        mount(f);open();change();f.rows["synthetic-a"]=emptyList();f.result=PendingState.REJECTED;f.error=ApiFailureCode.RECORD_DELETED
        send();expect("El registro ya no existe");compose.onNodeWithText("Guardar cambios").assertDoesNotExist()
        assertEquals(1,f.edits.size);assertTrue(f.rows["synthetic-a"]!!.isEmpty())
    }
    @Test fun unknownEditDoesNotAnnounceSuccessOrDefinitiveFailureAndRetryKeepsUuid() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;mount(f);open();change();send();expect("Confirmación pendiente")
        compose.onNodeWithText("Cambios guardados").assertDoesNotExist();compose.onNodeWithText("No se pudo guardar").assertDoesNotExist()
        val original=f.store.pendingFor("synthetic-a").single()
        compose.onNodeWithText("Reintentar corrección").performScrollTo().performClick()
        compose.waitUntil(3_000) { f.retries.size==1 };assertEquals(OperationRef(original.uid,original.operationId),f.retries.single())
        assertEquals(1,f.edits.size);assertEquals(original.envelope,f.store.pendingFor("synthetic-a").single().envelope)
    }
    @Test fun unknownDeleteKeepsVisibleRowUntilConfirmedAndNeverDeletesTwice() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;mount(f);open();expect("Entrenamiento de fuerza")
        compose.onNodeWithTag("history-delete-record-one").performScrollTo().performClick()
        expect("Confirmar eliminación");compose.onNodeWithText("Confirmar eliminación").performClick();expect("Confirmación pendiente")
        expect("Entrenamiento de fuerza");compose.onNodeWithText("Registro eliminado").assertDoesNotExist()
        val original=f.store.pendingFor("synthetic-a").single()
        f.result=PendingState.CONFIRMED;f.rows["synthetic-a"]=emptyList()
        compose.onNodeWithText("Reintentar corrección").performScrollTo().performClick();expect("Registro eliminado")
        assertEquals(listOf(OperationRef(original.uid,original.operationId)),f.retries);assertEquals(1,f.deletes.size)
    }
    @Test fun cancelDeleteDoesNotCreateAnOperationOrRemoveVisibleRow() = scenario { f,_ ->
        mount(f);open();expect("Entrenamiento de fuerza");compose.onNodeWithTag("history-delete-record-one").performScrollTo().performClick()
        expect("Confirmar eliminación");compose.onNodeWithText("Cancelar").performClick();expect("Entrenamiento de fuerza")
        assertTrue(f.deletes.isEmpty());assertTrue(f.store.pendingFor("synthetic-a").isEmpty())
    }
    @Test fun recreationRecoversPendingCorrectionFromReopenedRoomWithoutNewSubmit() = scenario { f,name ->
        f.result=PendingState.UNKNOWN;mount(f);open();change();send();expect("Confirmación pendiente")
        val original=f.store.pendingFor("synthetic-a").single();unmount();compose.activityRule.scenario.recreate()
        PendingDatabase.open(context,name).use { reopened ->
            val restored=Fixture(reopened).apply { recovered=PendingState.CONFIRMED };mount(restored)
            expect("Cambios guardados");assertTrue(restored.edits.isEmpty());assertTrue(restored.deletes.isEmpty())
            assertEquals(original.operationId,reopened.pendingFor("synthetic-a").single().operationId);unmount()
        }
    }
    @Test fun editDraftSurvivesRecreationWithoutOverwritingOtherDraftNamespaces() = scenario { f,name ->
        f.store.saveDraft(Draft("synthetic-a",JSONObject().put("profileUsername","Player").put("customActivity",JSONObject().put("name","Otra definición")).toString()))
        mount(f);open();change()
        compose.waitUntil(3_000) { f.store.draftFor("synthetic-a")?.content?.contains("45")==true }
        unmount();compose.activityRule.scenario.recreate()
        PendingDatabase.open(context,name).use { reopened ->
            mount(Fixture(reopened));expect("Guardar cambios");compose.onNodeWithText("Minutos").assertTextContains("45")
            val root=JSONObject(reopened.draftFor("synthetic-a")!!.content);assertEquals("Player",root.getString("profileUsername"))
            assertEquals("Otra definición",root.getJSONObject("customActivity").getString("name"));unmount()
        }
    }
    @Test fun authRequiredSuspendsRetryWithoutDiscardingEnvelope() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;f.error=ApiFailureCode.AUTH_REQUIRED;mount(f);open();change();send()
        expect("Volvé a iniciar sesión para resolver la operación");compose.onNodeWithText("Reintentar corrección").assertIsNotEnabled()
        assertEquals(1,f.store.pendingFor("synthetic-a").size);assertTrue(f.retries.isEmpty())
    }
    @Test fun forbiddenSuspendsRetryWithoutClaimingDefinitiveFailure() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;f.error=ApiFailureCode.FORBIDDEN;mount(f);open();change();send()
        expect("Acceso denegado. La operación sigue pendiente.")
        compose.onNodeWithText("Reintentar corrección").assertIsNotEnabled()
        compose.onNodeWithText("Cambios guardados").assertDoesNotExist()
        assertEquals(1,f.store.pendingFor("synthetic-a").size);assertTrue(f.retries.isEmpty())
    }
    @Test fun loadingQueryDoesNotRenderEmptyBeforeSuccessfulReply() = scenario { f,_ ->
        val gate=CompletableDeferred<HistoryPage>();f.loadGate=gate;mount(f);open()
        expect("Cargando historial…");compose.onNodeWithText("No hay registros en este período").assertDoesNotExist()
        gate.complete(HistoryPage(10,"2026-11-11","2026-11-12",emptyList()))
        expect("No hay registros en este período")
    }
    @Test fun offlineDisablesNewCorrectionAndPreservesEditedInputs() = scenario { f,_ ->
        mount(f);open();change();compose.runOnIdle { f.online=false }
        expect("Sin conexión");compose.onNodeWithText("Guardar cambios").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Minutos").assertTextContains("45");assertTrue(f.edits.isEmpty());assertTrue(f.store.pendingFor("synthetic-a").isEmpty())
    }
    @Test fun accountChangeHidesHistoryAndPendingWithoutReassigningThem() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;mount(f);open();change();send();expect("Confirmación pendiente")
        val original=f.store.pendingFor("synthetic-a").single()
        compose.runOnIdle { session.authenticate(SessionToken("synthetic-b","synthetic-b-history-token")) }
        open();expect("No hay registros en este período")
        compose.onNodeWithText("Entrenamiento de fuerza").assertDoesNotExist();compose.onNodeWithText("Confirmación pendiente").assertDoesNotExist()
        assertEquals(original,f.store.pendingFor("synthetic-a").single());assertTrue(f.store.pendingFor("synthetic-b").isEmpty())
    }
    @Test fun latePreviousAccountReadCannotPopulateNewAccountHistory() = scenario { f,_ ->
        val gate=CompletableDeferred<HistoryPage>();f.loadGate=gate;mount(f);open()
        compose.waitUntil(3_000) { f.loads.isNotEmpty() };f.loadGate=null
        compose.runOnIdle { session.authenticate(SessionToken("synthetic-b","synthetic-b-history-token")) }
        open();expect("No hay registros en este período")
        gate.complete(HistoryPage(10,"2026-11-11","2026-11-12",listOf(f.current)))
        compose.waitForIdle();compose.onNodeWithText("Entrenamiento de fuerza").assertDoesNotExist()
    }
}
