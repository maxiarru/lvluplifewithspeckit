package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import com.google.cloud.firestore.DocumentSnapshot
import com.google.api.core.ApiFuture
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import lvluplife.api.ApiError
import lvluplife.api.ApiException
import lvluplife.api.CommandEnvelope
import lvluplife.api.CommandType
import lvluplife.model.OperationId
import lvluplife.model.Revision
import lvluplife.model.Uid

enum class ReceiptOutcome { CONFIRMED, REJECTED }
data class OperationReceipt(
    val operationId: OperationId,
    val commandType: CommandType,
    val requestHash: String,
    val outcome: ReceiptOutcome,
    val validatedAt: Instant,
    val revision: Revision,
    val cycleId: String?,
    val result: Map<String, Any?>? = null,
    val error: ApiError? = null,
)

/** Receipt IO only. Application mutations must stage create inside RevisionTransaction's atomic commit. */
class OperationStore(private val firestore: Firestore) {
    private val json = ObjectMapper()

    /** Deterministic UTF-8 JSON for the validated command. UUID and transport credentials are excluded. */
    fun canonicalHash(command: CommandEnvelope): String {
        require(command.payload.isObject) { "Expected command payload object" }
        val canonical = json.createObjectNode().apply {
            put("type", command.type.name)
            put("cycleId", command.cycleId)
            set<JsonNode>("payload", canonicalNode(command.payload))
        }
        return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    fun find(uid: Uid, operationId: OperationId): OperationReceipt? = safeRead {
        decode(await(reference(uid, operationId).get()), operationId)
    }

    /** Read before staging any write. An existing receipt wins over subsequent application validation. */
    fun read(transaction: Transaction, uid: Uid, command: CommandEnvelope): OperationReceipt? {
        val receipt = safeRead { decode(await(transaction.get(reference(uid, command.operationId))), command.operationId) }
        return match(command, receipt)
    }

    /** Keep SDK retryable errors intact inside its callback; RevisionTransaction sanitizes final failures. */
    internal fun readForCommit(transaction: Transaction, uid: Uid, command: CommandEnvelope): OperationReceipt? =
        match(command, decode(await(transaction.get(reference(uid, command.operationId))), command.operationId))

    private fun match(command: CommandEnvelope, receipt: OperationReceipt?): OperationReceipt? {
        val hash = canonicalHash(command)
        if (receipt != null && (receipt.requestHash != hash || receipt.commandType != command.type || receipt.cycleId != command.cycleId)) {
            throw ApiException(ApiError.OPERATION_ID_REUSED)
        }
        return receipt
    }

    /** create carries an exists=false precondition; no set/update/delete API or expiry is provided. */
    fun create(transaction: Transaction, uid: Uid, receipt: OperationReceipt) {
        validate(receipt)
        val fields = linkedMapOf<String, Any?>(
            "commandType" to receipt.commandType.name,
            "requestHash" to receipt.requestHash,
            "outcome" to receipt.outcome.name,
            // RFC3339 text preserves the exact authoritative Instant across receipt replays.
            "validatedAt" to receipt.validatedAt.toString(),
            "appliedRevision" to receipt.revision.value,
        )
        receipt.cycleId?.let { fields["cycleId"] = it }
        receipt.result?.let { fields["result"] = copyResult(it) }
        receipt.error?.let { fields["errorCode"] = it.name }
        safeRead { transaction.create(reference(uid, receipt.operationId), fields); Unit }
    }

    private fun reference(uid: Uid, id: OperationId) = firestore.document("users/${uid.value}/operations/${id.value}")

    private fun canonicalNode(value: JsonNode): JsonNode = when {
        value.isObject -> json.createObjectNode().also { result ->
            value.fieldNames().asSequence().sorted().forEach { key -> result.set<JsonNode>(key, canonicalNode(value.get(key))) }
        }
        value.isArray -> json.createArrayNode().also { result -> value.forEach { result.add(canonicalNode(it)) } }
        else -> value.deepCopy<JsonNode>()
    }

    private fun decode(document: DocumentSnapshot, id: OperationId): OperationReceipt? {
        if (!document.exists()) return null
        val fields = requireNotNull(document.data)
        require(fields.keys.all { it in receiptFields }) { "Invalid receipt fields" }
        val cycle = fields["cycleId"]
        require(cycle == null || cycle is String) { "Invalid receipt cycle" }
        val error = fields["errorCode"]?.let { ApiError.valueOf(it as String) }
        val result = fields["result"]?.let { copyResult(it) }
        return OperationReceipt(id, CommandType.valueOf(fields["commandType"] as String),
            fields["requestHash"] as String, ReceiptOutcome.valueOf(fields["outcome"] as String),
            Instant.parse(fields["validatedAt"] as String), Revision(fields["appliedRevision"] as Long),
            cycle as String?, result, error).also(::validate)
    }

    private fun validate(receipt: OperationReceipt) {
        require(Regex("[0-9a-f]{64}").matches(receipt.requestHash)) { "Invalid receipt hash" }
        require(receipt.revision.value > 0) { "Invalid receipt revision" }
        val requiresCycle = receipt.commandType !in setOf(CommandType.COMPLETE_PROFILE, CommandType.CREATE_CUSTOM_ACTIVITY)
        require(requiresCycle == (receipt.cycleId != null)) { "Invalid receipt cycle" }
        receipt.cycleId?.let { require(it.isNotBlank() && '/' !in it && it.none(Char::isISOControl)) { "Invalid receipt cycle" } }
        when (receipt.outcome) {
            ReceiptOutcome.CONFIRMED -> require(receipt.result != null && receipt.error == null) { "Invalid confirmed receipt" }
            ReceiptOutcome.REJECTED -> require(receipt.result == null && receipt.error in functionalErrors) { "Invalid rejected receipt" }
        }
        receipt.result?.let(::copyResult)
    }

    private fun copyResult(value: Any): Map<String, Any?> {
        require(value is Map<*, *>) { "Invalid receipt result" }
        return value.entries.associate { (key, item) ->
            require(key is String && key.lowercase(java.util.Locale.ROOT).replace("_", "").replace("-", "") !in credentialKeys) {
                "Invalid receipt result key"
            }
            key to copyValue(item)
        }
    }

    private fun copyValue(value: Any?): Any? = when (value) {
        null, is String, is Boolean, is Long -> value
        is Byte, is Short, is Int -> (value as Number).toLong()
        is Double -> value.also { require(it.isFinite()) { "Invalid receipt number" } }
        is Float -> value.toDouble().also { require(it.isFinite()) { "Invalid receipt number" } }
        is Map<*, *> -> copyResult(value)
        is List<*> -> value.map(::copyValue)
        else -> throw IllegalArgumentException("Invalid receipt value")
    }

    private fun <T> await(future: ApiFuture<T>): T = try { future.get(10, TimeUnit.SECONDS) }
    catch (failure: ExecutionException) { future.cancel(true); throw (failure.cause ?: failure) }
    catch (failure: Exception) { future.cancel(true); throw failure }

    private fun <T> safeRead(block: () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw PersistenceUnavailable() }
    catch (_: Exception) { throw PersistenceUnavailable() }

    private companion object {
        val receiptFields = setOf("commandType", "requestHash", "cycleId", "outcome", "result", "validatedAt", "appliedRevision", "errorCode")
        val functionalErrors = setOf(ApiError.USERNAME_UNAVAILABLE, ApiError.RECORD_STALE, ApiError.RECORD_DELETED,
            ApiError.CYCLE_CLOSED, ApiError.INVALID_INPUT, ApiError.DATE_NOT_ALLOWED, ApiError.EDIT_WINDOW_EXPIRED, ApiError.DAILY_DURATION_EXCEEDED)
        val credentialKeys = setOf("authorization", "token", "idtoken", "accesstoken", "refreshtoken", "bearer", "password", "credentials", "privatekey", "clientsecret")
    }
}
