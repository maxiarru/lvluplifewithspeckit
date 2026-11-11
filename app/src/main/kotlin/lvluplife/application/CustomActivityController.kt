package lvluplife.application

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lvluplife.data.*

data class CustomActivityView(val draft: CustomActivityDraft = CustomActivityDraft("", "", "STR"),
    val open: Boolean = false, val busy: Boolean = true, val items: List<CustomActivityOption> = emptyList(),
    val listLoaded: Boolean = false, val listError: Boolean = false, val pending: OperationSnapshot? = null,
    val unresolved: Boolean = false, val message: String? = null, val settlement: Long = 0)

class CustomActivityController(private val session: SessionController, private val activities: CustomActivityFlowActivities,
    private val scope: CoroutineScope) {
    private val uid = session.current()?.uid
    private val epoch = session.state.value.sessionId
    private val mutable = MutableStateFlow(CustomActivityView())
    val state = mutable.asStateFlow()
    private var draftLoaded = false
    private var intent: String? = null
    private var saveJob: Job? = null
    private val draftScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dirty = false
    private var listRequest = 0L
    private val saves = Mutex()
    private fun valid() = uid != null && session.current()?.uid == uid && session.state.value.sessionId == epoch
    fun start() {
        mutable.value = mutable.value.copy(busy = true)
        scope.launch {
            try {
                if (!valid()) return@launch
                if (!draftLoaded) {
                    activities.draft(uid!!)?.let { if (valid()) mutable.value = mutable.value.copy(draft = it) }
                    if (valid()) draftLoaded = true
                }
                val results = activities.pending(uid!!)
                if (!valid()) return@launch
                val result = results.firstOrNull { it.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED) } ?: results.lastOrNull()
                if (result != null) receive(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(message = message(blocked.code)) }
            if (valid()) { mutable.value = mutable.value.copy(busy = false); loadList() }
        }
    }
    fun refreshList() { scope.launch { loadList() } }
    private suspend fun loadList() {
        if (!valid()) return
        val request = ++listRequest
        try {
            val items = activities.list(uid!!)
            if (valid() && request == listRequest) mutable.value = mutable.value.copy(items = items, listLoaded = true, listError = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OperationBlocked) { if (valid() && request == listRequest) mutable.value = mutable.value.copy(listError = true) }
    }
    fun open() { if (!mutable.value.busy) mutable.value = mutable.value.copy(open = true, message = null) }
    fun close() { if (!mutable.value.busy) mutable.value = mutable.value.copy(open = false) }
    fun change(draft: CustomActivityDraft) {
        if (!valid() || mutable.value.busy || mutable.value.pending != null || mutable.value.unresolved) return
        intent = null; dirty = true; mutable.value = mutable.value.copy(draft = draft, message = null)
        saveJob = draftScope.launch {
            try { saves.withLock { if (valid()) activities.saveDraft(uid!!, mutable.value.draft) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(message = message(blocked.code)) }
        }
    }
    fun dispose() {
        if (!dirty || !valid()) { draftScope.cancel(); return }
        val draft = mutable.value.draft
        draftScope.launch {
            try { saves.withLock { if (valid()) activities.saveDraft(uid!!, draft) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(message = message(blocked.code)) }
            finally { draftScope.cancel() }
        }
    }
    fun submit() {
        if (!valid() || mutable.value.busy || mutable.value.pending != null || !activities.online) return
        val draft = mutable.value.draft
        val id = intent ?: UUID.randomUUID().toString().also { intent = it }
        mutable.value = mutable.value.copy(busy = true, message = null)
        saveJob?.cancel()
        scope.launch {
            try {
                saves.withLock { if (valid()) activities.saveDraft(uid!!, draft) }
                if (valid()) receive(activities.submit(uid!!, id, draft))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(busy = false,
                unresolved = blocked.code == ApiFailureCode.TEMPORARILY_UNAVAILABLE, message = message(blocked.code)) }
        }
    }
    fun retry() {
        val view = mutable.value
        if (!valid() || view.busy || !activities.online || view.pending?.error in setOf(ApiFailureCode.AUTH_REQUIRED, ApiFailureCode.FORBIDDEN)) return
        val pending = view.pending
        if (pending == null) { if (view.unresolved) submit(); return }
        mutable.value = view.copy(busy = true)
        scope.launch {
            try { receive(activities.retry(pending.ref)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(busy = false, message = message(blocked.code)) }
        }
    }
    private fun receive(result: OperationSnapshot) {
        if (!valid() || result.ref.uid != uid) return
        mutable.value = when (result.state) {
            PendingState.CONFIRMED -> { intent = null; mutable.value.copy(open = false, busy = false, pending = null, unresolved = false,
                message = "Actividad personalizada guardada", settlement = mutable.value.settlement + 1) }
            PendingState.REJECTED -> { intent = null; mutable.value.copy(open = true, busy = false, pending = null, unresolved = false, message = message(result.error)) }
            else -> mutable.value.copy(open = true, busy = false, pending = result, message = result.error?.let(::message))
        }
    }
    private fun message(code: ApiFailureCode?) = when (code) {
        ApiFailureCode.INVALID_INPUT, ApiFailureCode.INVALID_REQUEST -> "Revisá los datos de la actividad"
        ApiFailureCode.AUTH_REQUIRED -> "Volvé a iniciar sesión para resolver la operación"
        ApiFailureCode.FORBIDDEN -> "Acceso denegado. La operación sigue pendiente."
        else -> "No se pudo completar la solicitud. Conservamos tus datos."
    }
}
