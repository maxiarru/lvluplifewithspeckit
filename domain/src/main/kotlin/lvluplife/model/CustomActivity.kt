package lvluplife.model

import java.time.Instant

/** T045 compile-only contract. Validation/model creation is implemented in T048. */
class CustomActivity private constructor(val uid: Uid, val definition: ActivityDefinition, val createdAt: Instant) {
    val id: ActivityId get() = definition.id
    companion object {
        fun create(uid: Uid, id: ActivityId, name: String, category: String, primary: Attribute,
            secondary: Attribute? = null, createdAt: Instant): CustomActivity =
            throw NotImplementedError("Custom activity validation is not implemented (T048)")
    }
}
