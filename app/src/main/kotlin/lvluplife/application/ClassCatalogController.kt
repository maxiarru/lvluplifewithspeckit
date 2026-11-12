package lvluplife.application

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lvluplife.data.ApiFailureCode

data class ClassCatalogView(val entries: List<ClassCatalogEntry>? = null, val loading: Boolean = true,
    val error: ApiFailureCode? = null, val stale: Boolean = false)

class ClassCatalogController(private val session: SessionController, private val records: RecordFlowRecords,
    private val scope: CoroutineScope) {
    private val uid=session.current()?.uid
    private val epoch=session.state.value.sessionId
    private val mutable=MutableStateFlow(ClassCatalogView())
    val state=mutable.asStateFlow()
    private var request=0L
    private var job: Job?=null
    private fun valid(id: Long)=id==request && uid!=null && session.current()?.uid==uid && session.state.value.sessionId==epoch
    fun dispose() { ++request; job?.cancel() }
    fun refresh() {
        val id=++request; job?.cancel()
        mutable.value=mutable.value.copy(loading=true,error=null)
        job=scope.launch(Dispatchers.Main.immediate) {
            try {
                val entries=records.classes(uid ?: return@launch)
                if(valid(id)) mutable.value=ClassCatalogView(entries,loading=false)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(blocked: OperationBlocked) {
                if(valid(id)) mutable.value=mutable.value.copy(loading=false,error=blocked.code,stale=mutable.value.entries!=null)
            }
        }
    }
}
