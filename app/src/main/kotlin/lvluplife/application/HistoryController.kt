package lvluplife.application

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lvluplife.data.*

data class HistoryView(val selection: HistorySelection = HistorySelection(HistoryPeriod.day,LocalDate.now(ZoneOffset.ofHours(-3)).toString()),
    val dateInput: String = selection.date, val page: HistoryPage? = null, val loading: Boolean = true,
    val error: ApiFailureCode? = null, val stale: Boolean = false)

class HistoryController(private val session: SessionController, private val history: HistoryFlowHistory, private val scope: CoroutineScope) {
    private val uid = session.current()?.uid
    private val epoch = session.state.value.sessionId
    private val mutable = MutableStateFlow(HistoryView())
    val state = mutable.asStateFlow()
    private var request = 0L
    private var job: Job? = null
    private val revisions = mutableMapOf<HistorySelection,Long>()
    private fun valid(id: Long) = uid != null && request == id && session.current()?.uid == uid && session.state.value.sessionId == epoch
    fun date(value: String) { mutable.value = mutable.value.copy(dateInput = value) }
    fun period(value: HistoryPeriod) { query(HistorySelection(value,mutable.value.dateInput)) }
    fun refresh() { query(mutable.value.selection) }
    fun consult() { query(HistorySelection(mutable.value.selection.period,mutable.value.dateInput)) }
    private fun query(selection: HistorySelection) {
        val id = ++request
        job?.cancel()
        val same = selection == mutable.value.selection
        val previous = mutable.value.page.takeIf { same }
        mutable.value = mutable.value.copy(selection = selection, page = previous, loading = true, error = null, stale = previous != null)
        job = scope.launch {
            try {
                try { LocalDate.parse(selection.date) } catch (_: Exception) { throw OperationBlocked(ApiFailureCode.INVALID_INPUT) }
                val page = history.load(uid ?: return@launch, selection)
                if (!valid(id)) return@launch
                if (page.revision < (revisions[selection] ?: 0)) throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
                revisions[selection] = page.revision
                mutable.value = mutable.value.copy(page = page, loading = false, error = null, stale = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (blocked: OperationBlocked) { if (valid(id)) mutable.value = mutable.value.copy(loading = false, error = blocked.code, stale = previous != null) }
        }
    }
}
