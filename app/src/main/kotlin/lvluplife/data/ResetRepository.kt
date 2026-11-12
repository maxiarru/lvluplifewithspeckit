package lvluplife.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.application.*

/** Owns only reset envelopes; resetting never deletes local drafts or account data. */
class ResetRepository(private val api: ApiClient, private val operations: OperationCoordinator,
    private val store: PendingStore, private val sessions: SessionTokenProvider,
    private val connected: () -> Boolean) : ResetFlowOperations {
    private val recovered = java.util.concurrent.ConcurrentHashMap<OperationRef, OperationSnapshot>()
    private val known = java.util.concurrent.ConcurrentHashMap.newKeySet<OperationRef>()
    override val online get() = connected()
    private fun lease(uid: String): () -> Unit {
        val token = sessions.current()
        if (token?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        return { if (sessions.current() !== token) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
    }
    private fun rows(uid: String): List<PendingOperation> = try {
        store.pendingFor(uid).filter { it.commandType == ApiCommandType.RESET_PROGRESSION.name }.also {
            it.forEach { row -> known.add(OperationRef(uid, row.operationId)) }
        }
    } catch (blocked: OperationBlocked) { throw blocked }
    catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
    catch (_: Exception) { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }
    fun recoveryIds(uid: String): Set<String> {
        val owner = lease(uid); val ids = rows(uid).map { it.operationId }.toSet(); owner(); return ids
    }
    fun rememberRecovered(results: List<OperationSnapshot>) {
        results.filter { it.ref.uid == sessions.current()?.uid && it.ref in known }.forEach { recovered[it.ref] = it }
    }
    fun clearMemory() { recovered.clear(); known.clear() }
    override suspend fun pending(uid: String): List<OperationSnapshot> = withContext(Dispatchers.IO) {
        val owner = lease(uid); rows(uid); owner()
        val startup = recovered.values.filter { it.ref.uid == uid }
        val live = if (online) operations.recoverFor(ApiCommandType.RESET_PROGRESSION) else emptyList()
        owner()
        val remaining = rows(uid).map { OperationSnapshot(OperationRef(uid,it.operationId),it.state,it.safeError?.let(ApiFailureCode::valueOf)) }
        owner(); startup.forEach { recovered.remove(it.ref,it) }
        (live + startup + remaining).distinctBy { it.ref }
    }
    override suspend fun submit(uid: String, intentId: String, cycleId: String, confirmed: Boolean): OperationSnapshot = withContext(Dispatchers.IO) {
        val owner = lease(uid)
        if (!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        if (!confirmed || intentId.isBlank() || cycleId.isBlank() || cycleId in setOf(".","..") || '/' in cycleId ||
            cycleId.any(Char::isISOControl) || cycleId.toByteArray(Charsets.UTF_8).size > 1500) throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
        owner()
        val ref = operations.prepare(uid,intentId,ApiCommandType.RESET_PROGRESSION,mapOf("confirmed" to true),cycleId)
        known.add(ref); owner()
        operations.submit(ref).also { owner() }
    }
    override suspend fun retry(ref: OperationRef): OperationSnapshot = withContext(Dispatchers.IO) {
        val owner = lease(ref.uid); rows(ref.uid); owner()
        if (ref !in known) throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
        operations.retry(ref).also { owner() }
    }
}
