package lvluplife.model

import java.time.Instant

@JvmInline
value class CycleId(val value: String) {
    init {
        require(value.isNotBlank() && value != "." && value != ".." && '/' !in value &&
            value.none(Char::isISOControl) && value.toByteArray(Charsets.UTF_8).size <= 1500) { "Invalid cycle identity" }
    }
}

@JvmInline
value class RulesVersion(val value: Int) {
    init { require(value > 0) { "Invalid rules version" } }
    companion object { val CURRENT = RulesVersion(1) }
}

data class Cycle(val uid: Uid, val id: CycleId, val startedAt: Instant,
    val rulesVersion: RulesVersion = RulesVersion.CURRENT, val closedAt: Instant? = null) {
    init { require(closedAt == null || !closedAt.isBefore(startedAt)) { "Cycle closes before its start" } }
    val isOpen: Boolean get() = closedAt == null
}
