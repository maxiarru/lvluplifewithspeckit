package lvluplife.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.Application
import com.google.firebase.auth.FirebaseAuth
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import lvluplife.model.OperationId
import lvluplife.model.Uid

private fun verifyFirebaseToken(token: String): String? =
    FirebaseIdentityVerifier(FirebaseAuth.getInstance()).verify(token)?.value

data class ApiResponse(val body: JsonNode, val status: HttpStatusCode = HttpStatusCode.OK)
enum class HistoryPeriod { day, week, month }
sealed interface ApiQuery {
    data object Me : ApiQuery
    data object Catalog : ApiQuery
    data object Progress : ApiQuery
    data object CustomActivities : ApiQuery
    data class History(val period: HistoryPeriod, val date: LocalDate) : ApiQuery
    data class Operation(val operationId: OperationId) : ApiQuery
}

data class ApiHandlers(
    val command: suspend (Uid, CommandEnvelope) -> ApiResponse = { _, _ -> throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE) },
    val query: suspend (Uid, ApiQuery) -> ApiResponse = { _, _ -> throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE) },
)

fun Application.serverModule(verifyToken: (String) -> String? = ::verifyFirebaseToken) =
    serverModule(ApiHandlers(), verifyToken)

fun Application.serverModule(handlers: ApiHandlers, verifyToken: (String) -> String? = ::verifyFirebaseToken) {
    installApiErrors()
    installIdentityGuard(verifyToken)
    routing {
        route("/v1") {
            post("/commands") {
                call.requireQueryKeys(emptySet())
                val command = parseCommand(call.receiveBoundedJson())
                call.respondApi(handlers.command(call.verifiedUid, command))
            }
            for ((path, query) in listOf("me" to ApiQuery.Me, "catalog" to ApiQuery.Catalog,
                "progress" to ApiQuery.Progress, "custom-activities" to ApiQuery.CustomActivities)) {
                get("/$path") {
                    call.requireQueryKeys(emptySet())
                    call.respondApi(handlers.query(call.verifiedUid, query))
                }
            }
            get("/history") {
                call.requireQueryKeys(setOf("period", "date"))
                val period = try { HistoryPeriod.valueOf(call.request.queryParameters["period"]!!) }
                catch (_: IllegalArgumentException) { invalidRequest() }
                val date = parseDate(call.request.queryParameters["date"]!!)
                call.respondApi(handlers.query(call.verifiedUid, ApiQuery.History(period, date)))
            }
            get("/operations/{operationId}") {
                call.requireQueryKeys(emptySet())
                val id = parseOperationId(call.parameters["operationId"] ?: invalidRequest())
                call.respondApi(handlers.query(call.verifiedUid, ApiQuery.Operation(id)))
            }
        }
    }
}

fun main() {
    val config = ServerConfig.fromEnvironment()
    ServerResources.open(config).use { resources ->
        // Data handlers remain unavailable until their application services are implemented.
        val engine = embeddedServer(Netty, port = config.port) {
            serverModule { resources.identityVerifier.verify(it)?.value }
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            try { engine.stop(500, 3000) } finally { resources.close() }
        })
        engine.start(wait = true)
    }
}

private fun ApplicationCall.requireQueryKeys(expected: Set<String>) {
    val parameters = request.queryParameters
    if (parameters.names() != expected || expected.any { parameters.getAll(it)?.size != 1 }) invalidRequest()
}

private suspend fun ApplicationCall.respondApi(response: ApiResponse) =
    respondText(apiJson.writeValueAsString(response.body), ContentType.Application.Json, response.status)

// Transport bound per command, not a limit on history size or number of records.
private const val MAX_COMMAND_BYTES = 65_536

private suspend fun ApplicationCall.receiveBoundedJson(): String {
    val type = try { request.contentType() } catch (_: IllegalArgumentException) { invalidRequest() }
    if (type.withoutParameters() != ContentType.Application.Json ||
        type.parameter("charset")?.let { !it.equals("utf-8", ignoreCase = true) } == true) invalidRequest()
    if ((request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) > MAX_COMMAND_BYTES) {
        throw ApiException(ApiError.REQUEST_TOO_LARGE)
    }
    val input = receiveChannel()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.readAvailable(buffer)
        if (count < 0) break
        if (output.size() + count > MAX_COMMAND_BYTES) throw ApiException(ApiError.REQUEST_TOO_LARGE)
        output.write(buffer, 0, count)
    }
    return try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
    } catch (_: CharacterCodingException) { invalidRequest() }
}
