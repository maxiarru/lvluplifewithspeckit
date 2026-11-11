package lvluplife.api

import lvluplife.application.ReadProgress
import lvluplife.application.RecordOperations
import lvluplife.model.Uid

internal fun progressResponse(service: ReadProgress, uid: Uid): ApiResponse =
    ApiResponse(apiJson.valueToTree(RecordOperations.progressionResult(service.execute(uid))))
