package lvluplife.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.application.*

class CorrectionRepository(private val api: ApiClient, private val operations: OperationCoordinator,
    private val store: PendingStore, private val sessions: SessionTokenProvider, private val connected: () -> Boolean,
    private val records: RecordFlowRecords) : HistoryFlowCorrections {
    private val json = ObjectMapper()
    private val recovered = java.util.concurrent.ConcurrentHashMap<OperationRef,OperationSnapshot>()
    private val types = java.util.concurrent.ConcurrentHashMap<OperationRef,String>()
    override val online get() = connected()
    private fun owner(uid:String) { if(sessions.current()?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
    private fun <T> local(block:()->T):T = try { block() }
    catch(blocked:OperationBlocked) { throw blocked }
    catch(cancelled:java.util.concurrent.CancellationException) { throw cancelled }
    catch(_:Exception) { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }
    private fun rows(uid:String) = local { store.pendingFor(uid) }.filter { it.commandType in correctionTypes }.also {
        it.forEach { row -> types[OperationRef(uid,row.operationId)] = row.commandType }
    }
    fun recoveryIds(uid:String):Set<String> { owner(uid);return rows(uid).map { it.operationId }.toSet() }
    fun rememberRecovered(results:List<OperationSnapshot>) {
        results.filter { it.ref.uid == sessions.current()?.uid }.forEach { recovered[it.ref]=it }
    }
    fun clearMemory() { recovered.clear();types.clear() }
    override fun wasDeletion(ref:OperationRef) = types[ref] == ApiCommandType.DELETE_RECORD.name
    override suspend fun options(uid:String):List<RecordOption> {
        owner(uid)
        return records.catalog(uid).filter { it.duration }
    }
    override suspend fun draft(uid:String):CorrectionDraft? = withContext(Dispatchers.IO) {
        owner(uid)
        local { store.draftFor(uid) }?.content?.let { raw -> runCatching {
            val node=json.readTree(raw).path("correctionDraft")
            if(!node.isObject) null else CorrectionDraft(node.path("recordId").asText(),node.path("cycleId").asText(),node.path("expectedVersion").longValue(),
                node.path("activityId").asText(),node.path("date").asText(),node.path("hours").takeIf { it.isTextual }?.textValue(),node.path("minutes").takeIf { it.isTextual }?.textValue())
        }.getOrNull() }
    }
    private fun updateDraft(uid:String, draft:CorrectionDraft?) {
        owner(uid)
        local { synchronized(store) {
            val previous=store.draftFor(uid)?.content
            val root=previous?.let { runCatching { json.readTree(it) as? ObjectNode }.getOrNull() }
                ?: json.createObjectNode().also { if(previous != null) it.put("previousDraft",previous) }
            if(draft == null) root.remove("correctionDraft") else root.set<JsonNode>("correctionDraft",json.valueToTree(draft))
            owner(uid);store.saveDraft(Draft(uid,json.writeValueAsString(root)))
        } }
    }
    override suspend fun saveDraft(uid:String,draft:CorrectionDraft):Unit = withContext(Dispatchers.IO) { updateDraft(uid,draft) }
    override suspend fun clearDraft(uid:String):Unit = withContext(Dispatchers.IO) { updateDraft(uid,null) }
    override suspend fun pending(uid:String):List<OperationSnapshot> = withContext(Dispatchers.IO) {
        owner(uid);rows(uid)
        val startup=recovered.values.filter { it.ref.uid == uid }
        val live=if(online) operations.recoverFor(ApiCommandType.EDIT_RECORD)+operations.recoverFor(ApiCommandType.DELETE_RECORD) else emptyList()
        owner(uid)
        val remaining=rows(uid).map { OperationSnapshot(OperationRef(uid,it.operationId),it.state,it.safeError?.let(ApiFailureCode::valueOf)) }
        startup.forEach { recovered.remove(it.ref,it) }
        (live+startup+remaining).distinctBy { it.ref }
    }
    private fun identity(recordId:String,cycleId:String,version:Long) {
        if(recordId.isBlank() || '/' in recordId || cycleId.isBlank() || '/' in cycleId || version <= 0) throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
    }
    override suspend fun edit(uid:String,intentId:String,draft:CorrectionDraft):OperationSnapshot = withContext(Dispatchers.IO) {
        owner(uid);if(!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        identity(draft.recordId,draft.cycleId,draft.expectedVersion)
        try { LocalDate.parse(draft.date) } catch(_:Exception) { throw OperationBlocked(ApiFailureCode.INVALID_INPUT) }
        val payload=linkedMapOf<String,Any?>("recordId" to draft.recordId,"expectedVersion" to draft.expectedVersion,"activityDate" to draft.date)
        if(draft.activityId == "meal") {
            if(draft.hours != null || draft.minutes != null) throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
        } else {
            val hours=draft.hours?.toLongOrNull();val minutes=draft.minutes?.toLongOrNull()
            if(hours == null || minutes == null || hours !in 0..24 || minutes !in 0..59 || hours == 0L && minutes == 0L || hours == 24L && minutes != 0L)
                throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
            payload["activityId"]=draft.activityId;payload["hours"]=hours;payload["minutes"]=minutes
        }
        val ref=operations.prepare(uid,intentId,ApiCommandType.EDIT_RECORD,payload,draft.cycleId)
        types[ref]=ApiCommandType.EDIT_RECORD.name;operations.submit(ref)
    }
    override suspend fun delete(uid:String,intentId:String,item:HistoryItem):OperationSnapshot = withContext(Dispatchers.IO) {
        owner(uid);if(!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        identity(item.id,item.cycleId,item.version)
        val ref=operations.prepare(uid,intentId,ApiCommandType.DELETE_RECORD,mapOf("recordId" to item.id,"expectedVersion" to item.version),item.cycleId)
        types[ref]=ApiCommandType.DELETE_RECORD.name;operations.submit(ref)
    }
    override suspend fun retry(ref:OperationRef):OperationSnapshot = withContext(Dispatchers.IO) { owner(ref.uid);operations.retry(ref) }
    companion object { private val correctionTypes=setOf(ApiCommandType.EDIT_RECORD.name,ApiCommandType.DELETE_RECORD.name) }
}
