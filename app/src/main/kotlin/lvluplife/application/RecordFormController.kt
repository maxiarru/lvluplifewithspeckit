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
    fun start() { mutable.value = mutable.value.copy(busy = true); scope.launch {
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
    fun change(draft: RecordDraft) {
        if (!valid() || mutable.value.busy || mutable.value.pending != null || mutable.value.unresolved) return
        intent = null; mutable.value = mutable.value.copy(draft = draft, message = null)
        saveJob?.cancel()
        saveJob = scope.launch {
            try { delay(100); saves.withLock { if (valid()) records.saveDraft(uid!!, mutable.value.draft) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(message = message(blocked.code)) }
        }
    }
    fun submit() {
        if (!valid() || mutable.value.busy || !records.online || mutable.value.pending != null) return
        val draft = mutable.value.draft
        val id = intent ?: UUID.randomUUID().toString().also { intent = it }
        mutable.value = mutable.value.copy(busy = true, message = null)
        saveJob?.cancel()
        scope.launch {
            try {
                saves.withLock { if (valid()) records.saveDraft(uid!!, draft) }
                if (valid()) receive(records.submit(uid!!, id, draft))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(busy = false,
                unresolved = blocked.code == ApiFailureCode.TEMPORARILY_UNAVAILABLE, message = message(blocked.code)) }
        }
    }
    fun retry() {
        val view = mutable.value
        if (!valid() || view.busy || !records.online || view.pending?.error in setOf(ApiFailureCode.AUTH_REQUIRED, ApiFailureCode.FORBIDDEN)) return
        val pending = view.pending
        if (pending == null) { if (view.unresolved) submit(); return }
        mutable.value = view.copy(busy = true)
        scope.launch {
            try { receive(records.retry(pending.ref)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(busy = false, message = message(blocked.code)) }
        }
    }
    private fun receive(snapshot: OperationSnapshot) {
        if (!valid() || snapshot.ref.uid != uid) return
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
        ApiFailureCode.INVALID_INPUT, ApiFailureCode.INVALID_REQUEST -> "Revisá los datos ingresados."
        ApiFailureCode.DATE_NOT_ALLOWED -> "La fecha ya no está permitida. Revisá el día de la actividad."
        ApiFailureCode.DAILY_DURATION_EXCEEDED -> "No queda duración disponible para esa fecha."
        ApiFailureCode.CYCLE_CLOSED -> "El ciclo cambió. Actualizá antes de volver a cargar."
        else -> "No se pudo completar la solicitud. Conservamos tus datos."
    }
}
