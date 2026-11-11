package lvluplife.account

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import lvluplife.api.*
import lvluplife.model.Uid

class RuntimeAccountRoutesTest {
    @Test fun runtimeModuleUsesFirebaseVerifierAndAccountHandlersWithNoImplicitProfileCreation() {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.host)
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val json = ObjectMapper()
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"returnSecureToken":true}""")).build()
            val identityResponse = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, identityResponse.statusCode())
            val identity = json.readTree(identityResponse.body())
            val token = identity.get("idToken").textValue(); val uid = Uid(identity.get("localId").textValue())
            testApplication {
                application { serverModule(resources) }
                val first = client.get("/v1/me") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, first.status)
                assertTrue(json.readTree(first.bodyAsText()).get("profileRequired").booleanValue())
                assertNull(resources.documents.read(uid, emptyList()))
                val username = "R" + UUID.randomUUID().toString().replace("-", "").take(10)
                val command = client.post("/v1/commands") {
                    bearerAuth(token); contentType(ContentType.Application.Json)
                    setBody(json.writeValueAsString(mapOf("operationId" to UUID.randomUUID().toString(), "type" to "COMPLETE_PROFILE", "payload" to mapOf("username" to username))))
                }
                assertEquals(HttpStatusCode.OK, command.status)
                val receipt = json.readTree(command.bodyAsText())
                assertEquals("CONFIRMED", receipt.get("outcome").textValue())
                assertNotNull(Instant.parse(receipt.get("validatedAt").textValue()))
                val me = client.get("/v1/me") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, me.status)
                val profile = json.readTree(me.bodyAsText())
                assertFalse(profile.get("profileRequired").booleanValue()); assertEquals(username, profile.findValue("username").textValue())
                assertFalse(profile.toString().contains(token)); assertFalse(receipt.toString().contains(token))
                val denied = client.get("/v1/me") { bearerAuth("synthetic-invalid-token") }
                assertEquals(HttpStatusCode.Unauthorized, denied.status)
            }
        }
    }
}
