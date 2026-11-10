package lvluplife.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlin.test.*
import lvluplife.api.*
import lvluplife.model.Uid

class FirestoreAccessTest {
    private fun open(): ServerResources {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        return ServerResources.open(config).also {
            // Assert destination before any RPC: tests must never reach the cloud endpoint.
            assertEquals("127.0.0.1:8080", it.firestore.options.host)
            assertEquals("127.0.0.1:8080", it.firestore.options.emulatorHost)
        }
    }
    private fun uid() = Uid("synthetic-${UUID.randomUUID()}")

    @Test fun identicalDocumentIdsRemainScopedToUidForReadsAndWrites() {
        open().use { resources ->
            val a = uid(); val b = uid()
            val path = listOf("records", "synthetic-shared-id")
            assertNull(resources.documents.read(a, path))
            resources.documents.write(a, path, mapOf("marker" to "account-a"))
            assertNull(resources.documents.read(b, path))
            resources.documents.write(b, path, mapOf("marker" to "account-b"))
            assertEquals("account-a", resources.documents.read(a, path)?.get("marker"))
            assertEquals("account-b", resources.documents.read(b, path)?.get("marker"))
            assertNull(resources.documents.read(a, emptyList()))
            resources.documents.write(a, emptyList(), mapOf("synthetic" to true))
            assertEquals(true, resources.documents.read(a, emptyList())?.get("synthetic"))
        }
    }

    @Test fun reconnectingWithANewClientPreservesDataAndSafeFailure() {
        val owner = uid()
        val path = listOf("records", "synthetic-reconnect")
        val first = open()
        first.documents.write(owner, path, mapOf("confirmed" to true))
        first.close()
        val failure = assertFailsWith<PersistenceUnavailable> { first.documents.read(owner, path) }
        assertNull(failure.cause)
        assertFalse(failure.message.orEmpty().contains(owner.value))
        assertFalse(failure.message.orEmpty().contains("synthetic-reconnect"))
        open().use { second -> assertEquals(true, second.documents.read(owner, path)?.get("confirmed")) }
    }

    @Test fun relativePathsCannotEscapeTheVerifiedUsersNamespace() {
        open().use { resources ->
            for (path in listOf(listOf("records"), listOf("records", ".."), listOf("records", "a/b"),
                listOf("records", ""), listOf("records", "a".repeat(1501)))) {
                assertFailsWith<IllegalArgumentException> { resources.documents.read(uid(), path) }
                assertFailsWith<IllegalArgumentException> { resources.documents.write(uid(), path, mapOf("synthetic" to true)) }
            }
        }
    }

    @Test fun unavailablePersistenceProduces503WithoutPrivatePaths() = testApplication {
        val resources = open()
        resources.close()
        application {
            serverModule(verifyToken = { "synthetic-owner-private" }, handlers = ApiHandlers(query = { owner, _ ->
                resources.documents.read(owner, listOf("records", "private-record-path"))
                error("must not return a successful query")
            }))
        }
        val response = client.get("/v1/progress") { bearerAuth("synthetic-secret-token") }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("TEMPORARILY_UNAVAILABLE"))
        listOf("synthetic-owner-private", "private-record-path", "synthetic-secret-token", "Exception").forEach {
            assertFalse(body.contains(it))
        }
    }

    @Test fun firebaseAdminVerifiesAnActualEmulatorIdentityWithoutAdc() {
        open().use { resources ->
            val request = HttpRequest.newBuilder(URI(
                "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"
            )).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"returnSecureToken":true}""")).build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            val json = ObjectMapper().readTree(response.body())
            val owner = resources.identityVerifier.verify(json.get("idToken").textValue())
            // Boolean assertion avoids printing tokens or response payloads on failure.
            assertTrue(owner?.value == json.get("localId").textValue())
        }
    }
}
