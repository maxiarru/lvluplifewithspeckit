package lvluplife.application

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import lvluplife.data.*
import lvluplife.ui.LoadState
import org.junit.Assert.*
import org.junit.Test

class SessionControllerTest {
    private val a = SessionToken("owner-a", "synthetic-a")
    private val b = SessionToken("owner-b", "synthetic-b")
    private val ref = OperationRef("owner-a", "123e4567-e89b-42d3-a456-426614174000")
    private class Operations : SessionOperations {
        val resumed = mutableListOf<String>()
        var clears = 0
        var recoveries = 0
        var result = emptyList<OperationSnapshot>()
        var blocked: ApiFailureCode? = null
        var onRecover: (() -> Unit)? = null
        override fun resumeAuthenticated(uid: String) { resumed += uid }
        override fun clearSessionMemory() { clears++ }
        override fun recover(): List<OperationSnapshot> {
            recoveries++
            onRecover?.invoke()
            blocked?.let { throw OperationBlocked(it) }
            return result
        }
    }

    @Test fun logoutClearsTokenCachePendingAndNavigation() {
        val ops = Operations(); val c = SessionController(ops); c.authenticate(a)
        c.confirm(c.beginLoad("progress"), "private-a")
        ops.result = listOf(OperationSnapshot(ref, PendingState.UNKNOWN)); c.recoverPending()
        c.navigate(SessionRoute.HISTORY); c.logout()
        assertNull(c.current()); assertEquals(SessionView(), c.state.value)
        assertFalse(c.back()); assertTrue(ops.clears >= 1)
    }

    @Test fun switchingAccountDiscardsOldDataAndIgnoresItsLateResponses() {
        val c = SessionController(); c.authenticate(a)
        val old = c.beginLoad("progress"); c.confirm(old, "private-a")
        val inFlight = c.beginLoad("history"); c.navigate(SessionRoute.HISTORY)
        c.authenticate(b); c.confirm(inFlight, "late-private-a"); c.fail(old, ApiFailureCode.AUTH_REQUIRED)
        assertEquals("owner-b", c.state.value.uid)
        assertTrue(c.state.value.loads.isEmpty()); assertTrue(c.state.value.pending.isEmpty())
        assertEquals(listOf(SessionRoute.HOME), c.state.value.routes)
    }

    @Test fun failedQueryIsErrorWithoutPreviousDataAndStaleWithSameAccountData() {
        val c = SessionController(); c.authenticate(a)
        c.fail(c.beginLoad("history"), ApiFailureCode.NETWORK_ERROR)
        assertEquals(LoadState.Error(ApiFailureCode.NETWORK_ERROR), c.state.value.loads["history"])
        c.confirm(c.beginLoad("progress"), "revision-1")
        c.fail(c.beginLoad("progress"), ApiFailureCode.TEMPORARILY_UNAVAILABLE)
        assertEquals(LoadState.Stale("revision-1", ApiFailureCode.TEMPORARILY_UNAVAILABLE), c.state.value.loads["progress"])
    }

    @Test fun emptyRequiresSuccessfulQueryAndClearsOldCache() {
        val c = SessionController(); c.authenticate(a)
        c.confirm(c.beginLoad("history"), "old-record")
        c.empty(c.beginLoad("history"))
        assertEquals(LoadState.Empty, c.state.value.loads["history"])
        c.fail(c.beginLoad("history"), ApiFailureCode.TIMEOUT)
        assertEquals(LoadState.Error(ApiFailureCode.TIMEOUT), c.state.value.loads["history"])
    }

    @Test fun pendingCannotBecomeConfirmedOrExposeAnotherOwner() {
        val c = SessionController(); c.authenticate(a)
        c.pending(c.beginLoad("record"), ref)
        assertEquals(LoadState.Pending(ref), c.state.value.loads["record"])
        assertThrows(IllegalArgumentException::class.java) {
            c.pending(c.beginLoad("record"), ref.copy(uid = "owner-b"))
        }
    }

    @Test fun expiredSessionHidesPrivateDataAndSuspendsRecoveryUntilAuthentication() {
        val ops = Operations(); val c = SessionController(ops); c.authenticate(a)
        c.confirm(c.beginLoad("progress"), "private-a")
        c.fail(c.beginLoad("history"), ApiFailureCode.AUTH_REQUIRED)
        assertEquals(SessionView(), c.state.value); assertNull(c.current())
        c.recoverPending(); assertEquals(0, ops.recoveries)
        c.authenticate(a); c.recoverPending()
        assertEquals(listOf("owner-a", "owner-a"), ops.resumed); assertEquals(1, ops.recoveries)
    }

    @Test fun recoveredPendingMustBelongToCurrentAccountAndKeepOriginalUuid() {
        val ops = Operations(); val c = SessionController(ops)
        ops.result = listOf(OperationSnapshot(ref, PendingState.UNKNOWN))
        c.authenticate(a); c.recoverPending()
        assertEquals(ref, c.state.value.pending.single().ref)
        c.authenticate(b); c.recoverPending()
        assertTrue(c.state.value.pending.isEmpty())
        c.authenticate(a); c.recoverPending()
        assertEquals(ref, c.state.value.pending.single().ref)
        assertEquals(listOf("owner-a", "owner-b", "owner-a"), ops.resumed)
    }

    @Test fun recoveryUnauthorizedAlsoExpiresSessionButTransientErrorDoesNot() {
        val ops = Operations(); val c = SessionController(ops); c.authenticate(a)
        ops.blocked = ApiFailureCode.TEMPORARILY_UNAVAILABLE; c.recoverPending()
        assertEquals("owner-a", c.state.value.uid)
        assertEquals(LoadState.Error(ApiFailureCode.TEMPORARILY_UNAVAILABLE), c.state.value.loads["pending"])
        ops.blocked = ApiFailureCode.AUTH_REQUIRED; c.recoverPending()
        assertEquals(SessionView(), c.state.value)
    }

    @Test fun recoveryReturningUnauthorizedRequestsAuthentication() {
        val ops = Operations(); val c = SessionController(ops); c.authenticate(a)
        ops.result = listOf(OperationSnapshot(ref, PendingState.UNKNOWN, ApiFailureCode.AUTH_REQUIRED))
        c.recoverPending(); assertEquals(SessionView(), c.state.value)
    }

    @Test fun logoutDoesNotWaitForNetworkAndLateRecoveryCannotRestoreData() {
        val ops = Operations(); val c = SessionController(ops); c.authenticate(a)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        ops.result = listOf(OperationSnapshot(ref, PendingState.UNKNOWN))
        ops.onRecover = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val request = pool.submit { c.recoverPending() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            c.logout(); c.authenticate(b)
            release.countDown(); request.get(5, TimeUnit.SECONDS)
            assertEquals("owner-b", c.state.value.uid); assertTrue(c.state.value.pending.isEmpty())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun newestQueryWinsAndOldReceiptDoesNotReplaceProgress() {
        val c = SessionController(); c.authenticate(a)
        val older = c.beginLoad("progress"); val latest = c.beginLoad("progress")
        c.confirm(latest, "revision-2"); c.confirm(older, "revision-1")
        assertEquals(LoadState.Confirmed("revision-2"), c.state.value.loads["progress"])
    }

    @Test fun unauthenticatedNavigationAndReadsAreBlockedAndInvalidTokensRejected() {
        val c = SessionController()
        assertFalse(c.back())
        assertThrows(OperationBlocked::class.java) { c.beginLoad("progress") }
        c.navigate(SessionRoute.HISTORY); assertEquals(SessionView(), c.state.value)
        assertThrows(IllegalArgumentException::class.java) { c.authenticate(SessionToken("owner-a", "bad\ntoken")) }
        assertNull(c.current())
    }

    @Test fun realCoordinatorNeverSendsAccountAPendingWithAccountBSession() {
        val rows = mutableMapOf<OperationRef, PendingOperation>()
        val store = object : PendingStore {
            override fun prepare(operation: PendingOperation) { rows[OperationRef(operation.uid, operation.operationId)] = operation }
            override fun pendingFor(uid: String) = rows.values.filter { it.uid == uid }
            override fun updateState(uid: String, operationId: String, state: PendingState, safeError: String?) {
                val key = OperationRef(uid, operationId); rows[key] = rows.getValue(key).copy(state = state, safeError = safeError)
            }
            override fun resolveAbsent(uid: String, operationId: String) = updateState(uid, operationId, PendingState.UNKNOWN, null)
            override fun removeDefinitive(uid: String, operationId: String) { rows.remove(OperationRef(uid, operationId)) }
            override fun saveDraft(draft: Draft) = Unit
            override fun draftFor(uid: String): Draft? = null
            override fun close() = Unit
        }
        lateinit var coordinator: OperationCoordinator
        val operations = object : SessionOperations {
            override fun recover() = coordinator.recover()
            override fun resumeAuthenticated(uid: String) = coordinator.resumeAuthenticated(uid)
            override fun clearSessionMemory() = coordinator.clearSessionMemory()
            override fun requiresAuthentication(uid: String) = coordinator.requiresAuthentication(uid)
        }
        val c = SessionController(operations)
        val requests = mutableListOf<TransportRequest>()
        var unauthorized = false
        val transport = HttpTransport { request ->
            requests += request
            if (unauthorized) return@HttpTransport TransportResponse(401, "{}")
            if (request.method == "POST") throw java.net.SocketTimeoutException()
            TransportResponse(200, """{"operationId":"${ref.operationId}","outcome":"CONFIRMED","validatedAt":"2026-11-10T15:00:00Z","result":{"revision":1}}""")
        }
        var idCount = 0
        coordinator = OperationCoordinator(store, ApiClient(ApiEndpoint.from("https://api.example.test", false), c, transport), c, { true },
            newId = { if (idCount++ == 0) ref.operationId else java.util.UUID(0, idCount.toLong()).toString() })
        c.authenticate(a)
        val operation = coordinator.prepare("owner-a", "load", ApiCommandType.CREATE_RECORD,
            mapOf("activityId" to "strength", "hours" to 0, "minutes" to 30, "activityDate" to "2026-11-10"), "cycle-a")
        assertEquals(PendingState.UNKNOWN, coordinator.submit(operation).state)
        c.logout(); c.authenticate(b); c.recoverPending()
        assertEquals(1, requests.size); assertTrue(c.state.value.pending.isEmpty())
        assertEquals(ref.operationId, rows.values.single().operationId)
        c.logout(); c.authenticate(a); c.recoverPending()
        assertEquals(ref, c.state.value.pending.single().ref)
        assertEquals(PendingState.CONFIRMED, c.state.value.pending.single().state)
        assertEquals(listOf("POST", "GET"), requests.map { it.method })
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer synthetic-a" })
        unauthorized = true
        val second = coordinator.prepare("owner-a", "second-load", ApiCommandType.CREATE_RECORD,
            mapOf("activityId" to "strength", "hours" to 0, "minutes" to 30, "activityDate" to "2026-11-10"), "cycle-a")
        assertEquals(ApiFailureCode.AUTH_REQUIRED, coordinator.submit(second).error)
        c.recoverPending()
        assertNull(c.current())
        assertEquals(SessionView(), c.state.value)
        // A fresh login resumes once; a new401 before its first recovery must not be cleared again.
        c.authenticate(a)
        assertEquals(ApiFailureCode.AUTH_REQUIRED, coordinator.submit(second).error)
        c.recoverPending()
        assertNull(c.current())
    }
}
