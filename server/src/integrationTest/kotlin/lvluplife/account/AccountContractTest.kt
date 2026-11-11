package lvluplife.account

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.model.*
import lvluplife.persistence.OperationStore
import lvluplife.persistence.ReceiptOutcome

/** HTTP contracts with real local Firestore and an injected test identity verifier, not Google sign-in. */
class AccountContractTest {
    private val json = ObjectMapper()
    private val now = Instant.parse("2026-11-10T15:00:00Z")
    private class Fixture(val resources: ServerResources) {
        val a = Uid("synthetic-account-${UUID.randomUUID()}")
        val b = Uid("synthetic-account-${UUID.randomUUID()}")
        val tokenA = "synthetic-account-token-a"
        val tokenB = "synthetic-account-token-b"
        fun verify(token: String): String? = when (token) { tokenA -> a.value; tokenB -> b.value; else -> null }
        fun root(uid: Uid) = resources.documents.read(uid, emptyList())
        fun cycles(uid: Uid) = resources.firestore.collection("users/${uid.value}/cycles").get().get(10, TimeUnit.SECONDS).documents
        fun reservation(name: String) = resources.firestore.document("usernames/${name.lowercase(Locale.ROOT)}").get().get(10, TimeUnit.SECONDS).data
        fun projection(uid: Uid) = resources.documents.read(uid, listOf("projections", "current"))
        fun noProfile(uid: Uid) {
            // A rejection may retain revision/receipt metadata; it must not create a profile or character.
            assertNull(root(uid)?.get("username")); assertTrue(cycles(uid).isEmpty()); assertNull(projection(uid))
        }
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.host)
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val fixture = Fixture(resources)
            testApplication {
                application { serverModule(accountHandlers(resources.firestore, AuthoritativeClock { now }), fixture::verify) }
                block(fixture)
            }
        }
    }
    private fun name() = "M" + UUID.randomUUID().toString().replace("-", "").take(10)
    private suspend fun HttpClient.complete(token: String, username: String, id: OperationId = OperationId.create()): HttpResponse =
        post("/v1/commands") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "COMPLETE_PROFILE", "payload" to mapOf("username" to username))))
        }
    private suspend fun body(response: HttpResponse): JsonNode = json.readTree(response.bodyAsText())

    @Test fun meRequiresProfileWithoutCreatingAnythingOnAuthenticationOrAbandonment() = scenario { f ->
        repeat(2) {
            val response = client.get("/v1/me") { bearerAuth(f.tokenA) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body(response).get("profileRequired").booleanValue())
        }
        // Abandon/cancel before COMPLETE_PROFILE: the server receives no mutation command.
        assertNull(f.root(f.a)); assertTrue(f.cycles(f.a).isEmpty()); assertNull(f.projection(f.a))
    }

    @Test fun completeProfileCreatesNameCycleProjectionAndReceiptTogether() = scenario { f ->
        val username = name(); val id = OperationId.create()
        val response = client.complete(f.tokenA, username, id)
        assertEquals(HttpStatusCode.OK, response.status)
        val receipt = body(response)
        assertEquals("CONFIRMED", receipt.get("outcome").textValue()); assertEquals(id.value, receipt.get("operationId").textValue())
        assertEquals(now, Instant.parse(receipt.get("validatedAt").textValue()))
        val root = assertNotNull(f.root(f.a))
        assertNotNull(root["createdAt"])
        assertEquals(username, root["username"]); assertEquals(username.lowercase(Locale.ROOT), root["usernameKey"])
        assertEquals(f.a.value, f.reservation(username)?.get("uid"))
        val cycle = f.cycles(f.a).single()
        assertEquals(cycle.id, root["currentCycleId"]); assertNotNull(cycle.data["startedAt"]); assertNull(cycle.data["closedAt"])
        val projection = assertNotNull(f.projection(f.a))
        assertEquals(cycle.id, projection["cycleId"])
        assertEquals(root["revision"], projection["revision"])
        assertEquals(1, (projection["generalLevel"] as Number).toInt())
        assertEquals(0, (projection["generalRemainder"] as Number).toInt())
        val attributes = assertIs<Map<*, *>>(projection["attributes"]); assertEquals(6, attributes.size)
        attributes.values.forEach { attribute ->
            val fields = assertIs<Map<*, *>>(attribute)
            assertEquals(1, (fields["level"] as Number).toInt()); assertEquals(0, (fields["xp"] as Number).toInt())
        }
        val stored = assertNotNull(OperationStore(f.resources.firestore).find(f.a, id))
        assertEquals(ReceiptOutcome.CONFIRMED, stored.outcome)
        assertEquals((root["revision"] as Number).toLong(), stored.revision.value)
    }

    @Test fun invalidNamesAreRejectedWithoutAssigningProfileOrCharacter() = scenario { f ->
        for (username in listOf("", " ", "two words", " leading", "trailing ", "Álvaro", "bad-name", "name!", "a".repeat(16), "🙂")) {
            val id = OperationId.create(); val response = client.complete(f.tokenA, username, id)
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            val result = body(response)
            assertEquals("REJECTED", result.get("outcome").textValue())
            assertEquals("INVALID_INPUT", result.get("error").get("code").textValue())
            assertEquals(ReceiptOutcome.REJECTED, OperationStore(f.resources.firestore).find(f.a, id)?.outcome)
            f.noProfile(f.a)
        }
    }

    @Test fun oneAndFifteenAsciiCharactersAreAcceptedWithVisibleCasePreserved() = scenario { f ->
        val names = listOf("_", "N" + UUID.randomUUID().toString().replace("-", "").take(14))
        for ((index, username) in names.withIndex()) {
            val uid = if (index == 0) f.a else f.b; val token = if (index == 0) f.tokenA else f.tokenB
            assertEquals(HttpStatusCode.OK, client.complete(token, username).status)
            assertEquals(username, f.root(uid)?.get("username"))
            assertEquals(username.lowercase(Locale.ROOT), f.root(uid)?.get("usernameKey"))
        }
    }

    @Test fun caseInsensitiveTakenNameRejectsOtherUidAndKeepsItsReceipt() = scenario { f ->
        val username = name()
        assertEquals(HttpStatusCode.OK, client.complete(f.tokenA, username).status)
        val id = OperationId.create(); val response = client.complete(f.tokenB, username.lowercase(Locale.ROOT), id)
        assertEquals(HttpStatusCode.Conflict, response.status)
        val rejected = body(response)
        assertEquals("REJECTED", rejected.get("outcome").textValue())
        assertEquals("USERNAME_UNAVAILABLE", rejected.get("error").get("code").textValue())
        assertEquals(rejected, body(client.complete(f.tokenB, username.lowercase(Locale.ROOT), id)))
        assertEquals(f.a.value, f.reservation(username)?.get("uid")); f.noProfile(f.b)
        val me = client.get("/v1/me") { bearerAuth(f.tokenB) }
        assertEquals(HttpStatusCode.OK, me.status); assertTrue(body(me).get("profileRequired").booleanValue())
    }

    @Test fun existingAccountKeepsItsSingleCharacterNameAndCycleAcrossNewCommands() = scenario { f ->
        val originalName = name(); val anotherName = name(); val id = OperationId.create()
        val originalResponse = client.complete(f.tokenA, originalName, id)
        assertEquals(HttpStatusCode.OK, originalResponse.status)
        val originalReceipt = body(originalResponse)
        assertEquals("CONFIRMED", originalReceipt.get("outcome").textValue())
        val root = assertNotNull(f.root(f.a)); val cycle = root["currentCycleId"]; val createdAt = root["createdAt"]
        assertEquals(HttpStatusCode.OK, client.complete(f.tokenA, anotherName).status)
        assertEquals(originalName, f.root(f.a)?.get("username")); assertEquals(cycle, f.root(f.a)?.get("currentCycleId"))
        assertEquals(createdAt, f.root(f.a)?.get("createdAt")); assertEquals(1, f.cycles(f.a).size)
        assertNull(f.reservation(anotherName)); assertEquals(originalReceipt, body(client.complete(f.tokenA, originalName, id)))
    }

    @Test fun changedContentOfSameUuidCannotRenameAccountOrReplaceReceipt() = scenario { f ->
        val username = name(); val changed = name(); val id = OperationId.create()
        val original = client.complete(f.tokenA, username, id)
        assertEquals(HttpStatusCode.OK, original.status)
        val receipt = body(original)
        val collision = client.complete(f.tokenA, changed, id)
        assertEquals(HttpStatusCode.Conflict, collision.status)
        assertEquals("OPERATION_ID_REUSED", body(collision).get("error").get("code").textValue())
        assertEquals(username, f.root(f.a)?.get("username")); assertNull(f.reservation(changed))
        assertEquals(receipt, body(client.complete(f.tokenA, username, id))); assertEquals(1, f.cycles(f.a).size)
    }

    @Test fun concurrentCaseVariantsHaveExactlyOneWinnerAndNoPartialLoserProfile() = scenario { f ->
        val username = name(); val firstId = OperationId.create(); val secondId = OperationId.create()
        val responses = coroutineScope {
            val first = async { client.complete(f.tokenA, username, firstId) }
            val second = async { client.complete(f.tokenB, username.lowercase(Locale.ROOT), secondId) }
            listOf(first.await(), second.await())
        }
        assertEquals(1, responses.count { it.status == HttpStatusCode.OK })
        assertEquals(1, responses.count { it.status == HttpStatusCode.Conflict })
        val winner = if (responses[0].status == HttpStatusCode.OK) f.a else f.b
        val loser = if (winner == f.a) f.b else f.a
        assertEquals(winner.value, f.reservation(username)?.get("uid"))
        assertEquals(1, f.cycles(winner).size); assertNotNull(f.projection(winner)); f.noProfile(loser)
        val rejected = body(responses.single { it.status == HttpStatusCode.Conflict })
        assertEquals("USERNAME_UNAVAILABLE", rejected.get("error").get("code").textValue())
    }

    @Test fun concurrentCommandsForOneUidNeverCreateTwoCharactersOrReserveTwoNames() = scenario { f ->
        val names = listOf(name(), name())
        val responses = coroutineScope { names.map { username -> async { client.complete(f.tokenA, username) } }.map { it.await() } }
        assertTrue(responses.all { it.status == HttpStatusCode.OK })
        assertEquals(1, f.cycles(f.a).size)
        assertEquals(1, names.count { f.reservation(it)?.get("uid") == f.a.value })
        assertTrue(f.root(f.a)?.get("username") in names); assertNotNull(f.projection(f.a))
    }

    @Test fun meReturnsOnlyTheAuthenticatedProfileAndAnotherClientSeesTheSameAccount() = scenario { f ->
        val nameA = name(); val nameB = name()
        assertEquals(HttpStatusCode.OK, client.complete(f.tokenA, nameA).status)
        assertEquals(HttpStatusCode.OK, client.complete(f.tokenB, nameB).status)
        val before = f.root(f.a)
        val secondClient = createClient {}
        val response = secondClient.get("/v1/me") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, response.status)
        val profile = body(response)
        assertFalse(profile.get("profileRequired").booleanValue())
        assertEquals(nameA, profile.findValue("username").textValue())
        assertFalse(profile.toString().contains(nameB)); assertFalse(profile.toString().contains(f.b.value))
        assertEquals(1, f.cycles(f.a).size)
        assertEquals(before, f.root(f.a))
    }

    @Test fun ownerInjectionAndForbiddenProfileFieldsAreRejectedBeforeAnyMutation() = scenario { f ->
        val query = client.get("/v1/me?uid=${f.b.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.BadRequest, query.status)
        for (extra in listOf("uid" to f.b.value, "xp" to 999, "createdAt" to now.toString())) {
            val id = OperationId.create()
            val response = client.post("/v1/commands") {
                bearerAuth(f.tokenA); contentType(ContentType.Application.Json)
                setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "COMPLETE_PROFILE",
                    "payload" to (mapOf("username" to name()) + extra))))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("INVALID_REQUEST", body(response).get("error").get("code").textValue())
            assertNull(OperationStore(f.resources.firestore).find(f.a, id))
        }
        assertNull(f.root(f.a)); assertNull(f.root(f.b))
    }

    @Test fun invalidIdentityDoesNotCreateProfileOrRevealPrivateData() = scenario { f ->
        val response = client.get("/v1/me") { bearerAuth("synthetic-invalid-token") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val error = body(response)
        assertEquals("AUTH_REQUIRED", error.get("error").get("code").textValue())
        assertFalse(error.toString().contains(f.a.value)); assertFalse(error.toString().contains("synthetic-invalid-token"))
        assertNull(f.root(f.a)); assertNull(f.root(f.b))
    }
}
