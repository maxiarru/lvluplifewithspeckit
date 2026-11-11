package lvluplife.data

import lvluplife.application.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class RecordRepository(private val api: ApiClient, private val operations: OperationCoordinator,
    private val store: PendingStore, private val sessions: SessionTokenProvider,
    private val connected: () -> Boolean, private val custom: CustomActivityFlowActivities? = null) : RecordFlowRecords {
    override val online get() = connected()
    private val json = ObjectMapper()
    private data class Context(val cycle: String, val options: List<RecordOption>)
    private val contexts = java.util.concurrent.ConcurrentHashMap<String, Context>()
    private val recoveredAtStartup = java.util.concurrent.ConcurrentHashMap<OperationRef, OperationSnapshot>()
    fun rememberRecovered(results: List<OperationSnapshot>) {
        results.filter { it.ref.uid == sessions.current()?.uid }.forEach { recoveredAtStartup[it.ref] = it }
    }
    fun clearMemory() { contexts.clear(); recoveredAtStartup.clear() }
    private fun owner(uid: String) { if (sessions.current()?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
    private fun <T> local(block: () -> T): T = try { block() }
    catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
    catch (_: Exception) { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }
    fun recoveryIds(uid: String): Set<String> {
        owner(uid)
        return local { store.pendingFor(uid) }.filter { it.commandType == ApiCommandType.CREATE_RECORD.name }.map { it.operationId }.toSet()
    }
    private fun query(uid: String, query: ApiQuery) = when (val reply = api.query(uid, query)) {
        is ApiReply.Data -> reply.body
        is ApiReply.Failure -> throw OperationBlocked(reply.code)
        else -> throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
    }
    override suspend fun catalog(uid: String): List<RecordOption> = withContext(Dispatchers.IO) {
        owner(uid)
        if (!online) return@withContext contexts[uid]?.options ?: throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        val body = query(uid, ApiQuery.Catalog); val progress = query(uid, ApiQuery.Progress)
        val context = try {
            val cycle = progress.path("cycleId").textValue()
            require(!cycle.isNullOrBlank() && '/' !in cycle && cycle.none(Char::isISOControl))
            LocalDate.parse(progress.path("businessDate").textValue())
            require(body.path("activities").isArray)
            val options = body.path("activities").map {
                val id = it.path("id").textValue(); val name = it.path("name").textValue(); val mode = it.path("mode").textValue()
                require(!id.isNullOrBlank() && !name.isNullOrBlank() && mode in setOf("DURATION", "OCCURRENCE"))
                RecordOption(id, name, mode == "DURATION")
            }
            require(options.isNotEmpty() && options.map { it.id }.toSet().size == options.size)
            Context(cycle, options)
        } catch (_: Exception) { throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE) }
        val combined = context.copy(options = context.options + custom?.list(uid).orEmpty().map { RecordOption(it.id, it.name, true) })
        if (combined.options.map { it.id }.toSet().size != combined.options.size) throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
        owner(uid); contexts[uid] = combined; combined.options
    }
    override suspend fun draft(uid: String): RecordDraft? = withContext(Dispatchers.IO) {
        owner(uid)
        local { store.draftFor(uid) }?.content?.let { raw -> runCatching {
            val node = json.readTree(raw).path("recordDraft")
            if (!node.isObject) null else RecordDraft(node.path("activityId").asText(), node.path("date").asText(),
                node.path("hours").asText(), node.path("minutes").asText())
        }.getOrNull() }
    }
    override suspend fun saveDraft(uid: String, draft: RecordDraft): Unit = withContext(Dispatchers.IO) {
        owner(uid)
        synchronized(store) {
        val previous = local { store.draftFor(uid) }?.content
        val root = previous?.let { runCatching { json.readTree(it) as? ObjectNode }.getOrNull() }
            ?: json.createObjectNode().also { if (previous != null) it.put("previousDraft", previous) }
        root.set<com.fasterxml.jackson.databind.JsonNode>("recordDraft", json.valueToTree(mapOf("activityId" to draft.activityId,
            "date" to draft.date, "hours" to draft.hours, "minutes" to draft.minutes)))
        owner(uid); local { store.saveDraft(Draft(uid, json.writeValueAsString(root))) }
        }
    }
    override suspend fun pending(uid: String): List<OperationSnapshot> = withContext(Dispatchers.IO) {
        owner(uid)
        val startup = recoveredAtStartup.values.filter { it.ref.uid == uid }.toList()
        val ids = recoveryIds(uid)
        val recovered = if (online) operations.recoverFor(ApiCommandType.CREATE_RECORD).filter { it.ref.operationId in ids } else emptyList()
        owner(uid)
        val remaining = local { store.pendingFor(uid) }.filter { it.commandType == ApiCommandType.CREATE_RECORD.name }.map {
            OperationSnapshot(OperationRef(uid, it.operationId), it.state, it.safeError?.let(ApiFailureCode::valueOf))
        }
        startup.forEach { recoveredAtStartup.remove(it.ref, it) }
        (recovered + startup + remaining).distinctBy { it.ref }
    }
    override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot = withContext(Dispatchers.IO) {
        owner(uid)
        if (!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        val context = contexts[uid] ?: throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE)
        val option = context.options.singleOrNull { it.id == draft.activityId } ?: throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
        try { LocalDate.parse(draft.date) } catch (_: Exception) { throw OperationBlocked(ApiFailureCode.INVALID_INPUT) }
        val payload = linkedMapOf<String, Any?>("activityId" to option.id, "activityDate" to draft.date)
        if (option.duration) {
            val hours = draft.hours.toLongOrNull(); val minutes = draft.minutes.toLongOrNull()
            if (hours == null || minutes == null || hours !in 0..24 || minutes !in 0..59 ||
                (hours == 0L && minutes == 0L) || (hours == 24L && minutes != 0L)) throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
            payload["hours"] = hours; payload["minutes"] = minutes
        }
        operations.submit(operations.prepare(uid, intentId, ApiCommandType.CREATE_RECORD, payload, context.cycle))
    }
    override suspend fun retry(ref: OperationRef): OperationSnapshot = withContext(Dispatchers.IO) {
        owner(ref.uid); operations.retry(ref)
    }
}
