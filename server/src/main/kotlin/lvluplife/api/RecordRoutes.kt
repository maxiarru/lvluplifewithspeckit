package lvluplife.api

import lvluplife.application.CreateRecord
import lvluplife.model.Uid

/** Called on the common handler's IO dispatcher, after authentication and strict request parsing. */
internal fun createRecordResponse(service: CreateRecord, uid: Uid, command: CommandEnvelope): ApiResponse =
    receiptResponse(service.execute(uid, command))
