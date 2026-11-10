package lvluplife.application

import java.time.Clock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import lvluplife.data.*

data class OperationRef(val uid: String, val operationId: String)
data class OperationSnapshot(val ref: OperationRef, val state: PendingState, val error: ApiFailureCode? = null, val receipt: RemoteReceipt? = null)
class OperationBlocked(val code: ApiFailureCode) : IllegalStateException(code.name)

/** Blocking coordination: invoke from IO. Only this installation's stored envelopes are recoverable. */
class OperationCoordinator(
    private val store: PendingStore,
    private val api: ApiClient,
    private val sessions: SessionTokenProvider,
    private val online: () -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val preparationLock = Any()
    private val networkLock = Any()
    private val memoryLock = Any()
    private val epoch = AtomicLong()
    private val intents = ConcurrentHashMap<Pair<String, String>, PendingOperation>()
    private val completed = ConcurrentHashMap<OperationRef, OperationSnapshot>()
    private val authSuspended = ConcurrentHashMap.newKeySet<String>()

    fun prepare(uid: String, intentId: String, type: ApiCommandType, payload: Map<String, Any?>, cycleId: String? = null): OperationRef {
        requireOwner(uid)
        requireOnline()
        require(intentId.isNotBlank()) { "Submission intent is required" }
        return synchronized(preparationLock) {
            val key = uid to intentId
            val generation = epoch.get()
            val previous = intents[key]
            if (previous != null) {
                val encoded = ApiClient.encode(ApiCommand(previous.operationId, type, payload, cycleId))
                require(encoded == previous.envelope) { "Submission intent content is immutable" }
                val ref = previous.ref()
                if (!completed.containsKey(ref)) local { store.prepare(previous) }
                ref
            } else {
                requireOnline()
                val id = newId()
                val envelope = ApiClient.encode(ApiCommand(id, type, payload, cycleId))
                val row = PendingOperation(uid, id, type.name, envelope, fingerprint(envelope), cycleId,
                    PendingState.PREPARED, createdAtEpochMillis = clock.millis())
                // Keep the UUID in memory even if local commit throws: a repeated tap cannot allocate a new one.
                synchronized(memoryLock) {
                    if (currentUid() != uid || epoch.get() != generation) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
                    intents[key] = row
                }
                try { local { store.prepare(row) } }
                finally { if (epoch.get() != generation) intents.remove(key, row) }
                row.ref()
            }
        }
    }

    fun submit(ref: OperationRef): OperationSnapshot = synchronized(networkLock) {
        requireOwner(ref.uid)
        completed[ref]?.let { return@synchronized it }
        val row = row(ref)
        if (row.state.isDefinitive) return@synchronized restoreDefinitive(row, epoch.get())
        if (row.safeError == ApiFailureCode.FORBIDDEN.name) return@synchronized row.snapshot()
        requireOnline()
        if (row.state == PendingState.PREPARED) transmit(row, epoch.get())
        else resolve(row, resendAbsent = false, generation = epoch.get())
    }

    fun retry(ref: OperationRef): OperationSnapshot = synchronized(networkLock) {
        requireOwner(ref.uid)
        completed[ref]?.let { return@synchronized it }
        val row = row(ref)
        if (row.state.isDefinitive) return@synchronized restoreDefinitive(row, epoch.get())
        requireOnline()
        // Prepared is known unsent; every uncertain operation must first look up its original receipt.
        if (row.state == PendingState.PREPARED) transmit(row, epoch.get())
        else resolve(row, resendAbsent = true, generation = epoch.get())
    }

    fun recover(): List<OperationSnapshot> = synchronized(networkLock) {
        val uid = currentUid() ?: return@synchronized emptyList()
        val pending = local { store.pendingFor(uid) }
        if (uid in authSuspended || pending.any { it.safeError == ApiFailureCode.AUTH_REQUIRED.name }) {
            authSuspended.add(uid)
            return@synchronized emptyList()
        }
        val results = mutableListOf<OperationSnapshot>()
        val generation = epoch.get()
        for (original in pending) {
            if (currentUid() != uid || generation != epoch.get() || uid in authSuspended) break
            val row = if (original.state in setOf(PendingState.SENDING, PendingState.RESOLVING)) {
                local { store.updateState(uid, original.operationId, PendingState.UNKNOWN, original.safeError) }
                original.copy(state = PendingState.UNKNOWN)
            } else original
            results += when {
                row.state.isDefinitive -> restoreDefinitive(row, generation)
                row.safeError == ApiFailureCode.FORBIDDEN.name -> row.snapshot()
                !online() -> row.snapshot().copy(error = ApiFailureCode.NETWORK_ERROR)
                row.state == PendingState.PREPARED -> transmit(row, generation)
                else -> resolve(row, resendAbsent = false, generation)
            }
        }
        results
    }

    /** Called only after the original account has obtained a fresh valid session. No HTTP is sent here. */
    fun resumeAuthenticated(uid: String) {
        requireOwner(uid, allowSuspended = true)
        local { store.pendingFor(uid) }.filter { it.safeError == ApiFailureCode.AUTH_REQUIRED.name }.forEach {
            local { store.updateState(uid, it.operationId, it.state, null) }
        }
        authSuspended.remove(uid)
    }

    /** Nonblocking on logout, including while a request is in flight. Does not delete durable pending data. */
    fun clearSessionMemory() = synchronized(memoryLock) {
        epoch.incrementAndGet()
        intents.clear()
        completed.clear()
        authSuspended.clear()
    }

    private fun transmit(row: PendingOperation, generation: Long): OperationSnapshot {
        requireOwner(row.uid)
        requireOnline()
        // This transaction must finish before ApiClient may touch the transport.
        local { store.updateState(row.uid, row.operationId, PendingState.SENDING) }
        return try { apply(row, api.send(row.uid, row.envelope), generation, mutationResponse = true) }
        catch (cancelled: CancellationException) {
            try { local { store.updateState(row.uid, row.operationId, PendingState.UNKNOWN) } } catch (_: OperationBlocked) { /* Sending remains recoverable. */ }
            throw cancelled
        }
    }

    private fun resolve(original: PendingOperation, resendAbsent: Boolean, generation: Long): OperationSnapshot {
        requireOwner(original.uid)
        requireOnline()
        val row = original.copy(state = PendingState.UNKNOWN)
        if (original.state != PendingState.UNKNOWN) local {
            store.updateState(row.uid, row.operationId, PendingState.UNKNOWN, row.safeError)
        }
        local { store.updateState(row.uid, row.operationId, PendingState.RESOLVING) }
        return try {
            when (val reply = api.lookup(row.uid, row.operationId)) {
                ApiReply.ReceiptAbsent -> {
                    local { store.resolveAbsent(row.uid, row.operationId) }
                    if (resendAbsent) transmit(row, generation) else row.snapshot().copy(error = null)
                }
                else -> apply(row, reply, generation, mutationResponse = false)
            }
        } catch (cancelled: CancellationException) {
            try { local { store.updateState(row.uid, row.operationId, PendingState.UNKNOWN) } } catch (_: OperationBlocked) { /* Resolving remains recoverable. */ }
            throw cancelled
        }
    }

    private fun apply(row: PendingOperation, reply: ApiReply, generation: Long, mutationResponse: Boolean): OperationSnapshot = when (reply) {
        is ApiReply.Receipt -> {
            check(reply.value.operationId == row.operationId)
            val state = if (reply.value.outcome == RemoteOutcome.CONFIRMED) PendingState.CONFIRMED else PendingState.REJECTED
            finish(row, state, reply.value.errorCode, reply.value, generation)
        }
        is ApiReply.Failure -> {
            val code = if (reply.action == FailureAction.SUSPEND_AUTH) synchronized(memoryLock) {
                if (currentUid() == row.uid && epoch.get() == generation) {
                    authSuspended.add(row.uid)
                    reply.code
                } else ApiFailureCode.UNKNOWN_ERROR
            } else reply.code
            if (mutationResponse && reply.action == FailureAction.CORRECT_INPUT && !reply.mayHaveCommitted) {
                // Schema/transport-limit failures are known not applied, but are not fabricated remote receipts.
                finish(row, PendingState.REJECTED, code, null, generation)
            } else {
                local { store.updateState(row.uid, row.operationId, PendingState.UNKNOWN, code.name) }
                row.copy(state = PendingState.UNKNOWN, safeError = code.name).snapshot()
            }
        }
        else -> {
            local { store.updateState(row.uid, row.operationId, PendingState.UNKNOWN, ApiFailureCode.UNREADABLE_RESPONSE.name) }
            row.copy(state = PendingState.UNKNOWN, safeError = ApiFailureCode.UNREADABLE_RESPONSE.name).snapshot()
        }
    }

    private fun finish(row: PendingOperation, state: PendingState, error: ApiFailureCode?, receipt: RemoteReceipt?, generation: Long): OperationSnapshot {
        try { local { store.updateState(row.uid, row.operationId, state, error?.name) } }
        catch (_: OperationBlocked) {
            // Keep Sending/Resolving evidence if the durable acknowledgment could not be recorded.
            return OperationSnapshot(row.ref(), PendingState.UNKNOWN, ApiFailureCode.TEMPORARILY_UNAVAILABLE)
        }
        val result = OperationSnapshot(row.ref(), state, error, receipt)
        cacheIfCurrent(result, generation)
        try { local { store.removeDefinitive(row.uid, row.operationId) } }
        catch (_: OperationBlocked) { /* A definitive journal row is safe to clean up on next recovery. */ }
        return result
    }

    private fun restoreDefinitive(row: PendingOperation, generation: Long): OperationSnapshot {
        val result = row.snapshot()
        cacheIfCurrent(result, generation)
        try { local { store.removeDefinitive(row.uid, row.operationId) } } catch (_: OperationBlocked) { /* Keep definitive journal. */ }
        return result
    }

    private fun cacheIfCurrent(result: OperationSnapshot, generation: Long) = synchronized(memoryLock) {
        if (currentUid() == result.ref.uid && epoch.get() == generation) completed[result.ref] = result
    }

    private fun row(ref: OperationRef): PendingOperation =
        local { store.pendingFor(ref.uid) }.firstOrNull { it.operationId == ref.operationId }
            ?: throw OperationBlocked(ApiFailureCode.UNKNOWN_ERROR)

    private fun currentUid(): String? = try {
        sessions.current()?.takeIf { it.uid.isNotBlank() && it.idToken.isNotEmpty() && it.idToken.none { char -> char.code !in 33..126 } }?.uid
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    private fun requireOwner(uid: String, allowSuspended: Boolean = false) {
        if (currentUid() != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        if (!allowSuspended && (uid in authSuspended || local { store.pendingFor(uid) }.any { it.safeError == ApiFailureCode.AUTH_REQUIRED.name })) {
            authSuspended.add(uid)
            throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        }
    }
    private fun requireOnline() { if (!online()) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR) }

    private fun <T> local(block: () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }

    private fun fingerprint(envelope: String): String {
        val mapper = ObjectMapper()
        val canonical = mapper.readTree(envelope) as ObjectNode
        canonical.remove("operationId")
        return MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}

private val PendingState.isDefinitive get() = this == PendingState.CONFIRMED || this == PendingState.REJECTED
private fun PendingOperation.ref() = OperationRef(uid, operationId)
private fun PendingOperation.snapshot() = OperationSnapshot(ref(), state, safeError?.let {
    try { ApiFailureCode.valueOf(it) } catch (_: IllegalArgumentException) { ApiFailureCode.UNKNOWN_ERROR }
})
