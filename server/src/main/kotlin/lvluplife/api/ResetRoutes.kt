package lvluplife.api

import lvluplife.application.ResetProgression
import lvluplife.model.Uid

/** Authentication/schema checks and IO dispatch are shared with every other command. */
internal fun resetResponse(service: ResetProgression, uid: Uid, command: CommandEnvelope): ApiResponse =
    receiptResponse(service.execute(uid,command))
