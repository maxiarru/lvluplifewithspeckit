package lvluplife.api

import io.ktor.http.HttpStatusCode
import lvluplife.model.OperationId
import lvluplife.model.Uid
import lvluplife.persistence.OperationStore

internal fun operationResponse(store: OperationStore, uid: Uid, id: OperationId): ApiResponse {
    val receipt = store.find(uid, id)
    return if (receipt == null) ApiResponse(apiJson.createObjectNode().put("operationId", id.value).put("outcome", "UNKNOWN"))
    else receiptResponse(receipt).copy(status = HttpStatusCode.OK)
}
