package lvluplife.application

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lvluplife.data.*

data class RecordFormView(val draft: RecordDraft = RecordDraft("strength", LocalDate.now(ZoneOffset.ofHours(-3)).toString(), "0", ""),
    val options: List<RecordOption> = emptyList(), val open: Boolean = false, val busy: Boolean = true,
    val pending: OperationSnapshot? = null, val unresolved: Boolean = false, val message: String? = null, val settlement: Long = 0)

class RecordFormController(private val session: SessionController, private val records: RecordFlowRecords,
    private val scope: CoroutineScope) {
    private val uid = session.current()?.uid
    private val epoch = session.state.value.sessionId
    private val mutable = MutableStateFlow(RecordFormView())
    val state = mutable.asStateFlow()
    private var intent: String? = null
    private var saveJob: Job? = null
    private var draftLoaded = false
    private val saves = Mutex()
    private fun valid() = uid != null && session.current()?.uid == uid && session.state.value.sessionId == epoch
    fun start() { mutable.value = mutable.value.copy(busy = true); scope.launch(Dispatchers.Main.immediate) {
        var recovered: OperationSnapshot? = null
        try {
            if (!valid()) return@launch
            if (!draftLoaded) {
                records.draft(uid!!)?.let { if (valid()) mutable.value = mutable.value.copy(draft = it) }
                if (valid()) draftLoaded = true
            }
            val results = records.pending(uid!!)
            recovered = results.firstOrNull { it.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED) } ?: results.lastOrNull()
            val options = records.catalog(uid!!)
            if (!valid()) return@launch
            mutable.value = mutable.value.copy(options = options)
            mutable.value = mutable.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(busy = false, message = message(blocked.code)) }
        recovered?.let(::receive)
    } }
    fun open() { if (!mutable.value.busy && mutable.value.pending == null && !mutable.value.unresolved)
        mutable.value = mutable.value.copy(open = true, message = null) }
    fun close() { if (!mutable.value.busy) mutable.value = mutable.value.copy(open = false) }
    fun refreshCatalog() { scope.launch(Dispatchers.Main.immediate) {
        try { val options = records.catalog(uid ?: return@launch)
            if (valid()) mutable.value = mutable.value.copy(options = options)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(message = message(blocked.code)) }
    } }
    fun change(draft: RecordDraft) {
        if (!valid() || mutable.value.busy || mutable.value.pending != null || mutable.value.unresolved) return
        intent = null; mutable.value = mutable.value.copy(draft = draft, message = null)
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.Main.immediate) {
            try { delay(100); saves.withLock { if (valid()) records.saveDraft(uid!!, mutable.value.draft) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(message = message(blocked.code)) }
        }
    }
    fun submit() {
        if (!valid() || mutable.value.busy || !records.online || mutable.value.pending != null) return
        val id = intent ?: UUID.randomUUID().toString().also { intent = it }
        saveJob?.cancel()
        send(Send(id, mutable.value.draft, persistDraft = true))
    }
    /** Quick record (FR-015/FR-016): its own intent and values; the form draft is left untouched. */
    fun quick(activityId: String, minutes: Int?, date: String) {
        val view=mutable.value
        if(!valid() || view.busy || view.pending != null || view.unresolved || !records.online) return
        val option=view.options.firstOrNull { it.id==activityId } ?: return
        if(option.duration && (minutes == null || minutes !in 1..1440) || !option.duration && minutes != null) return
        send(Send(UUID.randomUUID().toString(),
            RecordDraft(activityId,date,minutes?.let { (it/60).toString() } ?: "0",minutes?.let { (it%60).toString() } ?: ""),
            persistDraft = false))
    }
    /** One submission: intent, values and whether they are the form draft that must be saved first. */
    private data class Send(val intent: String, val draft: RecordDraft, val persistDraft: Boolean)
    /** Kept while the result is unknown so a retry resends exactly the same intent and values. */
    private var unresolvedSend: Send? = null
    private fun send(item: Send) {
        mutable.value = mutable.value.copy(busy = true, message = null)
        scope.launch(Dispatchers.Main.immediate) {
            try {
                if (item.persistDraft) saves.withLock { if (valid()) records.saveDraft(uid!!, item.draft) }
                if (valid()) receive(records.submit(uid!!, item.intent, item.draft))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) {
                val unresolved = blocked.code == ApiFailureCode.TEMPORARILY_UNAVAILABLE
                unresolvedSend = item.takeIf { unresolved }
                mutable.value = mutable.value.copy(busy = false, unresolved = unresolved, message = message(blocked.code))
            } }
        }
    }
    fun retry() {
        val view = mutable.value
        if (!valid() || view.busy || !records.online || view.pending?.error in setOf(ApiFailureCode.AUTH_REQUIRED, ApiFailureCode.FORBIDDEN)) return
        val pending = view.pending
        if (pending == null) { if (view.unresolved) unresolvedSend?.let(::send) ?: submit(); return }
        mutable.value = view.copy(busy = true)
        scope.launch(Dispatchers.Main.immediate) {
            try { receive(records.retry(pending.ref)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(busy = false, message = message(blocked.code)) }
        }
    }
    private fun receive(snapshot: OperationSnapshot) {
        if (!valid() || snapshot.ref.uid != uid) return
        unresolvedSend = null
        mutable.value = when (snapshot.state) {
            PendingState.CONFIRMED -> { intent = null; mutable.value.copy(busy = false, open = false, pending = null, unresolved = false,
                message = "Actividad guardada", settlement = mutable.value.settlement + 1) }
            PendingState.REJECTED -> { intent = null; mutable.value.copy(busy = false, open = snapshot.error != ApiFailureCode.CYCLE_CLOSED,
                options = if (snapshot.error == ApiFailureCode.CYCLE_CLOSED) emptyList() else mutable.value.options,
                pending = null, unresolved = false, message = message(snapshot.error), settlement = mutable.value.settlement + 1) }
            else -> mutable.value.copy(busy = false, open = false, pending = snapshot, message = snapshot.error?.let(::message) ?: mutable.value.message)
        }
    }
    private fun message(code: ApiFailureCode?): String = when (code) {
        ApiFailureCode.AUTH_REQUIRED -> "Volvé a iniciar sesión para resolver la operación"
        ApiFailureCode.FORBIDDEN -> "Acceso denegado. La operación sigue pendiente."
        ApiFailureCode.INVALID_INPUT -> if (mutable.value.options.firstOrNull { it.id == mutable.value.draft.activityId }?.duration == true)
            "Revisá la fecha y la duración: mayor que cero, hasta 24 horas y minutos entre 0 y 59."
            else "Revisá los datos ingresados."
        ApiFailureCode.INVALID_REQUEST -> "Revisá los datos ingresados."
        ApiFailureCode.DATE_NOT_ALLOWED -> "La fecha ya no está permitida. Revisá el día de la actividad."
        ApiFailureCode.DAILY_DURATION_EXCEEDED -> "No queda duración disponible para esa fecha."
        ApiFailureCode.CYCLE_CLOSED -> "El ciclo cambió. Actualizá antes de volver a cargar."
        else -> "No se pudo completar la solicitud. Conservamos tus datos."
    }
}
