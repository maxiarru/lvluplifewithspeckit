package lvluplife.data

import lvluplife.application.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiAccessAccounts(private val api: ApiClient, private val operations: OperationCoordinator,
    private val store: PendingStore, private val sessions: SessionTokenProvider) : AccessAccounts {
    private val json = ObjectMapper()
    private fun owner(uid: String) {
        if (sessions.current()?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
    }
    override suspend fun load(uid: String): ProfileLookup = withContext(Dispatchers.IO) {
        owner(uid)
        when (val reply = api.query(uid, ApiQuery.Me)) {
            is ApiReply.Failure -> throw OperationBlocked(reply.code)
            is ApiReply.Data -> {
                val required = reply.body.path("profileRequired")
                if (!required.isBoolean) throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
                if (required.booleanValue()) ProfileLookup.Required else {
                    val profile = reply.body.path("profile"); val name = profile.path("username")
                    if (profile.path("uid").textValue() != uid || !name.isTextual ||
                        !Regex("[A-Za-z0-9_]{1,15}").matches(name.textValue()))
                        throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
                    ProfileLookup.Existing(uid, name.textValue())
                }
            }
            else -> throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
        }
    }
    override suspend fun complete(uid: String, intentId: String, username: String): OperationSnapshot = withContext(Dispatchers.IO) {
        owner(uid)
        operations.submit(operations.prepare(uid, intentId, ApiCommandType.COMPLETE_PROFILE, mapOf("username" to username)))
    }
    override suspend fun retry(ref: OperationRef): OperationSnapshot = withContext(Dispatchers.IO) {
        owner(ref.uid); operations.retry(ref)
    }
    override suspend fun draft(uid: String): String = withContext(Dispatchers.IO) {
        owner(uid)
        store.draftFor(uid)?.content?.let { content ->
            runCatching { json.readTree(content).path("profileUsername").takeIf { it.isTextual }?.textValue() }.getOrNull()
        } ?: ""
    }
    override suspend fun saveName(uid: String, username: String): Unit = withContext(Dispatchers.IO) {
        owner(uid)
        synchronized(store) {
        val previous = store.draftFor(uid)?.content
        val root = previous?.let { runCatching { json.readTree(it) as? ObjectNode }.getOrNull() }
            ?: json.createObjectNode().also { if (previous != null) it.put("previousDraft", previous) }
        root.put("profileUsername", username)
        owner(uid); store.saveDraft(Draft(uid, json.writeValueAsString(root)))
        }
    }
    override suspend fun pendingProfile(uid: String): OperationSnapshot? = withContext(Dispatchers.IO) {
        owner(uid)
        store.pendingFor(uid).firstOrNull { it.commandType == ApiCommandType.COMPLETE_PROFILE.name }?.let {
            OperationSnapshot(OperationRef(uid, it.operationId), it.state,
                it.safeError?.let { code -> ApiFailureCode.valueOf(code) })
        }
    }
}
