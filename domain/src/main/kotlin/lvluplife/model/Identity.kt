package lvluplife.model

import java.util.UUID

@JvmInline
value class Uid(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128)
        require(value != "." && value != ".." && '/' !in value)
        require(value.none { it.isISOControl() })
    }
}

@JvmInline
value class OperationId private constructor(val value: String) {
    companion object {
        private val canonical = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        fun parse(value: String): OperationId {
            require(canonical.matches(value))
            return OperationId(UUID.fromString(value).toString())
        }
        fun create(): OperationId = OperationId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class Revision(val value: Long) {
    init { require(value >= 0) }
    fun next(): Revision = Revision(Math.addExact(value, 1))
}
