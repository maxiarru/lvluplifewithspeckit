package lvluplife.application

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lvluplife.data.ApiFailureCode

data class ProgressView(val value: RecordProgressView? = null, val loading: Boolean = true,
    val error: ApiFailureCode? = null, val stale: Boolean = false)

/** Session-owned display state. Neither receipt projections nor previous account data are accepted here. */
class ProgressController(private val session: SessionController, private val progress: RecordFlowProgress,
    private val scope: CoroutineScope) {
    private val uid = session.current()?.uid
    private val epoch = session.state.value.sessionId
    private val mutable = MutableStateFlow(ProgressView())
    val state = mutable.asStateFlow()
    private var request = 0L
    private var job: Job? = null
    private fun valid(id: Long) = uid != null && request == id && session.current()?.uid == uid && session.state.value.sessionId == epoch
    fun refresh() {
        val id = ++request
        job?.cancel()
        mutable.value = mutable.value.copy(loading = true, stale = mutable.value.value != null, error = null)
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val next = progress.load(uid ?: return@launch)
                if (!valid(id)) return@launch
                val current = mutable.value.value
                if (current != null && next.revision < current.revision)
                    mutable.value = mutable.value.copy(loading = false, stale = true, error = ApiFailureCode.UNREADABLE_RESPONSE)
                else mutable.value = ProgressView(next, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) {
                if (valid(id)) mutable.value = mutable.value.copy(loading = false, error = blocked.code, stale = mutable.value.value != null)
            }
        }
    }
}
