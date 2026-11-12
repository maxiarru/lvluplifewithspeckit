package lvluplife.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lvluplife.data.*
import lvluplife.ui.LoadState

enum class SessionRoute { SIGN_IN, HOME, PROFILE, HISTORY }
data class SessionView(val uid: String? = null, val routes: List<SessionRoute> = listOf(SessionRoute.SIGN_IN),
    val loads: Map<String, LoadState<Any>> = emptyMap(), val pending: List<OperationSnapshot> = emptyList(), val sessionId: Long = 0)
class ReadTicket internal constructor(val uid: String, internal val generation: Long, val key: String, internal val request: Long)

interface SessionOperations {
    fun recover(): List<OperationSnapshot>
    fun resumeAuthenticated(uid: String)
    fun clearSessionMemory()
    fun requiresAuthentication(uid: String): Boolean = false
}

/** Holds only the current account in memory. Authentication adapters supply verified, refreshed tokens. */
class SessionController(private val operations: SessionOperations? = null) : SessionTokenProvider {
    private val lock = Any()
    private val recoveryLock = Any()
    private val mutableState = MutableStateFlow(SessionView())
    val state: StateFlow<SessionView> = mutableState.asStateFlow()
    @Volatile private var token: SessionToken? = null
    private var generation = 0L
    private var request = 0L
    private val cache = mutableMapOf<String, Any>()
    private val latest = mutableMapOf<String, Long>()

    // Never acquire the UI lock from a coordinator callback: logout clears coordinator memory under that lock.
    override fun current(): SessionToken? = token

    /** Blocking IO when operations are attached. Call only after the identity adapter verifies a fresh session. */
    fun authenticationEpoch(): Long = synchronized(lock) { generation }

    fun authenticate(token: SessionToken, expectedEpoch: Long? = null): Boolean {
        require(token.uid.isNotBlank() && token.uid.length <= 128 && '/' !in token.uid && token.uid.none(Char::isISOControl))
        require(token.idToken.isNotEmpty() && token.idToken.all { it.code in 33..126 })
        val lease = synchronized(lock) {
            if (expectedEpoch != null && expectedEpoch != generation) return false
            clearLocked()
            this.token = token
            mutableState.value = SessionView(uid = token.uid, routes = listOf(SessionRoute.HOME), sessionId = generation)
            token.uid to generation
        }
        try { operations?.resumeAuthenticated(token.uid) }
        catch (blocked: OperationBlocked) { recoveryFailed(lease, blocked.code) }
        return synchronized(lock) { matches(lease) }
    }

    fun logout() = synchronized(lock) { clearLocked() }

    fun showHome(ticket: ReadTicket) = synchronized(lock) {
        if (accepts(ticket)) mutableState.value = mutableState.value.copy(routes = listOf(SessionRoute.HOME))
    }

    fun navigate(route: SessionRoute) = synchronized(lock) {
        if (token == null || route == SessionRoute.SIGN_IN) return@synchronized
        val view = mutableState.value
        if (view.routes.last() != route) mutableState.value = view.copy(routes = view.routes + route)
    }

    fun back(): Boolean = synchronized(lock) {
        val view = mutableState.value
        if (view.routes.size <= 1) false
        else { mutableState.value = view.copy(routes = view.routes.dropLast(1)); true }
    }

    fun beginLoad(key: String): ReadTicket = synchronized(lock) {
        val uid = token?.uid ?: throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        require(key.isNotBlank())
        val ticket = ReadTicket(uid, generation, key, ++request)
        latest[key] = ticket.request
        setLoad(key, LoadState.Loading)
        ticket
    }

    /** Values are authoritative query results; operation receipts must not be used to overwrite progress. */
    fun confirm(ticket: ReadTicket, value: Any) = synchronized(lock) {
        if (!accepts(ticket)) return@synchronized
        cache[ticket.key] = value
        setLoad(ticket.key, LoadState.Confirmed(value))
    }

    fun empty(ticket: ReadTicket) = synchronized(lock) {
        if (!accepts(ticket)) return@synchronized
        cache.remove(ticket.key)
        setLoad(ticket.key, LoadState.Empty)
    }

    fun fail(ticket: ReadTicket, code: ApiFailureCode) = synchronized(lock) {
        if (!accepts(ticket)) return@synchronized
        if (code == ApiFailureCode.AUTH_REQUIRED) clearLocked()
        else setLoad(ticket.key, cache[ticket.key]?.let { LoadState.Stale(it, code) } ?: LoadState.Error(code))
    }

    fun pending(ticket: ReadTicket, ref: OperationRef) = synchronized(lock) {
        if (!accepts(ticket)) return@synchronized
        require(ref.uid == ticket.uid) { "Pending owner must match session" }
        setLoad(ticket.key, LoadState.Pending(ref))
    }

    /** A screen may settle only its own recovered entry in the same authentication epoch. */
    fun settlePending(result: OperationSnapshot, expectedEpoch: Long) = synchronized(lock) {
        if (generation != expectedEpoch || token?.uid != result.ref.uid ||
            result.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED)) return@synchronized
        mutableState.value = mutableState.value.copy(pending=mutableState.value.pending.filterNot { it.ref == result.ref })
    }

    /** Blocking IO entry point; logout never acquires recoveryLock or waits for network calls. */
    fun recoverPending(): Unit = synchronized(recoveryLock) {
        val lease = synchronized(lock) { token?.uid?.let { it to generation } } ?: return@synchronized
        val ops = operations ?: return@synchronized
        try {
            if (!synchronized(lock) { matches(lease) }) return@synchronized
            if (ops.requiresAuthentication(lease.first)) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
            val results = ops.recover().filter { it.ref.uid == lease.first }
            synchronized(lock) {
                if (matches(lease)) {
                    if (results.any { it.error == ApiFailureCode.AUTH_REQUIRED }) clearLocked()
                    else mutableState.value = mutableState.value.copy(pending = results,
                        loads = mutableState.value.loads - "pending")
                }
            }
        } catch (blocked: OperationBlocked) {
            recoveryFailed(lease, blocked.code)
        }
    }

    private fun recoveryFailed(lease: Pair<String, Long>, code: ApiFailureCode) = synchronized(lock) {
        if (matches(lease)) {
            if (code == ApiFailureCode.AUTH_REQUIRED) clearLocked()
            else setLoad("pending", LoadState.Error(code))
        }
    }

    private fun accepts(ticket: ReadTicket) = token?.uid == ticket.uid && generation == ticket.generation && latest[ticket.key] == ticket.request
    private fun matches(lease: Pair<String, Long>) = token?.uid == lease.first && generation == lease.second
    private fun setLoad(key: String, value: LoadState<Any>) {
        mutableState.value = mutableState.value.copy(loads = mutableState.value.loads + (key to value))
    }
    private fun clearLocked() {
        generation++
        token = null
        cache.clear()
        latest.clear()
        mutableState.value = SessionView()
        operations?.clearSessionMemory()
    }
}
