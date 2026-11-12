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
import lvluplife.application.CreateCustomActivity
import lvluplife.persistence.CustomActivityStore
import lvluplife.application.ReadProgress
import lvluplife.application.ReadHistory
import lvluplife.application.CorrectRecord
import lvluplife.persistence.HistoryStore
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
    val transactions = RevisionTransaction(firestore, operations, clock)
    val complete = CompleteProfile(accounts, operations, transactions)
    val custom = CustomActivityStore(firestore)
    val createCustom = CreateCustomActivity(accounts, custom, operations, transactions)
    val records = RecordOperations(operations)
    val store = RecordStore(firestore)
    val create = CreateRecord(store, records, transactions)
    val correct = CorrectRecord(store, records, transactions)
    val progress = ReadProgress(store, transactions)
    val history = ReadHistory(HistoryStore(firestore))
    return ApiHandlers(command = { uid, command ->
        withContext(Dispatchers.IO) {
            if (command.type == CommandType.CREATE_RECORD) return@withContext createRecordResponse(create, uid, command)
            if (command.type in setOf(CommandType.EDIT_RECORD, CommandType.DELETE_RECORD))
                return@withContext correctionResponse(correct, uid, command)
            val receipt = when {
                command.type == CommandType.COMPLETE_PROFILE -> complete.execute(uid, command)
                command.type == CommandType.CREATE_CUSTOM_ACTIVITY -> createCustom.execute(uid, command)
                else -> throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE)
            }
            receiptResponse(receipt)
        }
    }, query = { uid, query ->
        withContext(Dispatchers.IO) {
            when (query) {
                ApiQuery.Catalog -> return@withContext catalogResponse()
                ApiQuery.Progress -> return@withContext progressResponse(progress, uid)
                ApiQuery.CustomActivities -> return@withContext customActivitiesResponse(custom, uid)
                is ApiQuery.History -> return@withContext historyResponse(history, store, uid, query)
                is ApiQuery.Operation -> return@withContext operationResponse(operations, uid, query.operationId)
                ApiQuery.Me -> Unit
            }
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
