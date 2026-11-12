package lvluplife.security

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.net.URI
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.model.*
import lvluplife.persistence.*

/** Recovery HTTP contracts with actual local Auth verification and Firestore commits. No production fault switches. */
class FullIsolationTest {
    private val json = ObjectMapper()
    private class Identity(val uid: Uid, val token: String) { override fun toString() = "Identity(redacted)" }
    private fun identity(): Identity {
        val response = JavaHttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(
            "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"))
            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"returnSecureToken":true}""")).build(), JavaHttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val body = json.readTree(response.body())
        return Identity(Uid(body.path("localId").textValue()), body.path("idToken").textValue())
    }
    private class Fixture(val resources: ServerResources, val a: Identity, val b: Identity) {
        @Volatile var now: Instant = Instant.parse("2026-11-12T18:00:00Z")
        var failure: RuntimeException? = null
        var providerFailure: RuntimeException? = null
        val logs = CaptureLogs()
        fun projection() = resources.documents.read(a.uid, listOf("projections", "current"))!!
        fun root(identity: Identity = a) = resources.documents.read(identity.uid, emptyList())!!
        fun cycle(identity: Identity = a) = root(identity)["currentCycleId"] as String
        fun documents(collection: String, identity: Identity = a) = resources.firestore.collection("users/${identity.uid.value}/$collection").get().get(10, TimeUnit.SECONDS).documents.associate { it.id to it.data }
        fun snapshot(identity: Identity = a) = listOf(root(identity), documents("cycles",identity), documents("projections",identity), documents("records",identity), documents("activities",identity), documents("operations",identity))
        fun receipt(id: OperationId, identity: Identity = a) = OperationStore(resources.firestore).find(identity.uid, id)
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.host)
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val f = Fixture(resources, identity(), identity())
            testApplication {
                application {
                    val base = accountHandlers(resources.firestore, AuthoritativeClock { f.now })
                    serverModule(base.copy(command = { uid, command -> f.failure?.let { throw it };base.command(uid,command) },
                        query = { uid, query -> f.failure?.let { throw it };base.query(uid,query) }),
                        verifyToken = { f.providerFailure?.let { throw it }; resources.identityVerifier.verify(it)?.value })
                }
                environment { log=f.logs.logger }

                for (owner in listOf(f.a, f.b)) {
                    val response = client.post("/v1/commands") {
                        bearerAuth(owner.token); contentType(ContentType.Application.Json)
                        setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value, "type" to "COMPLETE_PROFILE",
                            "payload" to mapOf("username" to ("R" + UUID.randomUUID().toString().replace("-", "").take(12))))))
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                }
                val previousOut=System.out;val previousErr=System.err
                val console=java.io.ByteArrayOutputStream()
                java.io.PrintStream(console,true,Charsets.UTF_8).use { capture ->
                    try {
                        System.setOut(capture);System.setErr(capture)
                        System.out.print("capture-console-control")
                        block(f)
                    } finally { System.setOut(previousOut);System.setErr(previousErr) }
                }
                val captured=console.toString(Charsets.UTF_8)
                assertTrue(captured.contains("capture-console-control"))
                assertSafe(captured,f)
            }
        }
    }

    private class CaptureLogs {
        val entries=java.util.concurrent.ConcurrentLinkedQueue<String>()
        val logger=java.lang.reflect.Proxy.newProxyInstance(org.slf4j.Logger::class.java.classLoader,
            arrayOf(org.slf4j.Logger::class.java)) { proxy, method, args ->
            when {
                method.name in setOf("getName","toString") -> "security-capture"
                method.name=="hashCode" -> System.identityHashCode(proxy)
                method.name=="equals" -> proxy === args?.firstOrNull()
                method.name.startsWith("is") -> true
                else -> { entries.add(args.orEmpty().joinToString { value ->
                    if(value is Throwable) value.stackTraceToString() else if(value is Array<*>) value.contentDeepToString() else value.toString()
                });null }
            }
        } as org.slf4j.Logger
    }
    private suspend fun body(response: io.ktor.client.statement.HttpResponse)=json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: io.ktor.client.statement.HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK,response.status)
        return body(response).also { assertEquals("CONFIRMED",it.path("outcome").textValue()) }
    }
    private fun envelope(type: CommandType, cycle: String, record: String="foreign-record", activity: String="strength"): Map<String,Any?> {
        val payload: Map<String,Any?> = when(type) {
            CommandType.COMPLETE_PROFILE -> mapOf("username" to ("S"+UUID.randomUUID().toString().replace("-", "").take(12)))
            CommandType.CREATE_CUSTOM_ACTIVITY -> mapOf("name" to "CuentaPropia","category" to "Rutina","primary" to "STR")
            CommandType.CREATE_RECORD -> mapOf("activityId" to activity,"activityDate" to "2026-11-12","hours" to 0,"minutes" to 30)
            CommandType.EDIT_RECORD -> mapOf("recordId" to record,"expectedVersion" to 1,"activityDate" to "2026-11-12","activityId" to "strength","hours" to 0,"minutes" to 60)
            CommandType.DELETE_RECORD -> mapOf("recordId" to record,"expectedVersion" to 1)
            CommandType.RESET_PROGRESSION -> mapOf("confirmed" to true)
        }
        return linkedMapOf<String,Any?>("operationId" to OperationId.create().value,"type" to type.name,"payload" to payload).also {
            if(type !in setOf(CommandType.COMPLETE_PROFILE,CommandType.CREATE_CUSTOM_ACTIVITY)) it["cycleId"]=cycle
        }
    }
    private suspend fun HttpClient.send(owner: Identity?, envelope: Map<String,Any?>, token: String?=owner?.token)=post("/v1/commands") {
        if(token!=null) bearerAuth(token);contentType(ContentType.Application.Json);setBody(json.writeValueAsString(envelope))
    }
    private suspend fun seed(client: HttpClient,f: Fixture): Pair<String,String> {
        val custom=envelope(CommandType.CREATE_CUSTOM_ACTIVITY,f.cycle()).toMutableMap()
        custom["payload"]=mapOf("name" to "PrivadaSoloA","category" to "RutinaPrivada","primary" to "STR","secondary" to "DEX")
        val activity=confirmed(client.send(f.a,custom)).path("result").path("activityId").textValue()
        val record=confirmed(client.send(f.a,envelope(CommandType.CREATE_RECORD,f.cycle(),activity=activity))).path("result").path("recordId").textValue()
        assertEquals(1,f.documents("activities").size);assertEquals(1,f.documents("records").size)
        return record to activity
    }
    private fun paths(id: String)=listOf("me","catalog","progress","custom-activities","history?period=day&date=2026-11-12","operations/$id")
    private fun assertSafe(text: String,f: Fixture,privateValues: List<String> = emptyList()) {
        for(secret in listOf(f.a.token,f.b.token,"users/${f.a.uid.value}","PrivadaSoloA","RutinaPrivada")+privateValues)
            assertFalse(text.contains(secret),"Sensitive value must not appear in output")
    }
    @Test fun forgedOwnerFieldsAreRejectedForEveryCommandWithoutChangingEitherAccount()=scenario { f ->
        seed(client,f);val a=f.snapshot();val b=f.snapshot(f.b)
        for(type in CommandType.entries) for(key in listOf("uid","owner")) {
            val base=envelope(type,f.cycle(f.b))
            @Suppress("UNCHECKED_CAST") val payload=base["payload"] as Map<String,Any?>
            for(value in listOf(base+(key to f.a.uid.value),base+("payload" to (payload+(key to f.a.uid.value))))) {
                val response=client.send(f.b,value);assertEquals(HttpStatusCode.BadRequest,response.status)
                assertSafe(response.bodyAsText(),f,listOf(f.a.uid.value))
            }
        }
        assertEquals(a,f.snapshot());assertEquals(b,f.snapshot(f.b))
    }
    @Test fun guessedForeignRecordActivityAndCycleNeverMutateOrRevealTheirOwner()=scenario { f ->
        val (record,activity)=seed(client,f);val a=f.snapshot()
        val attempts=listOf(CommandType.EDIT_RECORD to "RECORD_DELETED",CommandType.DELETE_RECORD to "RECORD_DELETED",
            CommandType.CREATE_RECORD to "INVALID_INPUT",CommandType.RESET_PROGRESSION to "CYCLE_CLOSED")
        for((type,code) in attempts) {
            val command=envelope(type,if(type==CommandType.RESET_PROGRESSION) f.cycle() else f.cycle(f.b),record,activity)
            val response=client.send(f.b,command)
            assertTrue(response.status in setOf(HttpStatusCode.Conflict,HttpStatusCode.UnprocessableEntity))
            val text=response.bodyAsText();assertSafe(text,f,listOf(f.a.uid.value,record,activity,f.cycle()))
            assertEquals(code,json.readTree(text).path("error").path("code").textValue())
            assertEquals("REJECTED",json.readTree(text).path("outcome").textValue())
            assertEquals(a,f.snapshot())
        }
        assertTrue(f.documents("records",f.b).isEmpty());assertTrue(f.documents("activities",f.b).isEmpty())
        assertEquals(1,f.documents("cycles",f.b).size)
    }
    @Test fun allQueriesRejectOwnerSelectorsAndIgnoreSpoofedIdentityHeaders()=scenario { f ->
        seed(client,f);val a=f.snapshot();val operation=f.documents("operations").keys.first()
        for(path in paths(operation)) {
            val response=client.get("/v1/$path"+(if('?' in path) "&" else "?")+"uid=${f.a.uid.value}") { bearerAuth(f.b.token) }
            assertEquals(HttpStatusCode.BadRequest,response.status);assertSafe(response.bodyAsText(),f,listOf(f.a.uid.value))
            val own=client.get("/v1/$path") { bearerAuth(f.b.token);header("X-User-Id",f.a.uid.value);header("X-Owner",f.a.uid.value) }
            assertEquals(HttpStatusCode.OK,own.status);val text=own.bodyAsText();assertSafe(text,f,listOf(f.a.uid.value))
            val value=json.readTree(text)
            when {
                path=="me" -> assertEquals(f.b.uid.value,value.path("profile").path("uid").textValue())
                path=="progress" -> assertEquals(f.cycle(f.b),value.path("cycleId").textValue())
                path=="custom-activities" || path.startsWith("history") -> assertEquals(0,value.path("items").size())
                path.startsWith("operations") -> { assertEquals("UNKNOWN",value.path("outcome").textValue());assertFalse(value.has("result")) }
                path=="catalog" -> assertEquals(26,value.path("classes").size())
            }
        }
        assertEquals(a,f.snapshot())
    }
    @Test fun everyCommandAndQueryRejectsMissingAndInvalidAuthenticationBeforeAnyWrite()=scenario { f ->
        seed(client,f);val a=f.snapshot();val b=f.snapshot(f.b);val id=OperationId.create().value
        for(token in listOf<String?>(null,"synthetic-invalid-token")) {
            for(type in CommandType.entries) {
                val response=client.send(null,envelope(type,f.cycle()),token)
                assertEquals(HttpStatusCode.Unauthorized,response.status);assertSafe(response.bodyAsText(),f)
            }
            for(path in paths(id)) {
                val response=client.get("/v1/$path") { if(token!=null) bearerAuth(token) }
                assertEquals(HttpStatusCode.Unauthorized,response.status);assertSafe(response.bodyAsText(),f)
            }
        }
        assertEquals(a,f.snapshot());assertEquals(b,f.snapshot(f.b))
    }
    @Test fun sharedOperationUuidIsScopedToAuthenticatedAccountAndNeverReturnsForeignReceipt()=scenario { f ->
        seed(client,f);val a=f.snapshot();val operation=f.documents("operations").keys.first()
        val fresh=identity();val command=envelope(CommandType.COMPLETE_PROFILE,"unused")+("operationId" to operation)
        val result=confirmed(client.send(fresh,command));assertEquals(operation,result.path("operationId").textValue())
        assertSafe(result.toString(),f,listOf(f.a.uid.value))
        val ownCycle=result.path("result").path("cycleId").textValue();assertNotEquals(f.cycle(),ownCycle)
        assertEquals(a,f.snapshot());assertEquals(1,f.documents("cycles",fresh).size)
        assertNotNull(f.receipt(OperationId.parse(operation),fresh))
    }
    @Test fun foreignPathsAndEncodedStorageSelectorsCannotBypassOwnerResolution()=scenario { f ->
        val (record,activity)=seed(client,f);val a=f.snapshot();val b=f.snapshot(f.b)
        for(path in listOf("users/${f.a.uid.value}","users/${f.a.uid.value}/records/$record","activities/$activity",
            "history/${f.a.uid.value}","progress/${f.a.uid.value}","operations/%2e%2e%2f${f.a.uid.value}")) {
            val response=client.get("/v1/$path") { bearerAuth(f.b.token) }
            assertTrue(response.status in setOf(HttpStatusCode.NotFound,HttpStatusCode.BadRequest))
            assertSafe(response.bodyAsText(),f,listOf(f.a.uid.value,record,activity))
        }
        assertEquals(a,f.snapshot());assertEquals(b,f.snapshot(f.b))
    }
    @Test fun handlerFailuresNeverExposeCredentialsPayloadsOrCausesInResponsesOrApplicationLogs()=scenario { f ->
        val a=f.snapshot();val b=f.snapshot(f.b)
        f.logs.logger.info("capture-control");assertTrue(f.logs.entries.any { it.contains("capture-control") })
        val secret="private-error-marker-${UUID.randomUUID()}"
        for(failure in listOf(RuntimeException("$secret ${f.a.token}",IllegalStateException(f.b.token)),SecurityException("$secret ${f.a.token}"))) {
            f.failure=failure
            for(type in CommandType.entries) {
                val response=client.send(f.a,envelope(type,f.cycle()))
                assertEquals(if(failure is SecurityException) HttpStatusCode.Forbidden else HttpStatusCode.ServiceUnavailable,response.status)
                assertSafe(response.bodyAsText(),f,listOf(secret))
            }
            for(path in paths(OperationId.create().value)) {
                val response=client.get("/v1/$path") { bearerAuth(f.a.token) }
                assertEquals(if(failure is SecurityException) HttpStatusCode.Forbidden else HttpStatusCode.ServiceUnavailable,response.status)
                assertSafe(response.bodyAsText(),f,listOf(secret))
            }
        }
        assertSafe(f.logs.entries.joinToString("\n"),f,listOf(secret));assertEquals(a,f.snapshot());assertEquals(b,f.snapshot(f.b))
    }
    @Test fun authenticationProviderAndMalformedPayloadErrorsDoNotLogTokensOrRawInput()=scenario { f ->
        val a=f.snapshot();val secret="malformed-private-${UUID.randomUUID()}";val b=f.snapshot(f.b)
        f.logs.logger.info("capture-control");assertTrue(f.logs.entries.any { it.contains("capture-control") })
        f.providerFailure=RuntimeException("$secret ${f.a.token}")
        val unavailable=client.get("/v1/me") { bearerAuth(f.a.token) }
        assertEquals(HttpStatusCode.ServiceUnavailable,unavailable.status);assertSafe(unavailable.bodyAsText(),f,listOf(secret))
        f.providerFailure=null
        for(raw in listOf("{\"token\":\"$secret\",", """{"operationId":"$secret","type":"RESET_PROGRESSION","payload":{"confirmed":true}}""")) {
            val response=client.post("/v1/commands") { bearerAuth(f.a.token);contentType(ContentType.Application.Json);setBody(raw) }
            assertEquals(HttpStatusCode.BadRequest,response.status);assertSafe(response.bodyAsText(),f,listOf(secret))
        }
        assertSafe(f.logs.entries.joinToString("\n"),f,listOf(secret));assertEquals(a,f.snapshot());assertEquals(b,f.snapshot(f.b))
    }
}

