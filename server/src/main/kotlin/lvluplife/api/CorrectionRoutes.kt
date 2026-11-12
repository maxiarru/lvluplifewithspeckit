package lvluplife.api

import lvluplife.application.CorrectRecord
import lvluplife.model.Uid

/** Common authentication/schema parsing precedes this IO-dispatched adapter.
 * The service recovers the original receipt before revalidating version, cycle or time.
 */
internal fun correctionResponse(service: CorrectRecord, uid: Uid, command: CommandEnvelope): ApiResponse =
    receiptResponse(service.execute(uid, command))
