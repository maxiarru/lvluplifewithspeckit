package lvluplife.api

import lvluplife.application.ReadProgress
import lvluplife.application.RecordOperations
import lvluplife.model.Progression
import lvluplife.model.ProgressionThresholds
import lvluplife.model.Uid

internal fun progressResponse(service: ReadProgress, uid: Uid): ApiResponse =
    ApiResponse(apiJson.valueToTree(progressPayload(service.execute(uid))))

/** GET /progress only: adds the canonical next-level requirement (null at level 50). Receipts and
 * stored projections keep using progressionResult unchanged, so idempotent replays are unaffected. */
fun progressPayload(progression: Progression): Map<String, Any?> {
    val base = RecordOperations.progressionResult(progression)
    @Suppress("UNCHECKED_CAST")
    val published = base.getValue("attributes") as Map<String, Map<String, Any?>>
    val attributes = progression.attributes.entries.associate { (attribute, state) ->
        attribute.name to published.getValue(attribute.name) +
            ("xpRequired" to ProgressionThresholds.attribute(state.attribute.level)?.toLong())
    }
    return base + ("attributes" to attributes)
}
