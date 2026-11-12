package lvluplife.application

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lvluplife.data.*

data class ResetView(val busy: Boolean = true, val ready: Boolean = false, val dialogCycle: String? = null,
    val pending: OperationSnapshot? = null, val unresolved: Boolean = false, val error: ApiFailureCode? = null,
    val message: String? = null, val settlement: Long = 0)

class ResetController(private val session: SessionController, private val operations: ResetFlowOperations,
    private val scope: CoroutineScope) {
    private val uid = session.current()?.uid
    private val epoch = session.state.value.sessionId
    private val mutable = MutableStateFlow(ResetView())
    val state = mutable.asStateFlow()
    private var intent: String? = null
    private var confirmedCycle: String? = null
    private var job: Job? = null
    private var disposed = false
    private fun valid() = !disposed && uid != null && session.current()?.uid == uid && session.state.value.sessionId == epoch
    fun start() {
        if (!valid() || job?.isActive == true) return
        mutable.value = mutable.value.copy(busy = true)
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val results = operations.pending(uid!!)
                if (!valid()) return@launch
                val result = results.firstOrNull { it.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED) } ?: results.lastOrNull()
                if (result != null) receive(result)
                else if (intent == null) mutable.value=mutable.value.copy(pending=null,unresolved=false,error=null,message=null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid()) mutable.value = mutable.value.copy(unresolved=true,error=blocked.code,message=message(blocked.code)) }
            if (valid()) mutable.value = mutable.value.copy(busy=false,ready=true)
        }
    }
    fun open(cycleId: String?) {
        val view = mutable.value
        if (!valid() || !view.ready || view.busy || view.pending != null || view.unresolved || !operations.online || cycleId.isNullOrBlank()) return
        mutable.value = view.copy(dialogCycle=cycleId,message=null,error=null)
    }
    fun cancel() { if (valid()) mutable.value = mutable.value.copy(dialogCycle=null) }
    fun confirm() {
        val view = mutable.value
        val cycle = view.dialogCycle ?: return
        if (!valid() || view.busy || !operations.online) return
        confirmedCycle = cycle
        intent = UUID.randomUUID().toString()
        submit()
    }
    private fun submit() {
        val cycle = confirmedCycle ?: return
        val id = intent ?: return
        mutable.value = mutable.value.copy(busy=true,dialogCycle=null,message=null,error=null)
        job = scope.launch(Dispatchers.Main.immediate) {
            try { receive(operations.submit(uid!!,id,cycle,true)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { failed(blocked.code) }
        }
    }
    fun retry() {
        val view = mutable.value
        if (!valid() || view.busy || !operations.online || view.error in suspended) return
        val pending = view.pending
        if (pending == null) { if (view.unresolved) { if (intent == null) start() else submit() }; return }
        mutable.value = view.copy(busy=true,message=null)
        job = scope.launch(Dispatchers.Main.immediate) {
            try { receive(operations.retry(pending.ref)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { failed(blocked.code) }
        }
    }
    private fun failed(code: ApiFailureCode) {
        if (valid()) mutable.value = mutable.value.copy(busy=false,unresolved=true,error=code,
            message=if (mutable.value.pending == null && code !in suspended) "No se pudo preparar el reinicio" else message(code))
    }
    private fun receive(result: OperationSnapshot) {
        if (!valid() || result.ref.uid != uid) return
        val definitive = result.state in setOf(PendingState.CONFIRMED,PendingState.REJECTED)
        if (definitive) { intent=null; confirmedCycle=null; session.settlePending(result,epoch) }
        mutable.value = if (definitive) mutable.value.copy(busy=false,pending=null,unresolved=false,error=null,
            message=if(result.state == PendingState.CONFIRMED) "Reinicio confirmado" else "Reinicio rechazado",
            settlement=mutable.value.settlement+1)
        else mutable.value.copy(busy=false,pending=result,unresolved=false,error=result.error,message=result.error?.let(::message))
    }
    fun dispose() { disposed=true; job?.cancel() }
    private fun message(code: ApiFailureCode) = when(code) {
        ApiFailureCode.AUTH_REQUIRED -> "Volvé a iniciar sesión para resolver la operación"
        ApiFailureCode.FORBIDDEN -> "Acceso denegado. La operación sigue pendiente."
        else -> "No se pudo resolver el reinicio. Conservamos tu progreso confirmado."
    }
    companion object { val suspended = setOf(ApiFailureCode.AUTH_REQUIRED,ApiFailureCode.FORBIDDEN) }
}
