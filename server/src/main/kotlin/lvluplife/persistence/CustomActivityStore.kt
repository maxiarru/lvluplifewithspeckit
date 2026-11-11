package lvluplife.persistence

import lvluplife.model.CustomActivity
import lvluplife.model.Revision

/** Pure serialization/commit plan. Writes are performed only by RevisionTransaction under the verified UID. */
class CustomActivityStore {
    fun fields(activity: CustomActivity): Map<String, Any?> = mapOf(
        "name" to activity.definition.name,
        "category" to activity.definition.category,
        "primary" to activity.definition.primary.name,
        "secondary" to activity.definition.secondary?.name,
        "createdAt" to activity.createdAt.toString(),
    )

    fun creationPlan(activity: CustomActivity, revision: Revision) = OperationDecision.Confirmed(
        rootFields = emptyMap(),
        writes = listOf(DocumentWrite(listOf("activities", activity.id.value), fields(activity))),
        result = mapOf("activityId" to activity.id.value, "createdAt" to activity.createdAt.toString(), "revision" to revision.value),
    )
}
