package lvluplife.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.application.*

class ActivityRepository(private val api: ApiClient, private val operations: OperationCoordinator,
    private val store: PendingStore, private val sessions: SessionTokenProvider,
    private val connected: () -> Boolean) : CustomActivityFlowActivities {
    private val json = ObjectMapper()
    private val recovered = java.util.concurrent.ConcurrentHashMap<OperationRef, OperationSnapshot>()
    override val online get() = connected()
    private fun owner(uid: String) { if (sessions.current()?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
    private fun <T> local(block: () -> T): T = try { block() }
    catch (blocked: OperationBlocked) { throw blocked }
    catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
    catch (_: Exception) { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }
    fun clearMemory() = recovered.clear()
    fun recoveryIds(uid: String): Set<String> {
        owner(uid)
        return local { store.pendingFor(uid) }.filter { it.commandType == ApiCommandType.CREATE_CUSTOM_ACTIVITY.name }.map { it.operationId }.toSet()
    }
    fun rememberRecovered(results: List<OperationSnapshot>) {
        results.filter { it.ref.uid == sessions.current()?.uid }.forEach { recovered[it.ref] = it }
    }
    override suspend fun list(uid: String): List<CustomActivityOption> = withContext(Dispatchers.IO) {
        val lease = sessions.current()
        if (lease?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        if (!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        val body = when (val reply = api.query(uid, ApiQuery.CustomActivities)) {
            is ApiReply.Data -> reply.body
            is ApiReply.Failure -> throw OperationBlocked(reply.code)
            else -> throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
        }
        if (sessions.current() !== lease) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        try {
            val rev = body.path("revision")
            require(rev.isIntegralNumber && rev.canConvertToLong() && rev.longValue() > 0)
            require(body.path("complete").isBoolean && body.path("complete").booleanValue() && body.path("items").isArray)
            val items = body.path("items").map { item ->
                val id = item.path("id").textValue(); val name = item.path("name").textValue(); val category = item.path("category").textValue()
                val primary = item.path("primary").textValue(); val secondary = item.path("secondary").takeUnless { it.isNull }?.textValue()
                require(!id.isNullOrBlank() && id !in setOf(".", "..") && '/' !in id && id.none(Char::isISOControl))
                require(!name.isNullOrBlank() && name == name.trim() && name.codePointCount(0, name.length) <= 50 && !category.isNullOrBlank())
                require(item.path("mode").textValue() == "DURATION" && primary in allowed && (secondary == null || secondary in allowed && secondary != primary))
                require(item.has("secondary") && (item.path("secondary").isNull || item.path("secondary").isTextual))
                Instant.parse(item.path("createdAt").textValue())
                CustomActivityOption(id, name, category, primary, secondary)
            }
            require(items.map { it.id }.toSet().size == items.size)
            items
        } catch (_: Exception) { throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE) }
    }
    override suspend fun draft(uid: String): CustomActivityDraft? = withContext(Dispatchers.IO) {
        owner(uid)
        local { store.draftFor(uid) }?.content?.let { raw -> runCatching {
            val node = json.readTree(raw).path("customActivity")
            if (!node.isObject) null else CustomActivityDraft(node.path("name").asText(), node.path("category").asText(),
                node.path("primary").asText(), node.path("secondary").takeIf { it.isTextual }?.textValue())
        }.getOrNull() }
    }
    override suspend fun saveDraft(uid: String, draft: CustomActivityDraft): Unit = withContext(Dispatchers.IO) {
        owner(uid)
        local { synchronized(store) {
            val previous = store.draftFor(uid)?.content
            val root = previous?.let { runCatching { json.readTree(it) as? ObjectNode }.getOrNull() }
                ?: json.createObjectNode().also { if (previous != null) it.put("previousDraft", previous) }
            root.set<JsonNode>("customActivity", json.valueToTree(draft))
            owner(uid); store.saveDraft(Draft(uid, json.writeValueAsString(root)))
        } }
    }
    override suspend fun pending(uid: String): List<OperationSnapshot> = withContext(Dispatchers.IO) {
        owner(uid)
        val startup = recovered.values.filter { it.ref.uid == uid }
        val live = if (online) operations.recoverFor(ApiCommandType.CREATE_CUSTOM_ACTIVITY) else emptyList()
        owner(uid)
        val remaining = local { store.pendingFor(uid) }.filter { it.commandType == ApiCommandType.CREATE_CUSTOM_ACTIVITY.name }.map {
            OperationSnapshot(OperationRef(uid, it.operationId), it.state, it.safeError?.let(ApiFailureCode::valueOf))
        }
        startup.forEach { recovered.remove(it.ref, it) }
        (live + startup + remaining).distinctBy { it.ref }
    }
    override suspend fun submit(uid: String, intentId: String, draft: CustomActivityDraft): OperationSnapshot = withContext(Dispatchers.IO) {
        owner(uid)
        if (!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        val name = draft.name.trim()
        if (name.codePointCount(0, name.length) !in 1..50 || draft.category.isBlank() || draft.primary !in allowed ||
            draft.secondary != null && (draft.secondary !in allowed || draft.secondary == draft.primary)) throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
        val payload = linkedMapOf<String, Any?>("name" to name, "category" to draft.category, "primary" to draft.primary)
        draft.secondary?.let { payload["secondary"] = it }
        operations.submit(operations.prepare(uid, intentId, ApiCommandType.CREATE_CUSTOM_ACTIVITY, payload))
    }
    override suspend fun retry(ref: OperationRef): OperationSnapshot = withContext(Dispatchers.IO) { owner(ref.uid); operations.retry(ref) }
    companion object { private val allowed = setOf("STR", "DEX", "INT", "WIT", "MEN") }
}
