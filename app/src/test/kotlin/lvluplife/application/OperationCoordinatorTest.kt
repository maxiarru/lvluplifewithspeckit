package lvluplife.application

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import lvluplife.data.*
import org.junit.Assert.*
import org.junit.Test

class OperationCoordinatorTest {
    private class Store : PendingStore {
        val rows = ConcurrentHashMap<OperationRef, PendingOperation>()
        val drafts = ConcurrentHashMap<String, Draft>()
        val readOwners = mutableListOf<String>()
        var failPrepareBefore = false
        var failPrepareAfter = false
        var failState: PendingState? = null
        var failRemove = false
        override fun prepare(operation: PendingOperation) {
            if (failPrepareBefore) throw IOException("private-database-path")
            val key = OperationRef(operation.uid, operation.operationId)
            val original = rows.putIfAbsent(key, operation)
            require(original == null || (original.envelope == operation.envelope && original.requestHash == operation.requestHash))
            if (failPrepareAfter) throw IOException("private-database-path")
        }
        override fun pendingFor(uid: String): List<PendingOperation> {
            synchronized(readOwners) { readOwners += uid }
            return rows.values.filter { it.uid == uid }.sortedBy { it.operationId }
        }
        override fun updateState(uid: String, operationId: String, state: PendingState, safeError: String?) {
            if (failState == state) throw IOException("private-database-path")
            val key = OperationRef(uid, operationId)
            val original = checkNotNull(rows[key])
            check(original.state !in setOf(PendingState.CONFIRMED, PendingState.REJECTED) || state == original.state)
            rows[key] = original.copy(state = state, safeError = safeError)
        }
        override fun resolveAbsent(uid: String, operationId: String) = updateState(uid, operationId, PendingState.UNKNOWN, null)
        override fun removeDefinitive(uid: String, operationId: String) {
            if (failRemove) throw IOException("private-database-path")
            val key = OperationRef(uid, operationId)
            rows[key]?.let { check(it.state in setOf(PendingState.CONFIRMED, PendingState.REJECTED)) }
            rows.remove(key)
        }
        override fun saveDraft(draft: Draft) { drafts[draft.uid] = draft }
        override fun draftFor(uid: String) = drafts[uid]
        override fun close() = Unit
    }

    private class Backend : HttpTransport {
        val requests = java.util.Collections.synchronizedList(mutableListOf<TransportRequest>())
        val committed = ConcurrentHashMap<String, String>()
        var beforeCommitFailure = false
        var dropResponse = false
        var forcedStatus: Int? = null
        var rejection: String? = null
        var cancel = false
        var inspectPost: ((TransportRequest) -> Unit)? = null
        var inspectRequest: (() -> Unit)? = null
        fun receipt(id: String, error: String? = null): String = if (error == null)
            """{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-10T15:00:00Z","result":{"recordId":"synthetic-record","revision":1}}"""
        else """{"operationId":"$id","outcome":"REJECTED","validatedAt":"2026-11-10T15:00:00Z","error":{"code":"$error"}}"""
        override fun execute(request: TransportRequest): TransportResponse {
            requests += request
            inspectRequest?.invoke()
            forcedStatus?.let { return TransportResponse(it, "{}") }
            if (request.method == "GET") {
                check(request.url.encodedPath.startsWith("/v1/operations/"))
                return TransportResponse(200, committed[request.url.pathSegments.last()] ?: """{"outcome":"UNKNOWN"}""")
            }
            inspectPost?.invoke(request)
            if (cancel) throw CancellationException()
            if (beforeCommitFailure) throw IOException("private-network-details")
            val id = ObjectMapper().readTree(request.body).get("operationId").textValue()
            val body = committed.computeIfAbsent(id) { receipt(it, rejection) }
            if (dropResponse) throw SocketTimeoutException("private-network-details")
            return TransportResponse(if (rejection == null) 200 else 422, body)
        }
        fun posts() = requests.count { it.method == "POST" }
    }

    private class Fixture {
        val store = Store()
        val backend = Backend()
        @Volatile var session: SessionToken? = SessionToken("owner-a", "synthetic-token-a")
        var connected = true
        val ids = AtomicInteger()
        val sessions = SessionTokenProvider { session }
        val api = ApiClient(ApiEndpoint.from("https://api.example.test", false), sessions, backend)
        fun coordinator() = OperationCoordinator(store, api, sessions, { connected },
            Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC),
            { UUID(0, ids.incrementAndGet().toLong()).toString() })
        val payload = mapOf("activityId" to "strength", "activityDate" to "2026-11-10", "hours" to 0, "minutes" to 30)
        fun prepare(coordinator: OperationCoordinator, intent: String = "first") =
            coordinator.prepare("owner-a", intent, ApiCommandType.CREATE_RECORD, payload, "original-cycle")
    }

    @Test fun persistAndSendingCommitPrecedeHttpAndDoubleTapReusesOneOperation() {
        val f = Fixture(); val coordinator = f.coordinator()
        val ref = f.prepare(coordinator)
        assertEquals(ref, f.prepare(coordinator))
        val original = f.store.rows.getValue(ref)
        assertEquals(PendingState.PREPARED, original.state)
        assertEquals(64, original.requestHash.length)
        assertTrue(f.backend.requests.isEmpty())
        f.backend.inspectPost = { request ->
            assertEquals(PendingState.SENDING, f.store.rows.getValue(ref).state)
            assertEquals(original.envelope, request.body)
        }
        assertEquals(PendingState.CONFIRMED, coordinator.submit(ref).state)
        assertEquals(PendingState.CONFIRMED, coordinator.submit(ref).state)
        assertEquals(1, f.backend.posts())
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun twoIntentionalIdenticalLoadsHaveDifferentUuids() {
        val f = Fixture(); val c = f.coordinator()
        val first = f.prepare(c, "first"); val second = f.prepare(c, "second")
        assertNotEquals(first.operationId, second.operationId)
        c.submit(first); c.submit(second)
        assertEquals(2, f.backend.posts())
        assertEquals(2, f.backend.committed.size)
    }

    @Test fun concurrentDoubleTapCannotAllocateOrSendTwoOperations() {
        val f = Fixture(); val c = f.coordinator()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit<OperationSnapshot> { c.submit(f.prepare(c, "same-intent")) } }
                .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(results[0].ref, results[1].ref)
            assertTrue(results.all { it.state == PendingState.CONFIRMED })
            assertEquals(1, f.ids.get())
            assertEquals(1, f.backend.posts())
        } finally { pool.shutdownNow() }
    }

    @Test fun offlineOrWrongSessionCannotCreateNewPendingLoads() {
        val f = Fixture(); val c = f.coordinator()
        f.connected = false
        assertThrows(OperationBlocked::class.java) { f.prepare(c) }
        f.connected = true; f.session = SessionToken("owner-b", "synthetic-token-b")
        assertThrows(OperationBlocked::class.java) { f.prepare(c) }
        assertTrue(f.store.rows.isEmpty())
        assertTrue(f.backend.requests.isEmpty())
        assertEquals(0, f.ids.get())
    }

    @Test fun failedLocalPreparationRetainsTheIntentUuidAndNeverSends() {
        val f = Fixture(); val c = f.coordinator()
        f.store.failPrepareBefore = true
        assertThrows(OperationBlocked::class.java) { f.prepare(c) }
        assertTrue(f.backend.requests.isEmpty())
        f.store.failPrepareBefore = false
        val ref = f.prepare(c)
        assertEquals(1, f.ids.get())
        assertEquals(PendingState.CONFIRMED, c.submit(ref).state)
    }

    @Test fun failureAfterLocalCommitIsRecoveredWithOriginalEnvelopeAndCycle() {
        val f = Fixture(); val c = f.coordinator()
        f.store.failPrepareAfter = true
        assertThrows(OperationBlocked::class.java) { f.prepare(c) }
        val original = f.store.rows.values.single()
        f.store.failPrepareAfter = false
        val recovered = f.coordinator().recover().single()
        assertEquals(original.operationId, recovered.ref.operationId)
        assertEquals(PendingState.CONFIRMED, recovered.state)
        assertEquals(original.envelope, f.backend.requests.single().body)
        assertEquals(1, f.ids.get())
    }

    @Test fun lostResponseAfterRemoteCommitIsResolvedWithoutSecondPost() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.dropResponse = true
        assertEquals(PendingState.UNKNOWN, c.submit(ref).state)
        assertEquals(PendingState.UNKNOWN, f.store.rows.getValue(ref).state)
        val recovered = f.coordinator().recover().single()
        assertEquals(ref, recovered.ref)
        assertEquals(PendingState.CONFIRMED, recovered.state)
        assertEquals(1, f.backend.posts())
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun absentReceiptKeepsUnknownUntilExplicitRetryWithSameEnvelope() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        val original = f.store.rows.getValue(ref)
        f.backend.beforeCommitFailure = true
        assertEquals(PendingState.UNKNOWN, c.submit(ref).state)
        assertEquals(PendingState.UNKNOWN, f.coordinator().recover().single().state)
        assertEquals(1, f.backend.posts())
        assertEquals(original.envelope, f.store.rows.getValue(ref).envelope)
        f.backend.beforeCommitFailure = false
        assertEquals(PendingState.CONFIRMED, c.retry(ref).state)
        assertTrue(f.backend.requests.filter { it.method == "POST" }.all { it.body == original.envelope })
        assertEquals(1, f.ids.get())
    }

    @Test fun failureBeforeSendingCommitPreventsAnyHttpRequest() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.store.failState = PendingState.SENDING
        assertThrows(OperationBlocked::class.java) { c.submit(ref) }
        assertTrue(f.backend.requests.isEmpty())
        assertEquals(PendingState.PREPARED, f.store.rows.getValue(ref).state)
        f.store.failState = null
        assertEquals(ref, f.coordinator().recover().single().ref)
        assertEquals(1, f.backend.posts())
    }

    @Test fun failureToJournalRemoteReceiptKeepsRecoveryEvidence() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.store.failState = PendingState.CONFIRMED
        assertEquals(PendingState.UNKNOWN, c.submit(ref).state)
        assertTrue(f.store.rows.containsKey(ref))
        f.store.failState = null
        assertEquals(PendingState.CONFIRMED, f.coordinator().recover().single().state)
        assertEquals(1, f.backend.posts())
    }

    @Test fun failedCleanupLeavesDefinitiveJournalThatCanBeRemovedWithoutResending() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.store.failRemove = true
        assertEquals(PendingState.CONFIRMED, c.submit(ref).state)
        assertEquals(PendingState.CONFIRMED, f.store.rows.getValue(ref).state)
        f.store.failRemove = false
        assertEquals(PendingState.CONFIRMED, f.coordinator().recover().single().state)
        assertEquals(1, f.backend.requests.size)
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun rejectionPreservesDraftAndCorrectionAllocatesNewUuid() {
        val f = Fixture(); val c = f.coordinator()
        f.store.saveDraft(Draft("owner-a", "retained-user-input"))
        val ref = f.prepare(c)
        f.backend.rejection = "DATE_NOT_ALLOWED"
        assertEquals(PendingState.REJECTED, c.submit(ref).state)
        assertEquals("retained-user-input", f.store.draftFor("owner-a")?.content)
        val corrected = f.prepare(c, "corrected-intent")
        assertNotEquals(ref.operationId, corrected.operationId)
        f.backend.rejection = null
        assertEquals(PendingState.CONFIRMED, c.submit(corrected).state)
        assertEquals(PendingState.REJECTED, c.submit(ref).state)
        assertEquals(2, f.backend.posts())
    }

    @Test fun unauthorizedSuspendsAllRecoveryUntilOriginalAccountReauthenticates() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.forcedStatus = 401
        assertEquals(ApiFailureCode.AUTH_REQUIRED, c.submit(ref).error)
        assertTrue(c.recover().isEmpty())
        assertThrows(OperationBlocked::class.java) { f.prepare(c, "new-intent") }
        assertEquals(1, f.backend.requests.size)
        f.session = SessionToken("owner-b", "synthetic-token-b")
        val readsBefore = f.store.readOwners.size
        assertTrue(f.coordinator().recover().isEmpty())
        assertTrue(f.store.readOwners.drop(readsBefore).all { it == "owner-b" })
        assertThrows(OperationBlocked::class.java) { c.resumeAuthenticated("owner-a") }
        f.session = SessionToken("owner-a", "synthetic-token-a")
        val restored = f.coordinator()
        assertTrue(restored.recover().isEmpty())
        restored.resumeAuthenticated("owner-a")
        f.backend.forcedStatus = null
        assertEquals(PendingState.CONFIRMED, restored.retry(ref).state)
        assertEquals(1, f.ids.get())
    }

    @Test fun forbiddenDoesNotAutomaticallyRetryAfterRestart() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.forcedStatus = 403
        assertEquals(ApiFailureCode.FORBIDDEN, c.submit(ref).error)
        assertEquals(ApiFailureCode.FORBIDDEN, f.store.rows.getValue(ref).safeError?.let(ApiFailureCode::valueOf))
        assertEquals(PendingState.UNKNOWN, f.coordinator().recover().single().state)
        assertEquals(1, f.backend.requests.size)
        f.backend.forcedStatus = null
        assertEquals(PendingState.CONFIRMED, f.coordinator().retry(ref).state)
    }

    @Test fun sendingAndResolvingAfterProcessDeathAreQueriedBeforeAnyResend() {
        for (state in listOf(PendingState.SENDING, PendingState.RESOLVING)) {
            val f = Fixture(); val ref = f.prepare(f.coordinator())
            f.store.updateState(ref.uid, ref.operationId, state)
            f.backend.committed[ref.operationId] = f.backend.receipt(ref.operationId)
            assertEquals(PendingState.CONFIRMED, f.coordinator().recover().single().state)
            assertEquals(0, f.backend.posts())
        }
    }

    @Test fun cancellationAfterSendingLeavesUnknownWithOriginalUuid() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.cancel = true
        assertThrows(CancellationException::class.java) { c.submit(ref) }
        assertEquals(PendingState.UNKNOWN, f.store.rows.getValue(ref).state)
    }

    @Test fun anotherInstallationCannotDiscoverOrImportOperationsWithoutLocalEnvelope() {
        val f = Fixture(); val ref = f.prepare(f.coordinator())
        val other = Fixture()
        other.backend.committed[ref.operationId] = other.backend.receipt(ref.operationId)
        val c = other.coordinator()
        assertTrue(c.recover().isEmpty())
        assertThrows(OperationBlocked::class.java) { c.retry(ref) }
        assertTrue(other.backend.requests.isEmpty())
    }

    @Test fun sameIntentCannotChangeItsPersistedContents() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        val original = f.store.rows.getValue(ref)
        assertThrows(IllegalArgumentException::class.java) {
            c.prepare("owner-a", "first", ApiCommandType.CREATE_RECORD, f.payload + ("minutes" to 45), "original-cycle")
        }
        assertEquals(original, f.store.rows.getValue(ref))
        assertEquals(1, f.ids.get())
    }

    @Test fun lookupInputErrorCannotRejectAnAlreadyCommittedMutation() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.dropResponse = true
        assertEquals(PendingState.UNKNOWN, c.submit(ref).state)
        f.backend.forcedStatus = 400
        assertEquals(PendingState.UNKNOWN, f.coordinator().recover().single().state)
        assertTrue(f.store.rows.containsKey(ref))
        f.backend.forcedStatus = null
        assertEquals(PendingState.CONFIRMED, f.coordinator().recover().single().state)
        assertEquals(1, f.backend.posts())
    }

    @Test fun restartedCoordinatorCannotBypassPersistedAuthenticationSuspension() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.forcedStatus = 401
        c.submit(ref)
        val restarted = f.coordinator()
        assertThrows(OperationBlocked::class.java) { f.prepare(restarted, "new-intent") }
        assertThrows(OperationBlocked::class.java) { restarted.retry(ref) }
        assertEquals(1, f.ids.get())
        assertEquals(1, f.backend.requests.size)
    }

    @Test fun lateUnauthorizedResponseCannotSuspendTheNewSession() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.forcedStatus = 401
        f.backend.inspectRequest = {
            c.clearSessionMemory()
            f.session = SessionToken("owner-a", "synthetic-fresh-token")
        }
        assertEquals(PendingState.UNKNOWN, c.submit(ref).state)
        assertNotEquals(ApiFailureCode.AUTH_REQUIRED.name, f.store.rows.getValue(ref).safeError)
        f.prepare(c, "fresh-intent")
    }

    @Test fun clearedSessionCannotBeRepopulatedByLateConfirmation() {
        val f = Fixture(); val c = f.coordinator(); val ref = f.prepare(c)
        f.backend.inspectRequest = { c.clearSessionMemory() }
        assertEquals(PendingState.CONFIRMED, c.submit(ref).state)
        assertThrows(OperationBlocked::class.java) { c.submit(ref) }
        assertEquals(1, f.backend.posts())
    }
}
