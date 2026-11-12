package lvluplife.api

import lvluplife.application.ReadHistory
import lvluplife.model.Uid
import lvluplife.persistence.RecordStore
import lvluplife.application.AuthoritativeClock

/** Called only after the common route authenticates the UID and validates the requested period/date. */
internal fun historyResponse(service: ReadHistory, records: RecordStore, uid: Uid, query: ApiQuery.History, clock: AuthoritativeClock): ApiResponse {
    val result = service.execute(uid, query.period, query.date)
    return ApiResponse(apiJson.valueToTree(mapOf(
        "periodStart" to result.periodStart.toString(),
        "periodEndExclusive" to result.periodEndExclusive.toString(),
        "revision" to result.revision.value,
        "complete" to true,
        "observedAt" to clock.now().toString(),
        "items" to result.items.map { records.fields(it) + ("recordId" to it.id.value) },
    )))
}
