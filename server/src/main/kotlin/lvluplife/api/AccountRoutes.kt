package lvluplife.api

import com.google.cloud.firestore.Firestore
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.application.AuthoritativeClock
import lvluplife.application.CompleteProfile
import lvluplife.application.RecordOperations
import lvluplife.application.CreateRecord
import lvluplife.persistence.RecordStore
import lvluplife.persistence.AccountStore
import lvluplife.persistence.OperationStore
import lvluplife.persistence.RevisionTransaction
import lvluplife.persistence.OperationReceipt
import lvluplife.persistence.ReceiptOutcome

/** Dispatches account endpoints through the common authenticated/schema-checked /v1 routes. */
fun accountHandlers(firestore: Firestore, clock: AuthoritativeClock): ApiHandlers {
    val accounts = AccountStore(firestore)
    val operations = OperationStore(firestore)
    val complete = CompleteProfile(accounts, operations, RevisionTransaction(firestore, operations, clock))
    val records = RecordOperations(operations)
    val create = CreateRecord(RecordStore(firestore), records, RevisionTransaction(firestore, operations, clock))
    return ApiHandlers(command = { uid, command ->
        withContext(Dispatchers.IO) {
            if (command.type == CommandType.CREATE_RECORD) return@withContext createRecordResponse(create, uid, command)
            val receipt = when {
                command.type == CommandType.COMPLETE_PROFILE -> complete.execute(uid, command)
                RecordOperations.supports(command.type) -> records.recover(uid, command)
                    ?: throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE)
                else -> throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE)
            }
            receiptResponse(receipt)
        }
    }, query = { uid, query ->
        if (query != ApiQuery.Me) throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE)
        withContext(Dispatchers.IO) {
            val account = accounts.read(uid)
            val body = apiJson.createObjectNode().put("profileRequired", account == null)
            if (account != null) body.putObject("profile").apply {
                put("uid", account.uid.value)
                put("username", account.username.value)
                put("createdAt", account.createdAt.toString())
                put("currentCycleId", account.currentCycleId.value)
                put("revision", account.revision.value)
                put("schemaVersion", account.schemaVersion)
            }
            ApiResponse(body)
        }
    })
}

/** Only the public receipt contract is returned, never request hashes or stored private metadata. */
internal fun receiptResponse(receipt: OperationReceipt): ApiResponse {
    val body = apiJson.createObjectNode().apply {
        put("operationId", receipt.operationId.value)
        put("outcome", receipt.outcome.name)
        put("validatedAt", receipt.validatedAt.toString())
    }
    return when (receipt.outcome) {
        ReceiptOutcome.CONFIRMED -> {
            body.set<JsonNode>("result", apiJson.valueToTree(requireNotNull(receipt.result)))
            ApiResponse(body, HttpStatusCode.OK)
        }
        ReceiptOutcome.REJECTED -> {
            val error = requireNotNull(receipt.error)
            body.putObject("error").put("code", error.name).put("message", error.safeMessage)
            ApiResponse(body, error.status)
        }
    }
}
