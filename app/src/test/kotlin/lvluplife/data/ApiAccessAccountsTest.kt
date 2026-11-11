package lvluplife.data

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class ApiAccessAccountsTest {
    private class Store : PendingStore {
        val rows = ConcurrentHashMap<String, PendingOperation>(); val drafts = ConcurrentHashMap<String, Draft>(); var reads = 0
        override fun prepare(operation: PendingOperation) { rows.putIfAbsent(operation.operationId, operation) }
        override fun pendingFor(uid: String): List<PendingOperation> { reads++; return rows.values.filter { it.uid == uid } }
        override fun updateState(uid: String, operationId: String, state: PendingState, safeError: String?) {
            rows[operationId] = rows.getValue(operationId).copy(state = state, safeError = safeError)
        }
        override fun resolveAbsent(uid: String, operationId: String) = updateState(uid, operationId, PendingState.UNKNOWN, null)
        override fun removeDefinitive(uid: String, operationId: String) { rows.remove(operationId) }
        override fun saveDraft(draft: Draft) { drafts[draft.uid] = draft }
        override fun draftFor(uid: String): Draft? { reads++; return drafts[uid] }
        override fun close() = Unit
    }
    private class Fixture {
        val store = Store(); var token: SessionToken? = SessionToken("owner-a", "synthetic-firebase")
        val sessions = SessionTokenProvider { token }; val requests = mutableListOf<TransportRequest>()
        var response = TransportResponse(200, """{"profileRequired":true}""")
        val api = ApiClient(ApiEndpoint.from("https://api.example.test", false), sessions, HttpTransport { request ->
            requests += request
            if (request.method == "POST") {
                val json = ObjectMapper().readTree(request.body); val id = json.get("operationId").textValue()
                assertEquals("COMPLETE_PROFILE", json.get("type").textValue()); assertFalse(json.has("cycleId")); assertFalse(json.has("uid"))
                assertEquals(PendingState.SENDING, store.rows.getValue(id).state)
                TransportResponse(200, """{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-10T15:00:00Z","result":{"username":"Player"}}""")
            } else response
        })
        val coordinator = OperationCoordinator(store, api, sessions, { true })
        val accounts = ApiAccessAccounts(api, coordinator, store, sessions)
    }

    @Test fun successfulLookupDistinguishesRequiredFromOwnExistingProfileWithoutMutation() = runBlocking {
        val f = Fixture()
        assertEquals(ProfileLookup.Required, f.accounts.load("owner-a"))
        f.response = TransportResponse(200, """{"profileRequired":false,"profile":{"uid":"owner-a","username":"Player"}}""")
        assertEquals(ProfileLookup.Existing("owner-a", "Player"), f.accounts.load("owner-a"))
        assertTrue(f.requests.all { it.method == "GET" }); assertTrue(f.store.rows.isEmpty())
    }

    @Test fun failedOrMalformedLookupCannotBecomeARegistrationOrExposeAnotherUid() = runBlocking {
        val f = Fixture()
        for (response in listOf(TransportResponse(503, "{}"), TransportResponse(401, "{}"),
            TransportResponse(200, """{"profileRequired":false,"profile":{"uid":"owner-b","username":"Private_B"}}"""),
            TransportResponse(200, """{"profileRequired":"true"}"""))) {
            f.response = response
            try { f.accounts.load("owner-a"); fail("Expected failed lookup") } catch (_: OperationBlocked) { }
        }
    }

    @Test fun profileSubmissionPersistsBeforeHttpAndDoubleTapUsesTheSameOperation() = runBlocking {
        val f = Fixture()
        val first = f.accounts.complete("owner-a", "same-intent", "Player")
        assertEquals(PendingState.CONFIRMED, first.state)
        assertEquals(first.ref, f.accounts.complete("owner-a", "same-intent", "Player").ref)
        assertEquals(1, f.requests.size)
        assertNotEquals(first.ref, f.accounts.complete("owner-a", "new-intent", "Other").ref)
    }

    @Test fun draftIsOwnedByUidAndPreservedBeforeAttemptingRegistration() = runBlocking {
        val f = Fixture(); f.accounts.saveName("owner-a", "invalid name")
        assertEquals("invalid name", f.accounts.draft("owner-a"))
        assertTrue(f.requests.isEmpty()); assertTrue(f.store.rows.isEmpty())
        f.token = SessionToken("owner-b", "synthetic-b"); val before = f.store.reads
        try { f.accounts.draft("owner-a"); fail("Expected auth guard") } catch (failure: OperationBlocked) { assertEquals(ApiFailureCode.AUTH_REQUIRED, failure.code) }
        assertEquals(before, f.store.reads)
    }
}
