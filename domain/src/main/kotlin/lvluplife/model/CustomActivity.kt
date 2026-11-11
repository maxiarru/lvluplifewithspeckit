package lvluplife.model

import java.time.Instant

/** Immutable owned definition; identity and creation time are supplied by the server. */
class CustomActivity private constructor(val uid: Uid, val definition: ActivityDefinition, val createdAt: Instant) {
    val id: ActivityId get() = definition.id
    companion object {
        fun create(uid: Uid, id: ActivityId, name: String, category: String, primary: Attribute,
            secondary: Attribute? = null, createdAt: Instant): CustomActivity {
            require(ActivityCatalog.find(id) == null) { "Custom activity identity is reserved for the basic catalogue" }
            val definition = ActivityDefinition(id, name.trim(), category, ActivityMode.DURATION,
                primary, secondary, ExperienceRule.DURATION)
            return CustomActivity(uid, definition, createdAt)
        }
    }
}
