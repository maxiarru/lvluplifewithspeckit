package lvluplife.api

import lvluplife.model.ActivityCatalog
import lvluplife.progression.ClassCatalog

/** Versioned domain data only; resolvable image resources are delivered in US5. */
internal fun catalogResponse(): ApiResponse = ApiResponse(apiJson.valueToTree(mapOf(
    "activities" to ActivityCatalog.activities.map { activity -> mapOf("id" to activity.id.value, "name" to activity.name,
        "category" to activity.category, "mode" to activity.mode.name, "primary" to activity.primary.name,
        "secondary" to activity.secondary?.name) },
    "classes" to ClassCatalog.classes.map { definition -> mapOf("id" to definition.id, "name" to definition.name,
        "attributes" to definition.attributes.map { it.name }, "balancedCount" to definition.balancedCount,
        "initialOnly" to definition.initialOnly, "stages" to definition.stages) },
)))
