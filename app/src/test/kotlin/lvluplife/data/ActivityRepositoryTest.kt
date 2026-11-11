package lvluplife.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class ActivityRepositoryTest {
    private class Store : PendingStore {
        val rows = linkedMapOf<String, PendingOperation>(); val drafts = mutableMapOf<String, Draft>()
        override fun prepare(operation: PendingOperation) { rows.putIfAbsent(operation.operationId, operation) }
        override fun pendingFor(uid: String) = rows.values.filter { it.uid == uid }
        override fun updateState(uid: String, operationId: String, state: PendingState, safeError: String?) {
            check(rows.getValue(operationId).uid == uid); rows[operationId] = rows.getValue(operationId).copy(state = state, safeError = safeError)
        }
        override fun resolveAbsent(uid: String, operationId: String) = updateState(uid, operationId, PendingState.UNKNOWN, null)
        override fun removeDefinitive(uid: String, operationId: String) { rows.remove(operationId) }
        override fun saveDraft(draft: Draft) { drafts[draft.uid] = draft }
        override fun draftFor(uid: String) = drafts[uid]
        override fun close() = Unit
    }
    private class Fixture {
        val json = ObjectMapper(); val store = Store(); var token: SessionToken? = SessionToken("owner-a", "synthetic-token")
        var online = true; var unknown = false; var swapDuringList = false
        val requests = mutableListOf<TransportRequest>()
        var listBody = """{"revision":3,"complete":true,"items":[{"id":"custom-one","name":"Práctica","category":"Libre","primary":"STR","secondary":null,"mode":"DURATION","createdAt":"2026-11-11T15:00:00Z"}]}"""
        val sessions = SessionTokenProvider { token }
        val api = ApiClient(ApiEndpoint.from("https://api.example.test", false), sessions, HttpTransport { request ->
            requests += request
            when {
                request.method == "POST" -> {
                    val body = json.readTree(request.body); val id = body.path("operationId").textValue()
                    assertEquals(PendingState.SENDING, store.rows.getValue(id).state)
                    assertEquals(body, json.readTree(store.rows.getValue(id).envelope))
                    if (unknown) TransportResponse(503, "{}") else TransportResponse(200,
                        """{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-11T15:00:00Z","result":{"activityId":"custom-one"}}""")
                }
                request.url.pathSegments.contains("operations") -> TransportResponse(200,
                    """{"operationId":"${request.url.pathSegments.last()}","outcome":"UNKNOWN"}""")
                request.url.pathSegments.contains("custom-activities") -> {
                    if (swapDuringList) token = SessionToken("owner-a", "synthetic-new-session")
                    TransportResponse(200, listBody)
                }
                request.url.pathSegments.contains("catalog") -> TransportResponse(200,
                    """{"activities":[{"id":"strength","name":"Fuerza","mode":"DURATION"}]}""")
                else -> TransportResponse(200, """{"cycleId":"cycle-observed","businessDate":"2026-11-11"}""")
            }
        })
        val coordinator = OperationCoordinator(store, api, sessions, { online })
        val repository = ActivityRepository(api, coordinator, store, sessions, { online })
        val draft = CustomActivityDraft("  Práctica  ", " Mi categoría ", "STR")
    }
    private suspend fun blocked(code: ApiFailureCode, block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (failure: OperationBlocked) { assertEquals(code, failure.code) }
    }
    @Test fun creationPersistsEnvelopeBeforeHttpAndOmitsCycleAndAbsentSecondary() = runBlocking {
        val f = Fixture(); f.repository.submit("owner-a", "intent", f.draft)
        val post = f.json.readTree(f.requests.single().body)
        assertEquals("CREATE_CUSTOM_ACTIVITY", post.path("type").textValue()); assertFalse(post.has("cycleId"))
        assertEquals("Práctica", post.path("payload").path("name").textValue()); assertEquals(" Mi categoría ", post.path("payload").path("category").textValue())
        assertFalse(post.path("payload").has("secondary")); assertFalse(post.path("payload").has("uid"))
    }
    @Test fun invalidInputsAndOfflineNeverAllocateOrSendAnOperation() = runBlocking {
        val f = Fixture()
        for (bad in listOf(f.draft.copy(name=" "), f.draft.copy(name="A".repeat(51)), f.draft.copy(category=" "),
            f.draft.copy(primary="CON"), f.draft.copy(secondary="CON"), f.draft.copy(secondary="STR")))
            blocked(ApiFailureCode.INVALID_INPUT) { f.repository.submit("owner-a", "bad", bad) }
        f.online = false; blocked(ApiFailureCode.NETWORK_ERROR) { f.repository.submit("owner-a", "offline", f.draft) }
        assertTrue(f.store.rows.isEmpty()); assertTrue(f.requests.isEmpty())
    }
    @Test fun incompleteOrCorruptListNeverBecomesAnEmptySuccess() = runBlocking {
        val f = Fixture(); assertEquals("custom-one", f.repository.list("owner-a").single().id)
        for (body in listOf("""{"revision":3,"complete":false,"items":[]}""", """{"revision":3,"complete":true}""",
            f.listBody.replace("\"STR\"", "\"CON\""), f.listBody.replace("\"secondary\":null", "\"secondary\":5"))) {
            f.listBody = body; blocked(ApiFailureCode.UNREADABLE_RESPONSE) { f.repository.list("owner-a") }
        }
    }
    @Test fun sessionReplacementDuringQueryCannotPublishOldDataEvenForSameUid() = runBlocking {
        val f = Fixture(); f.swapDuringList = true
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.list("owner-a") }
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.list("owner-b") }
    }
    @Test fun customDraftPreservesProfileAndRecordNamespacesAndOtherOwner() = runBlocking {
        val f = Fixture(); f.store.saveDraft(Draft("owner-a", """{"profileUsername":"Player","recordDraft":{"minutes":"25"}}"""))
        f.store.saveDraft(Draft("owner-b", "untouched")); f.repository.saveDraft("owner-a", f.draft)
        assertEquals(f.draft, f.repository.draft("owner-a"))
        val root = f.json.readTree(f.store.draftFor("owner-a")!!.content)
        assertEquals("Player", root.path("profileUsername").textValue()); assertEquals("25", root.path("recordDraft").path("minutes").textValue())
        assertEquals("untouched", f.store.draftFor("owner-b")!!.content); assertTrue(f.requests.isEmpty())
    }
    @Test fun uncertainRetryQueriesReceiptBeforeResendingOriginalEnvelope() = runBlocking {
        val f = Fixture(); f.unknown = true; val first = f.repository.submit("owner-a", "one", f.draft)
        val envelope = f.requests.single().body; f.unknown = false; f.repository.retry(first.ref)
        assertTrue(f.requests[1].url.pathSegments.contains("operations")); assertEquals(envelope, f.requests[2].body)
        assertEquals(2, f.requests.count { it.method == "POST" })
    }
    @Test fun recordRecoveryDoesNotConsumeCustomPendingAndPickerIncludesConfirmedDefinition() = runBlocking {
        val f = Fixture(); f.unknown = true; f.repository.submit("owner-a", "custom", f.draft)
        val records = RecordRepository(f.api, f.coordinator, f.store, f.sessions, { f.online }, f.repository)
        val before = f.requests.size; assertTrue(records.pending("owner-a").isEmpty()); assertEquals(before, f.requests.size)
        val options = records.catalog("owner-a"); assertEquals(listOf("strength", "custom-one"), options.map { it.id })
        assertTrue(options.last().duration)
    }
    @Test fun startupDefinitiveResultIsDeliveredOnceWithoutAnotherCommand() = runBlocking {
        val f = Fixture(); val snapshot = OperationSnapshot(OperationRef("owner-a", "123e4567-e89b-42d3-a456-426614174000"), PendingState.CONFIRMED)
        f.repository.rememberRecovered(listOf(snapshot)); assertEquals(listOf(snapshot), f.repository.pending("owner-a"))
        assertTrue(f.repository.pending("owner-a").isEmpty()); assertTrue(f.requests.isEmpty())
    }
}
