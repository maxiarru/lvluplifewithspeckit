package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** Real private Room and synthetic remote state. No reset engine, HTTP or Firebase in the fixture. */
class ResetFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val session=SessionController()
    private class Remote {
        val applied=java.util.concurrent.ConcurrentHashMap.newKeySet<OperationRef>()
        val cycles=java.util.concurrent.ConcurrentHashMap<String,Int>().apply { put("synthetic-a",1);put("synthetic-b",1) }
        val values=java.util.concurrent.ConcurrentHashMap<String,RecordProgressView>().apply {
            put("synthetic-a",RecordProgressView(10,"cycle-a","2026-11-12",2,"guerrero",1,
                listOf("STR","DEX","CON","INT","WIT","MEN").associateWith { RecordAttributeView(if(it=="STR") 2 else 1,if(it=="STR") 18 else 0,if(it=="STR") 60 else 30) },"Guerrero"))
            put("synthetic-b",initial("cycle-b",10))
        }
        val username="Cuenta_original"
        val customActivities=listOf("Actividad propia")
        val history=listOf("Registro histórico")
        fun apply(ref: OperationRef) { if(applied.add(ref)) {
            cycles[ref.uid]=cycles.getValue(ref.uid)+1
            values[ref.uid]=initial("confirmed-${ref.operationId}",values.getValue(ref.uid).revision+1)
        } }
        companion object {
            fun initial(cycle: String,revision: Long)=RecordProgressView(revision,cycle,"2026-11-12",1,"novato",1,
                listOf("STR","DEX","CON","INT","WIT","MEN").associateWith { RecordAttributeView(1,0,30) },"Novato")
        }
    }
    private class Fixture(val store: PendingStore,val remote: Remote=Remote()) : ResetFlowOperations {
        override var online by mutableStateOf(true)
        var result=PendingState.CONFIRMED
        var error: ApiFailureCode?=null
        var recovered: PendingState?=null
        var gate: CompletableDeferred<Unit>?=null
        var failBeforePreparation=false
        var failRecovery=false
        val submissions=Collections.synchronizedList(mutableListOf<Triple<String,String,String>>())
        val retries=Collections.synchronizedList(mutableListOf<OperationRef>())
        val lookups=Collections.synchronizedList(mutableListOf<OperationRef>())
        val records=object : RecordFlowRecords {
            override val online get()=this@Fixture.online
            override suspend fun catalog(uid: String)=emptyList<RecordOption>()
            override suspend fun draft(uid: String): RecordDraft?=null
            override suspend fun saveDraft(uid: String,draft: RecordDraft) { error("No debe guardar borradores de carga") }
            override suspend fun pending(uid: String)=emptyList<OperationSnapshot>()
            override suspend fun submit(uid: String,intentId: String,draft: RecordDraft): OperationSnapshot=error("No debe crear registros")
            override suspend fun retry(ref: OperationRef): OperationSnapshot=error("No debe reenviar otro tipo de operación")
        }
        override suspend fun pending(uid: String)=withContext(Dispatchers.IO) {
            if(failRecovery) throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE)
            store.pendingFor(uid).filter { it.commandType=="RESET_PROGRESSION" && it.state !in setOf(PendingState.CONFIRMED,PendingState.REJECTED) }.map {
                val ref=OperationRef(uid,it.operationId);lookups+=ref
                val state=if(online) recovered ?: it.state else it.state
                val code=error ?: it.safeError?.let(ApiFailureCode::valueOf)
                store.updateState(uid,it.operationId,state,code?.name)
                if(state==PendingState.CONFIRMED)remote.apply(ref)
                OperationSnapshot(ref,state,code)
            }
        }
        override suspend fun submit(uid: String,intentId: String,cycleId: String,confirmed: Boolean)=withContext(Dispatchers.IO) {
            check(online);check(confirmed);UUID.fromString(intentId)
            submissions+=Triple(uid,intentId,cycleId)
            if(failBeforePreparation)throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE)
            store.prepare(operation(uid,intentId,cycleId))
            gate?.await()
            val ref=OperationRef(uid,intentId)
            store.updateState(uid,intentId,result,error?.name)
            if(result==PendingState.CONFIRMED)remote.apply(ref)
            OperationSnapshot(ref,result,error)
        }
        override suspend fun retry(ref: OperationRef)=withContext(Dispatchers.IO) {
            check(online);retries+=ref
            val row=store.pendingFor(ref.uid).single { it.operationId==ref.operationId }
            check(row.commandType=="RESET_PROGRESSION")
            store.updateState(ref.uid,ref.operationId,result,error?.name)
            if(result==PendingState.CONFIRMED)remote.apply(ref)
            OperationSnapshot(ref,result,error)
        }
        companion object {
            fun operation(uid: String="synthetic-a",id: String=UUID.randomUUID().toString(),cycle: String="cycle-a",type: String="RESET_PROGRESSION") =
                PendingOperation(uid,id,type,JSONObject().put("operationId",id).put("type",type).put("cycleId",cycle)
                    .put("payload",if(type=="CREATE_RECORD") JSONObject().put("activityId","strength").put("activityDate","2026-11-12")
                        .put("hours",0).put("minutes",30) else JSONObject().put("confirmed",true)).toString(),"synthetic-reset-hash",cycle,PendingState.UNKNOWN)
        }
    }
    private fun scenario(block: (Fixture,String) -> Unit) {
        val name="reset-flow-${UUID.randomUUID()}.db";val store=PendingDatabase.open(context,name)
        try { block(Fixture(store),name) } finally { unmount();store.close();context.deleteDatabase(name) }
    }
    private fun unmount() { compose.runOnIdle { compose.activity.setContent {} };compose.waitForIdle() }
    private fun mount(f: Fixture) {
        if(session.current()==null)session.authenticate(SessionToken("synthetic-a","synthetic-reset-token"))
        compose.runOnIdle { compose.activity.setContent { MaterialTheme {
            SessionHost(session) { RecordFlow(session,f.records,RecordFlowProgress { uid -> f.remote.values.getValue(uid) },reset=f) }
        } } }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("character-class").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun expect(text: String) {
        compose.waitUntil(3_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun open()=compose.onNodeWithTag("reset-open").assertExists().performClick()
    private fun confirm()=compose.onNodeWithTag("reset-confirm").assertExists().performClick()
    private fun retry()=compose.onNodeWithTag("reset-retry").assertExists().performClick()
    private fun capture(name: String, dialog: Boolean=false) {
        val root=if(dialog) compose.onNode(isDialog()) else compose.onRoot()
        java.io.File(context.cacheDir,name).outputStream().use {
            root.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun failedStartupRecoveryBlocksNewResetUntilRetryResolvesTheJournal() = scenario { f,_ ->
        f.failRecovery=true;mount(f);expect("Confirmación pendiente")
        compose.onNodeWithTag("reset-open").assertIsNotEnabled()
        assertTrue(f.submissions.isEmpty());assertTrue(f.store.pendingFor("synthetic-a").isEmpty())
        f.failRecovery=false;retry()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Confirmación pendiente").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("reset-open").assertIsEnabled();assertTrue(f.submissions.isEmpty())
    }

    @Test fun openingConfirmationExplainsPreservedDataAndDoesNotPrepareAnOperation() = scenario { f,_ ->
        mount(f);open()
        expect("¿Reiniciar tu personaje?")
        expect("Se reinicia tu progreso. Se conservan tu cuenta, nombre de usuario, actividades personalizadas e historial.")
        capture("t076-confirmation.png",dialog=true)
        assertTrue(f.submissions.isEmpty());assertTrue(f.store.pendingFor("synthetic-a").isEmpty())
        assertEquals(1,f.remote.cycles.getValue("synthetic-a"));assertEquals(18,f.remote.values.getValue("synthetic-a").attributes.getValue("STR").xp)
    }
    @Test fun cancelDoesNotChangeProgressCycleDefinitionsHistoryOrPendingRows() = scenario { f,_ ->
        val before=f.remote.values.getValue("synthetic-a");mount(f);open();compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("¿Reiniciar tu personaje?").assertDoesNotExist()
        assertEquals(before,f.remote.values.getValue("synthetic-a"));assertTrue(f.submissions.isEmpty());assertTrue(f.store.pendingFor("synthetic-a").isEmpty())
        assertEquals("Cuenta_original",f.remote.username);assertEquals(listOf("Actividad propia"),f.remote.customActivities);assertEquals(listOf("Registro histórico"),f.remote.history)
    }
    @Test fun explicitConfirmationAndDoubleTapUseOneDurableUuidAndOnlyOneNewCycle() = scenario { f,_ ->
        f.gate=CompletableDeferred();mount(f);open();confirm()
        compose.waitUntil(3_000) { f.submissions.size==1 }
        compose.onNodeWithTag("reset-open").assertIsNotEnabled()
        val row=f.store.pendingFor("synthetic-a").single();val payload=JSONObject(row.envelope)
        assertTrue(payload.getJSONObject("payload").getBoolean("confirmed"));assertEquals("cycle-a",row.cycleId)
        compose.onNodeWithTag("reset-open").performClick();assertEquals(1,f.submissions.size)
        compose.runOnIdle { f.gate!!.complete(Unit) };expect("Reinicio confirmado")
        assertEquals(2,f.remote.cycles.getValue("synthetic-a"));assertEquals(1,f.submissions.size)
        compose.onNodeWithTag("character-class").assertTextEquals("Novato")
    }
    @Test fun unknownResetKeepsOldProgressUntilRetryConfirmsSameOperation() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;mount(f);open();confirm();expect("Confirmación pendiente")
        compose.onNodeWithText("Reinicio confirmado").assertDoesNotExist();assertEquals(1,f.remote.cycles.getValue("synthetic-a"))
        compose.onNodeWithTag("attribute-STR-xp").assertTextEquals("18 XP")
        capture("t076-pending.png")
        val row=f.store.pendingFor("synthetic-a").single();f.result=PendingState.CONFIRMED;retry();expect("Reinicio confirmado")
        assertEquals(listOf(OperationRef("synthetic-a",row.operationId)),f.retries);assertEquals(1,f.submissions.size)
        assertEquals(row.envelope,f.store.pendingFor("synthetic-a").single().envelope);assertEquals(2,f.remote.cycles.getValue("synthetic-a"))
    }
    @Test fun rejectedResetRequiresAnotherExplicitConfirmationAndNewUuid() = scenario { f,_ ->
        f.result=PendingState.REJECTED;f.error=ApiFailureCode.INVALID_INPUT;mount(f);open();confirm();expect("Reinicio rechazado")
        val old=f.submissions.single().second;assertEquals(1,f.remote.cycles.getValue("synthetic-a"))
        f.result=PendingState.CONFIRMED;f.error=null;open();confirm();expect("Reinicio confirmado")
        assertEquals(2,f.submissions.size);assertNotEquals(old,f.submissions.last().second);assertEquals(2,f.remote.cycles.getValue("synthetic-a"))
    }
    @Test fun confirmationNeverRefreshesOriginalCycleToSilentlyTargetAnotherCycle() = scenario { f,_ ->
        mount(f);open();f.remote.values["synthetic-a"]=Remote.initial("remote-cycle",11);f.remote.cycles["synthetic-a"]=2
        f.result=PendingState.REJECTED;f.error=ApiFailureCode.CYCLE_CLOSED;confirm();expect("Reinicio rechazado")
        assertEquals("cycle-a",f.submissions.single().third);assertEquals("cycle-a",f.store.pendingFor("synthetic-a").single().cycleId)
        assertEquals(1,f.submissions.size);assertEquals(2,f.remote.cycles.getValue("synthetic-a"))
    }
    @Test fun reopenedRoomRecoversOriginalResetWithoutAnotherSubmitOrCycle() = scenario { f,name ->
        f.result=PendingState.UNKNOWN;mount(f);open();confirm();expect("Confirmación pendiente")
        val old=f.store.pendingFor("synthetic-a").single();unmount();f.store.close()
        PendingDatabase.open(context,name).use { reopened ->
            val restored=Fixture(reopened,f.remote).apply { recovered=PendingState.CONFIRMED };mount(restored);expect("Reinicio confirmado")
            assertTrue(restored.submissions.isEmpty());assertEquals(listOf(OperationRef("synthetic-a",old.operationId)),restored.lookups)
            assertEquals(old.envelope,reopened.pendingFor("synthetic-a").single().envelope);assertEquals(2,f.remote.cycles.getValue("synthetic-a"));unmount()
        }
    }
    @Test fun activityRecreationDoesNotPrepareAnotherResetOrLoseItsOriginalIdentity() = scenario { f,name ->
        f.result=PendingState.UNKNOWN;mount(f);open();confirm();expect("Confirmación pendiente")
        val old=f.store.pendingFor("synthetic-a").single();unmount();compose.activityRule.scenario.recreate()
        PendingDatabase.open(context,name).use { reopened ->
            val restored=Fixture(reopened,f.remote).apply { recovered=PendingState.CONFIRMED };mount(restored);expect("Reinicio confirmado")
            assertEquals(old.operationId,reopened.pendingFor("synthetic-a").single().operationId);assertTrue(restored.submissions.isEmpty())
            assertEquals(2,f.remote.cycles.getValue("synthetic-a"));unmount()
        }
    }
    @Test fun authRequiredSuspendsRetryAndRetainsOriginalPendingEnvelope() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;f.error=ApiFailureCode.AUTH_REQUIRED;mount(f);open();confirm()
        expect("Volvé a iniciar sesión para resolver la operación");compose.onNodeWithTag("reset-retry").assertIsNotEnabled()
        assertEquals(1,f.store.pendingFor("synthetic-a").size);assertTrue(f.retries.isEmpty());assertEquals(1,f.remote.cycles.getValue("synthetic-a"))
    }
    @Test fun forbiddenSuspendsRetryWithoutAnnouncingDefinitiveFailure() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;f.error=ApiFailureCode.FORBIDDEN;mount(f);open();confirm()
        expect("Acceso denegado. La operación sigue pendiente.");compose.onNodeWithTag("reset-retry").assertIsNotEnabled()
        compose.onNodeWithText("Reinicio rechazado").assertDoesNotExist();assertTrue(f.retries.isEmpty());assertEquals(1,f.store.pendingFor("synthetic-a").size)
    }
    @Test fun offlineDisablesNewResetWithoutPreparingAnything() = scenario { f,_ ->
        f.online=false;mount(f);expect("Sin conexión");compose.onNodeWithTag("reset-open").assertExists().assertIsNotEnabled()
        assertTrue(f.submissions.isEmpty());assertTrue(f.store.pendingFor("synthetic-a").isEmpty())
    }
    @Test fun accountChangeHidesOtherOwnersPendingAndNeverReassignsItsUuid() = scenario { f,_ ->
        f.result=PendingState.UNKNOWN;mount(f);open();confirm();expect("Confirmación pendiente")
        val old=f.store.pendingFor("synthetic-a").single();val before=f.lookups.size
        compose.runOnIdle { session.authenticate(SessionToken("synthetic-b","synthetic-b-reset-token")) }
        compose.waitUntil(3_000) { compose.onAllNodes(hasTestTag("character-class") and hasText("Novato")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reset-retry").assertDoesNotExist();compose.onNodeWithText("Confirmación pendiente").assertDoesNotExist()
        assertEquals(old,f.store.pendingFor("synthetic-a").single());assertTrue(f.store.pendingFor("synthetic-b").isEmpty())
        assertTrue(f.lookups.drop(before).all { it.uid=="synthetic-b" })
    }
    @Test fun startupRecoversOnlyOwnResetWithoutConsumingCreateOrAnotherOwnersOperation() = scenario { f,_ ->
        val old=Fixture.operation();val create=Fixture.operation(type="CREATE_RECORD");val foreign=Fixture.operation(uid="synthetic-b",cycle="cycle-b")
        f.store.prepare(old);f.store.prepare(create);f.store.prepare(foreign);mount(f);expect("Confirmación pendiente")
        assertEquals(listOf(OperationRef("synthetic-a",old.operationId)),f.lookups)
        assertEquals(2,f.store.pendingFor("synthetic-a").size);assertEquals(1,f.store.pendingFor("synthetic-b").size)
        assertTrue(f.submissions.isEmpty())
    }
    @Test fun pendingOldCycleRejectionIsNotResubmittedAgainstNewlyObservedCycle() = scenario { f,_ ->
        val old=Fixture.operation();f.store.prepare(old);f.remote.values["synthetic-a"]=Remote.initial("remote-cycle",11);f.remote.cycles["synthetic-a"]=2
        mount(f);expect("Confirmación pendiente");f.result=PendingState.REJECTED;f.error=ApiFailureCode.CYCLE_CLOSED;retry();expect("Reinicio rechazado")
        assertEquals(listOf(OperationRef("synthetic-a",old.operationId)),f.retries);assertTrue(f.submissions.isEmpty())
        assertEquals(old.envelope,f.store.pendingFor("synthetic-a").single().envelope);assertEquals(2,f.remote.cycles.getValue("synthetic-a"))
    }
    @Test fun failedLocalPreparationRetriesSameConfirmedIntentAndOriginalCycle() = scenario { f,_ ->
        f.failBeforePreparation=true;mount(f);open();confirm();expect("No se pudo preparar el reinicio")
        assertTrue(f.store.pendingFor("synthetic-a").isEmpty());assertEquals(1,f.submissions.size)
        f.failBeforePreparation=false;retry();expect("Reinicio confirmado")
        assertEquals(2,f.submissions.size);assertEquals(f.submissions.first(),f.submissions.last())
        assertEquals(1,f.store.pendingFor("synthetic-a").size);assertEquals(2,f.remote.cycles.getValue("synthetic-a"))
    }
    @Test fun savedResetEnvelopeRetainsUuidCycleAndExplicitConfirmationAcrossRoomClose() {
        val name="reset-storage-${UUID.randomUUID()}.db";val operation=Fixture.operation()
        try {
            PendingDatabase.open(context,name).use { it.prepare(operation) }
            PendingDatabase.open(context,name).use {
                assertEquals(operation,it.pendingFor("synthetic-a").single())
                assertTrue(it.pendingFor("synthetic-b").isEmpty())
                val value=JSONObject(it.pendingFor("synthetic-a").single().envelope)
                assertEquals(operation.operationId,value.getString("operationId"));assertEquals("cycle-a",value.getString("cycleId"))
                assertTrue(value.getJSONObject("payload").getBoolean("confirmed"))
            }
        } finally { context.deleteDatabase(name) }
    }
}
