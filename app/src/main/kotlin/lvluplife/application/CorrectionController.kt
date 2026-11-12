package lvluplife.application

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lvluplife.data.*

data class CorrectionView(val draft:CorrectionDraft?=null,val busy:Boolean=true,val ready:Boolean=false,val pending:OperationSnapshot?=null,
    val unresolved:Boolean=false,val confirmingDelete:HistoryItem?=null,val options:List<RecordOption> = emptyList(),
    val message:String?=null,val settlement:Long=0)

class CorrectionController(private val session:SessionController,private val corrections:HistoryFlowCorrections,private val scope:CoroutineScope) {
    private val uid=session.current()?.uid
    private val epoch=session.state.value.sessionId
    private val mutable=MutableStateFlow(CorrectionView())
    val state=mutable.asStateFlow()
    private var loaded=false
    private var intent:String?=null
    private var deletion=false
    private var deleting:HistoryItem?=null
    private var dirty=false
    @Volatile private var disposed=false
    private val saves=Mutex()
    private val draftScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private fun sessionValid()=uid != null && session.current()?.uid == uid && session.state.value.sessionId == epoch
    private fun valid()=!disposed && sessionValid()
    fun start() {
        mutable.value=mutable.value.copy(busy=true)
        scope.launch(Dispatchers.Main.immediate) {
            try {
                if(!valid()) return@launch
                if(!loaded) {
                    val draft=corrections.draft(uid!!)
                    if(valid()) { mutable.value=mutable.value.copy(draft=draft);loaded=true }
                }
                val results=corrections.pending(uid!!)
                if(!valid()) return@launch
                val result=results.firstOrNull { it.state !in setOf(PendingState.CONFIRMED,PendingState.REJECTED) } ?: results.lastOrNull()
                if(result != null) { deletion=corrections.wasDeletion(result.ref);receive(result) }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(blocked:OperationBlocked) { if(valid()) mutable.value=mutable.value.copy(message=message(blocked.code)) }
            if(valid()) { mutable.value=mutable.value.copy(busy=false,ready=true);loadOptions() }
        }
    }
    private fun loadOptions() {
        val draft=mutable.value.draft ?: return
        if(draft.hours == null) return
        scope.launch(Dispatchers.Main.immediate) {
            try { val options=corrections.options(uid ?: return@launch)
                if(valid() && mutable.value.draft?.recordId == draft.recordId) mutable.value=mutable.value.copy(options=options.filter { it.duration })
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(_:OperationBlocked) { /* Keep the original activity available; submission is still validated remotely. */ }
        }
    }
    fun open(item:HistoryItem) {
        val view=mutable.value
        if(!valid() || view.busy || view.pending != null || view.unresolved || !item.canCorrect) return
        intent=null;deletion=false;dirty=false
        mutable.value=view.copy(draft=CorrectionDraft(item.id,item.cycleId,item.version,item.activityId,item.date,
            item.durationMinutes?.let { (it/60).toString() },item.durationMinutes?.let { (it%60).toString() }),message=null,
            options=listOf(RecordOption(item.activityId,item.name,item.durationMinutes != null)))
        loadOptions()
    }
    fun close() { if(!mutable.value.busy && !mutable.value.unresolved) mutable.value=mutable.value.copy(draft=null) }
    fun change(draft:CorrectionDraft) {
        if(!valid() || mutable.value.busy || mutable.value.pending != null || mutable.value.unresolved) return
        intent=null;dirty=true;mutable.value=mutable.value.copy(draft=draft,message=null)
        draftScope.launch {
            try { saves.withLock { if(valid() && dirty) mutable.value.draft?.let { corrections.saveDraft(uid!!,it) } } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(blocked:OperationBlocked) { withContext(Dispatchers.Main.immediate) {
                if(valid()) mutable.value=mutable.value.copy(message=message(blocked.code))
            } }
        }
    }
    fun dispose() {
        disposed=true
        val draft=mutable.value.draft
        if(!dirty || draft == null || !sessionValid()) { draftScope.cancel();return }
        draftScope.launch {
            try { saves.withLock { if(sessionValid()) corrections.saveDraft(uid!!,draft) } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { /* Retain the last durable draft; never publish state after disposal. */ }
            finally { draftScope.cancel() }
        }
    }
    fun submit() {
        val view=mutable.value;val draft=view.draft ?: return
        if(!valid() || view.busy || view.pending != null || !corrections.online) return
        val id=intent ?: UUID.randomUUID().toString().also { intent=it };deletion=false
        mutable.value=view.copy(busy=true,message=null)
        scope.launch(Dispatchers.Main.immediate) {
            try { saves.withLock { if(valid()) corrections.saveDraft(uid!!,draft) };if(valid()) receive(corrections.edit(uid!!,id,draft)) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(blocked:OperationBlocked) { failed(blocked.code) }
        }
    }
    fun askDelete(item:HistoryItem) {
        val view=mutable.value
        if(valid() && !view.busy && view.pending == null && !view.unresolved && item.canCorrect) {
            intent=null;deleting=null
            mutable.value=view.copy(confirmingDelete=item,message=null)
        }
    }
    fun cancelDelete() { mutable.value=mutable.value.copy(confirmingDelete=null) }
    fun delete() {
        val view=mutable.value;val item=view.confirmingDelete ?: deleting.takeIf { view.unresolved && deletion } ?: return
        if(!valid() || view.busy || view.pending != null || !corrections.online) return
        val id=intent ?: UUID.randomUUID().toString().also { intent=it };deletion=true;deleting=item
        mutable.value=view.copy(busy=true,confirmingDelete=null,message=null)
        scope.launch(Dispatchers.Main.immediate) {
            try { receive(corrections.delete(uid!!,id,item)) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(blocked:OperationBlocked) { failed(blocked.code) }
        }
    }
    fun retry() {
        val view=mutable.value
        if(!valid() || view.busy || !corrections.online || view.pending?.error in setOf(ApiFailureCode.AUTH_REQUIRED,ApiFailureCode.FORBIDDEN)) return
        val pending=view.pending
        if(pending == null) { if(view.unresolved) { if(deletion) delete() else submit() };return }
        mutable.value=view.copy(busy=true)
        scope.launch(Dispatchers.Main.immediate) {
            try { receive(corrections.retry(pending.ref)) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(blocked:OperationBlocked) { failed(blocked.code) }
        }
    }
    private fun failed(code:ApiFailureCode) { if(valid()) mutable.value=mutable.value.copy(busy=false,
        unresolved=code==ApiFailureCode.TEMPORARILY_UNAVAILABLE || mutable.value.unresolved,message=message(code)) }
    private fun discardDraft() {
        dirty=false
        scope.launch(Dispatchers.Main.immediate) { try { saves.withLock { if(valid()) corrections.clearDraft(uid!!) } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(blocked:OperationBlocked) { if(valid()) mutable.value=mutable.value.copy(message=message(blocked.code)) }
        }
    }
    private fun receive(result:OperationSnapshot) {
        if(!valid() || result.ref.uid != uid) return
        mutable.value=when(result.state) {
            PendingState.CONFIRMED -> { intent=null;discardDraft();mutable.value.copy(draft=null,busy=false,pending=null,unresolved=false,
                message=if(deletion) "Registro eliminado" else "Cambios guardados",settlement=mutable.value.settlement+1) }
            PendingState.REJECTED -> {
                intent=null
                val close=result.error in setOf(ApiFailureCode.RECORD_STALE,ApiFailureCode.RECORD_DELETED,ApiFailureCode.CYCLE_CLOSED,ApiFailureCode.EDIT_WINDOW_EXPIRED)
                if(close) discardDraft()
                mutable.value.copy(draft=mutable.value.draft.takeUnless { close },busy=false,pending=null,unresolved=false,
                    message=message(result.error),settlement=mutable.value.settlement+1)
            }
            else -> mutable.value.copy(busy=false,pending=result,message=result.error?.let(::message))
        }
    }
    private fun message(code:ApiFailureCode?)=when(code) {
        ApiFailureCode.RECORD_STALE -> "El registro cambió. Revisá los datos actuales."
        ApiFailureCode.RECORD_DELETED -> "El registro ya no existe"
        ApiFailureCode.CYCLE_CLOSED -> "El ciclo cambió. Actualizá antes de corregir."
        ApiFailureCode.EDIT_WINDOW_EXPIRED -> "Finalizó el plazo para corregir el registro"
        ApiFailureCode.INVALID_INPUT,ApiFailureCode.INVALID_REQUEST -> "Revisá los datos de la corrección"
        ApiFailureCode.DATE_NOT_ALLOWED -> "La fecha ya no está permitida. Revisá el día de la actividad."
        ApiFailureCode.DAILY_DURATION_EXCEEDED -> "No queda duración disponible para esa fecha."
        ApiFailureCode.AUTH_REQUIRED -> "Volvé a iniciar sesión para resolver la operación"
        ApiFailureCode.FORBIDDEN -> "Acceso denegado. La operación sigue pendiente."
        else -> "No se pudo completar la solicitud. Conservamos tus datos."
    }
}
