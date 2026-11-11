package lvluplife.api

import lvluplife.model.Uid
import lvluplife.persistence.CustomActivityStore

internal fun customActivitiesResponse(store: CustomActivityStore, uid: Uid): ApiResponse {
    val snapshot = store.list(uid)
    val body = apiJson.createObjectNode().put("revision", snapshot.revision.value).put("complete", true)
    val items = body.putArray("items")
    snapshot.items.forEach { activity ->
        items.addObject().apply {
            put("id", activity.id.value)
            put("name", activity.definition.name)
            put("category", activity.definition.category)
            put("mode", activity.definition.mode.name)
            put("primary", activity.definition.primary.name)
            put("secondary", activity.definition.secondary?.name)
            put("createdAt", activity.createdAt.toString())
        }
    }
    return ApiResponse(body)
}
