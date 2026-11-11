package lvluplife.account

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*
import lvluplife.api.*
import lvluplife.model.OperationId
import lvluplife.model.Uid
import lvluplife.persistence.OperationStore

/** Real Auth tokens, runtime authorization and local Firestore rules, not an injected verifier. */
class IsolationTest {
    private val json = ObjectMapper()
    private val http = HttpClient.newHttpClient()
    private class Identity(val uid: Uid, val token: String) {
        override fun toString() = "Identity(redacted)"
    }
    private fun identity(): Identity {
        val response = http.send(HttpRequest.newBuilder(URI(
            "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"))
            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"returnSecureToken":true}""")).build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val value = json.readTree(response.body())
        return Identity(Uid(value.get("localId").textValue()), value.get("idToken").textValue())
    }
    private fun open(): ServerResources {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        return ServerResources.open(config).also {
            assertEquals("127.0.0.1:8080", it.firestore.options.host)
            assertEquals("127.0.0.1:8080", it.firestore.options.emulatorHost)
        }
    }
    private fun name() = "I" + UUID.randomUUID().toString().replace("-", "").take(12)
    private suspend fun io.ktor.client.HttpClient.complete(identity: Identity, name: String, id: OperationId = OperationId.create()) =
        post("/v1/commands") {
            bearerAuth(identity.token); contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "COMPLETE_PROFILE", "payload" to mapOf("username" to name))))
        }
    private suspend fun assertNoLeak(response: io.ktor.client.statement.HttpResponse, owner: Identity, name: String) {
        val body = response.bodyAsText()
        assertFalse(body.contains(owner.uid.value), "Response must not disclose the other owner")
        assertFalse(body.contains(name), "Response must not disclose the other name")
        assertFalse(body.contains(owner.token), "Response must not disclose credentials")
        assertFalse(body.contains("users/"), "Response must not disclose private storage paths")
    }

    @Test fun foreignQueriesAndGuessedPathsCannotSelectAnotherAccount() {
        open().use { resources ->
            val a = identity(); val b = identity(); val privateName = name()
            testApplication {
                application { serverModule(resources) }
                assertEquals(HttpStatusCode.OK, client.complete(a, privateName).status)
                val before = resources.documents.read(a.uid, emptyList())
                for (path in listOf("/v1/me?uid=${a.uid.value}", "/v1/progress?uid=${a.uid.value}",
                    "/v1/custom-activities?owner=${a.uid.value}", "/v1/history?period=day&date=2026-11-11&uid=${a.uid.value}")) {
                    val response = client.get(path) { bearerAuth(b.token) }
                    assertEquals(HttpStatusCode.BadRequest, response.status); assertNoLeak(response, a, privateName)
                }
                for (path in listOf("/v1/users/${a.uid.value}", "/v1/users/${a.uid.value}/progress", "/v1/me/${a.uid.value}")) {
                    val response = client.get(path) { bearerAuth(b.token) }
                    assertEquals(HttpStatusCode.NotFound, response.status); assertNoLeak(response, a, privateName)
                }
                val own = client.get("/v1/me") { bearerAuth(b.token); header("X-User-Id", a.uid.value) }
                assertEquals(HttpStatusCode.OK, own.status)
                assertTrue(json.readTree(own.bodyAsText()).get("profileRequired").booleanValue())
                assertNoLeak(own, a, privateName)
                assertEquals(before, resources.documents.read(a.uid, emptyList()))
                assertNull(resources.documents.read(b.uid, emptyList()))
                for (token in listOf<String?>(null, "synthetic-invalid-token")) {
                    val denied = client.get("/v1/me") { if (token != null) bearerAuth(token) }
                    assertEquals(HttpStatusCode.Unauthorized, denied.status); assertNoLeak(denied, a, privateName)
                }
            }
        }
    }

    @Test fun foreignOwnerInCommandsIsRejectedAndSameUuidIsScopedToVerifiedUid() {
        open().use { resources ->
            val a = identity(); val b = identity(); val privateName = name(); val ownName = name(); val id = OperationId.create()
            testApplication {
                application { serverModule(resources) }
                assertEquals(HttpStatusCode.OK, client.complete(a, privateName, id).status)
                val root = resources.documents.read(a.uid, emptyList())
                val projection = resources.documents.read(a.uid, listOf("projections", "current"))
                val receipt = OperationStore(resources.firestore).find(a.uid, id)
                val base = mapOf("operationId" to OperationId.create().value, "type" to "COMPLETE_PROFILE", "payload" to mapOf("username" to ownName))
                for (body in listOf(base + ("uid" to a.uid.value), base + ("owner" to a.uid.value),
                    base + ("payload" to mapOf("username" to ownName, "uid" to a.uid.value)), base + ("cycleId" to root?.get("currentCycleId")))) {
                    val response = client.post("/v1/commands") {
                        bearerAuth(b.token); contentType(ContentType.Application.Json); setBody(json.writeValueAsString(body))
                    }
                    assertEquals(HttpStatusCode.BadRequest, response.status); assertNoLeak(response, a, privateName)
                }
                val taken = client.complete(b, privateName)
                assertEquals(HttpStatusCode.Conflict, taken.status); assertNoLeak(taken, a, privateName)
                // Knowing A's UUID grants no ownership: the same ID belongs to B's separate namespace.
                val own = client.complete(b, ownName, id)
                assertEquals(HttpStatusCode.OK, own.status); assertNoLeak(own, a, privateName)
                assertEquals(ownName, resources.documents.read(b.uid, emptyList())?.get("username"))
                assertEquals(root, resources.documents.read(a.uid, emptyList()))
                assertEquals(projection, resources.documents.read(a.uid, listOf("projections", "current")))
                assertEquals(receipt, OperationStore(resources.firestore).find(a.uid, id))
                assertEquals(1, resources.firestore.collection("users/${a.uid.value}/cycles").get().get(10, TimeUnit.SECONDS).size())
            }
        }
    }

    @Test fun directFirestoreRejectsOwnForeignAndUnauthenticatedReadsWritesAndDeletes() {
        open().use { resources ->
            val a = identity(); val b = identity(); val privateName = name()
            testApplication {
                application { serverModule(resources) }
                assertEquals(HttpStatusCode.OK, client.complete(a, privateName).status)
                val root = assertNotNull(resources.documents.read(a.uid, emptyList()))
                val paths = listOf("users/${a.uid.value}", "users/${a.uid.value}/projections/current",
                    "users/${a.uid.value}/cycles/${root["currentCycleId"]}", "users/${a.uid.value}/records/private-record",
                    "users/${a.uid.value}/activities/private-activity", "users/${a.uid.value}/operations/${UUID.randomUUID()}",
                    "usernames/${privateName.lowercase()}")
                // Seed private child data through Admin, then assert every denied write leaves it intact.
                val snapshots = paths.associateWith { path ->
                    val ref = resources.firestore.document(path)
                    if (!ref.get().get(10, TimeUnit.SECONDS).exists()) ref.set(mapOf("privateMarker" to "synthetic-private")).get(10, TimeUnit.SECONDS)
                    ref.get().get(10, TimeUnit.SECONDS).data
                }
                for (token in listOf(null, a.token, b.token)) for (path in paths) for (method in listOf("GET", "PATCH", "DELETE")) {
                    val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:8080/v1/projects/demo-lvluplife/databases/(default)/documents/$path"))
                        .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    if (token != null) builder.header("Authorization", "Bearer $token")
                    val response = http.send(builder.method(method, if (method == "PATCH") HttpRequest.BodyPublishers.ofString(
                        """{"fields":{"privateMarker":{"stringValue":"must-not-write"}}}""") else HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                    assertEquals(403, response.statusCode(), "Direct client access must be denied")
                    assertEquals("PERMISSION_DENIED", json.readTree(response.body()).path("error").path("status").textValue())
                    assertFalse(response.body().contains("synthetic-private")); assertFalse(response.body().contains(a.token))
                }
                snapshots.forEach { (path, snapshot) -> assertEquals(snapshot, resources.firestore.document(path).get().get(10, TimeUnit.SECONDS).data) }
            }
        }
    }
}
