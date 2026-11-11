package lvluplife.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import lvluplife.application.*

class ProgressRepositoryTest {
    private class Fixture {
        val json = ObjectMapper()
        var token: SessionToken? = SessionToken("owner-a", "synthetic-token")
        val sessions = SessionTokenProvider { token }
        var afterRead: (() -> Unit)? = null
        val requests = mutableListOf<TransportRequest>()
        var data = json.readTree("""{"revision":12,"cycleId":"synthetic-cycle","businessDate":"2026-11-11","computedAt":"2026-11-11T15:00:00Z","generalLevel":1,"generalRemainder":0,"classId":"novato","stage":1,"attributes":{"STR":{"level":1,"xp":6},"DEX":{"level":1,"xp":0},"INT":{"level":1,"xp":0},"WIT":{"level":1,"xp":0},"MEN":{"level":1,"xp":0},"CON":{"level":1,"xp":0}}}""")
        var catalog = """{"classes":[{"id":"novato","name":"Novato","stages":[1]}]}"""
        val api = ApiClient(ApiEndpoint.from("https://api.example.test", false), sessions, HttpTransport { request ->
            requests += request
            if (request.url.pathSegments.contains("progress")) {
                afterRead?.invoke(); TransportResponse(200, data.toString())
            } else TransportResponse(200, catalog)
        })
        val repository = ProgressRepository(api, sessions)
    }
    @Test fun readsServerProgressAndCatalogWithoutSendingCommands() = runBlocking {
        val f = Fixture(); val p = f.repository.load("owner-a")
        assertEquals(12L, p.revision); assertEquals(6, p.attributes.getValue("STR").xp)
        assertEquals(setOf("STR","DEX","INT","WIT","MEN","CON"), p.attributes.keys)
        assertEquals("novato", p.classId); assertEquals(1, p.stage)
        assertEquals("Novato", p.className); assertEquals(java.time.Instant.parse("2026-11-11T15:00:00Z"), p.computedAt)
        assertTrue(f.requests.all { it.method == "GET" })
    }
    @Test fun missingNegativeFractionalAndTerminalXpCannotBecomeConfirmedProgress() = runBlocking {
        val values = listOf("""{"level":1,"xp":-1}""", """{"level":1,"xp":1.5}""", """{"level":50,"xp":1}""", """{"level":51,"xp":0}""")
        for (value in values) {
            val f = Fixture(); (f.data.path("attributes") as com.fasterxml.jackson.databind.node.ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>("STR", f.json.readTree(value))
            try { f.repository.load("owner-a"); fail("Malformed progress accepted") }
            catch (blocked: OperationBlocked) { assertEquals(ApiFailureCode.UNREADABLE_RESPONSE, blocked.code) }
        }
        val f = Fixture(); (f.data.path("attributes") as com.fasterxml.jackson.databind.node.ObjectNode).remove("CON")
        try { f.repository.load("owner-a"); fail() } catch (_: OperationBlocked) { }
    }
    @Test fun wrongOwnerCannotStartAQuery() = runBlocking {
        val f = Fixture()
        try { f.repository.load("owner-b"); fail() } catch (blocked: OperationBlocked) { assertEquals(ApiFailureCode.AUTH_REQUIRED, blocked.code) }
        assertTrue(f.requests.isEmpty())
    }
    @Test fun responseAfterSessionChangeIsDiscardedBeforeAnotherRequest() = runBlocking {
        val f = Fixture(); f.afterRead = { f.token = SessionToken("owner-b", "synthetic-b") }
        try { f.repository.load("owner-a"); fail() } catch (blocked: OperationBlocked) { assertEquals(ApiFailureCode.AUTH_REQUIRED, blocked.code) }
        assertEquals(1, f.requests.size)
    }
    @Test fun unknownClassOrStageDoesNotFallBackToNovato() = runBlocking {
        for (field in listOf("classId", "stage")) {
            val f = Fixture(); val node = f.data as com.fasterxml.jackson.databind.node.ObjectNode
            if (field == "classId") node.put(field, "synthetic-unknown") else node.put(field, 2)
            try { f.repository.load("owner-a"); fail() } catch (blocked: OperationBlocked) { assertEquals(ApiFailureCode.UNREADABLE_RESPONSE, blocked.code) }
        }
    }
    @Test fun returnedAttributesCannotBeMutated() = runBlocking {
        val f = Fixture(); val p = f.repository.load("owner-a")
        try { (p.attributes as MutableMap).clear(); fail() } catch (_: UnsupportedOperationException) { }
    }
}
