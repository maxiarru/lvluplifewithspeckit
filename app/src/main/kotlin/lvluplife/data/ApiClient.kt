package lvluplife.data

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import lvluplife.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class SessionToken(val uid: String, internal val idToken: String) {
    override fun toString(): String = "SessionToken(redacted)"
}
fun interface SessionTokenProvider { fun current(): SessionToken? }

enum class ApiCommandType { COMPLETE_PROFILE, CREATE_CUSTOM_ACTIVITY, CREATE_RECORD, EDIT_RECORD, DELETE_RECORD, RESET_PROGRESSION }
data class ApiCommand(val operationId: String, val type: ApiCommandType, val payload: Map<String, Any?>, val cycleId: String? = null)
enum class HistoryPeriod { day, week, month }
sealed interface ApiQuery {
    data object Me : ApiQuery
    data object Progress : ApiQuery
    data object Catalog : ApiQuery
    data object CustomActivities : ApiQuery
    data class History(val period: HistoryPeriod, val date: LocalDate) : ApiQuery
}
enum class RemoteOutcome { CONFIRMED, REJECTED }
enum class FailureAction { SUSPEND_AUTH, STOP, RESOLVE_OPERATION, RETRY_QUERY, CORRECT_INPUT }
enum class ApiFailureCode {
    INVALID_REQUEST, AUTH_REQUIRED, FORBIDDEN, USERNAME_UNAVAILABLE, OPERATION_ID_REUSED,
    RECORD_STALE, RECORD_DELETED, CYCLE_CLOSED, INVALID_INPUT, DATE_NOT_ALLOWED, EDIT_WINDOW_EXPIRED,
    DAILY_DURATION_EXCEEDED, REQUEST_TOO_LARGE, TEMPORARILY_UNAVAILABLE, TIMEOUT, NETWORK_ERROR,
    UNREADABLE_RESPONSE, UNKNOWN_ERROR,
}
data class RemoteReceipt(val operationId: String, val outcome: RemoteOutcome, val validatedAt: Instant,
    val result: JsonNode?, val errorCode: ApiFailureCode?)
sealed interface ApiReply {
    data class Receipt(val value: RemoteReceipt) : ApiReply
    data class Data(val body: JsonNode) : ApiReply
    data object ReceiptAbsent : ApiReply
    data class Failure(val code: ApiFailureCode, val action: FailureAction, val mayHaveCommitted: Boolean) : ApiReply {
        val retryAutomatically: Boolean get() = false
    }
}

/** Blocking API adapter; session/coordinator layers own suspension, persistence and explicit retries. */
class ApiClient internal constructor(private val endpoint: ApiEndpoint, private val tokens: SessionTokenProvider, private val transport: HttpTransport) {
    companion object {
        private val json = ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        private val forbiddenKeys = setOf("uid", "owner", "xp", "level", "classid", "createdat", "authorization",
            "token", "idtoken", "accesstoken", "refreshtoken", "credentials", "password", "privatekey", "clientsecret")

        fun create(baseUrl: String, tokens: SessionTokenProvider): ApiClient {
            val endpoint = ApiEndpoint.from(baseUrl, BuildConfig.DEBUG)
            return ApiClient(endpoint, tokens, OkHttpTransport(endpoint))
        }

        fun encode(command: ApiCommand): String {
            requireCanonicalId(command.operationId)
            val root = json.createObjectNode().put("operationId", command.operationId).put("type", command.type.name)
            command.cycleId?.let { root.put("cycleId", it) }
            validateLiterals(command.payload)
            root.set<JsonNode>("payload", json.valueToTree(command.payload))
            validateEnvelope(root)
            return json.writeValueAsString(root)
        }

        private fun requireCanonicalId(id: String) {
            val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { null }
            require(canonical == id) { "Invalid operation identity" }
        }

        private fun validateLiterals(value: Any?) {
            when (value) {
                null, is String, is Boolean, is Byte, is Short, is Int, is Long -> Unit
                is Map<*, *> -> value.forEach { (key, nested) -> require(key is String); validateLiterals(nested) }
                is List<*> -> value.forEach(::validateLiterals)
                else -> throw IllegalArgumentException("Unsupported command value")
            }
        }

        private fun validateEnvelope(root: JsonNode) {
            require(root.isObject && root.fieldNames().asSequence().toSet().let {
                it.containsAll(setOf("operationId", "type", "payload")) && (it - setOf("operationId", "type", "payload", "cycleId")).isEmpty()
            }) { "Invalid command envelope" }
            require(root.path("operationId").isTextual && root.path("type").isTextual && root.path("payload").isObject) { "Invalid command shape" }
            requireCanonicalId(root.get("operationId").textValue())
            val kind = ApiCommandType.valueOf(root.get("type").textValue())
            val cycleRequired = kind !in setOf(ApiCommandType.COMPLETE_PROFILE, ApiCommandType.CREATE_CUSTOM_ACTIVITY)
            require(if (cycleRequired) root.path("cycleId").isTextual && root.get("cycleId").textValue().isNotBlank() else !root.has("cycleId")) {
                "Invalid command cycle"
            }
            val stack = java.util.ArrayDeque<JsonNode>()
            stack.add(root.get("payload"))
            while (stack.isNotEmpty()) {
                val value = stack.removeLast()
                if (value.isObject) value.fields().forEach { (key, nested) ->
                    require(key.lowercase(java.util.Locale.ROOT).replace("_", "").replace("-", "") !in forbiddenKeys) { "Forbidden command field" }
                    stack.add(nested)
                } else if (value.isArray) value.forEach { stack.add(it) }
            }
        }
    }

    fun send(uid: String, envelope: String): ApiReply {
        val operationId = try {
            val root = json.readTree(envelope)
            validateEnvelope(root)
            root.get("operationId").textValue()
        } catch (_: Exception) {
            return ApiReply.Failure(ApiFailureCode.INVALID_REQUEST, FailureAction.CORRECT_INPUT, false)
        }
        // Send original bytes/text, never regenerate a UUID or rewrite a persisted retry.
        return execute(uid, "POST", listOf("commands"), emptyList(), envelope, operationId, lookup = false)
    }

    fun lookup(uid: String, operationId: String): ApiReply {
        try { requireCanonicalId(operationId) } catch (_: IllegalArgumentException) {
            return ApiReply.Failure(ApiFailureCode.INVALID_REQUEST, FailureAction.STOP, false)
        }
        return execute(uid, "GET", listOf("operations", operationId), emptyList(), null, operationId, lookup = true)
    }

    fun query(uid: String, query: ApiQuery): ApiReply {
        val path = when (query) {
            ApiQuery.Me -> "me"; ApiQuery.Progress -> "progress"; ApiQuery.Catalog -> "catalog"
            ApiQuery.CustomActivities -> "custom-activities"; is ApiQuery.History -> "history"
        }
        val parameters = if (query is ApiQuery.History) listOf("period" to query.period.name, "date" to query.date.toString()) else emptyList()
        return execute(uid, "GET", listOf(path), parameters, null, null, lookup = false)
    }

    private fun execute(uid: String, method: String, path: List<String>, parameters: List<Pair<String, String>>,
        body: String?, operationId: String?, lookup: Boolean): ApiReply {
        val mutation = method == "POST"
        val session = try { tokens.current() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return failure(ApiFailureCode.TEMPORARILY_UNAVAILABLE, mutation, sent = false) }
        if (session == null || session.uid != uid || session.idToken.isEmpty() || session.idToken.any { it.code !in 33..126 }) {
            return failure(ApiFailureCode.AUTH_REQUIRED, mutation, sent = false)
        }
        val url = endpoint.base.newBuilder().addPathSegment("v1").apply {
            path.forEach { addPathSegment(it) }; parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = TransportRequest(method, url, mapOf("Authorization" to "Bearer ${session.idToken}", "Accept" to "application/json"), body)
        val response = try { transport.execute(request) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: InterruptedIOException) { return failure(ApiFailureCode.TIMEOUT, mutation) }
        catch (_: IOException) { return failure(ApiFailureCode.NETWORK_ERROR, mutation) }
        catch (_: Exception) { return failure(ApiFailureCode.UNKNOWN_ERROR, mutation) }
        when {
            response.status == 401 -> return failure(ApiFailureCode.AUTH_REQUIRED, mutation)
            response.status == 403 -> return failure(ApiFailureCode.FORBIDDEN, mutation)
            response.status == 400 -> return ApiReply.Failure(ApiFailureCode.INVALID_REQUEST, FailureAction.CORRECT_INPUT, false)
            response.status == 413 -> return ApiReply.Failure(ApiFailureCode.REQUEST_TOO_LARGE, FailureAction.CORRECT_INPUT, false)
            response.status >= 500 || response.status == 429 -> return failure(ApiFailureCode.TEMPORARILY_UNAVAILABLE, mutation)
            response.status in 300..399 -> return ApiReply.Failure(ApiFailureCode.UNREADABLE_RESPONSE, FailureAction.STOP, mutation)
        }
        return try {
            val mediaType = response.contentType.toMediaTypeOrNull()
            require(mediaType?.type == "application" && mediaType.subtype == "json" &&
                (mediaType.charset() ?: Charsets.UTF_8) == Charsets.UTF_8)
            val root = json.readTree(response.body)
            require(root != null && root.isObject)
            if (operationId != null) {
                if (lookup && response.status == 200 && root.path("outcome").asText() == "UNKNOWN") {
                    require(!root.has("operationId") || root.get("operationId").asText() == operationId)
                    ApiReply.ReceiptAbsent
                } else parseReceipt(root, operationId, response.status, lookup)
            } else {
                require(response.status == 200 && !root.has("error"))
                ApiReply.Data(root)
            }
        } catch (_: Exception) { failure(ApiFailureCode.UNREADABLE_RESPONSE, mutation) }
    }

    private fun parseReceipt(root: JsonNode, id: String, status: Int, lookup: Boolean): ApiReply.Receipt {
        require(root.path("operationId").isTextual && root.get("operationId").textValue() == id)
        require(root.path("outcome").isTextual && root.path("validatedAt").isTextual)
        val outcome = RemoteOutcome.valueOf(root.get("outcome").textValue())
        val instant = Instant.parse(root.get("validatedAt").textValue())
        val result: JsonNode?
        val error: ApiFailureCode?
        if (outcome == RemoteOutcome.CONFIRMED) {
            require(status == 200 && root.path("result").isObject && !root.has("error"))
            result = root.get("result"); error = null
        } else {
            require(status in setOf(409, 422) || (lookup && status == 200))
            require(root.path("error").path("code").isTextual)
            result = null
            error = try { ApiFailureCode.valueOf(root.get("error").get("code").textValue()) }
            catch (_: IllegalArgumentException) { ApiFailureCode.UNKNOWN_ERROR }
        }
        return ApiReply.Receipt(RemoteReceipt(id, outcome, instant, result, error))
    }

    private fun failure(code: ApiFailureCode, mutation: Boolean, sent: Boolean = true): ApiReply.Failure {
        val action = when (code) {
            ApiFailureCode.AUTH_REQUIRED -> FailureAction.SUSPEND_AUTH
            ApiFailureCode.FORBIDDEN -> FailureAction.STOP
            else -> if (mutation) FailureAction.RESOLVE_OPERATION else FailureAction.RETRY_QUERY
        }
        return ApiReply.Failure(code, action, mutation && sent)
    }
}
