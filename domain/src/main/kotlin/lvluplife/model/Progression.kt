package lvluplife.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections
import java.util.EnumMap
import java.util.Locale

/** Canonical bounds for normalized balances. T032 reuses these thresholds for transitions. */
object ProgressionThresholds {
    fun attribute(level: Int): Int? {
        require(level in 1..50) { "Invalid attribute level" }
        return if (level == 50) null else 30 * level
    }
    fun general(level: Int): Int? {
        require(level in 1..50) { "Invalid general level" }
        return when (level) { 1 -> 1; in 2..4 -> 2; in 5..8 -> 3; in 9..14 -> 4; in 15..49 -> 5; else -> null }
    }
}
object BusinessTime {
    val offset: ZoneOffset = ZoneOffset.ofHours(-3)
    fun date(instant: Instant): LocalDate = instant.atOffset(offset).toLocalDate()
}
data class AttributeBalance(val level: Int, val xp: Int) {
    init {
        val required = ProgressionThresholds.attribute(level)
        require(xp >= 0 && (if (required == null) xp == 0 else xp < required)) { "Attribute XP must be normalized" }
    }
}
data class GeneralBalance(val level: Int, val remaining: Int) {
    init {
        val required = ProgressionThresholds.general(level)
        require(remaining >= 0 && (if (required == null) remaining == 0 else remaining < required)) { "General counter must be normalized" }
    }
}
data class InactivityBalance(val attribute: AttributeBalance, val inactiveDates: Int) {
    init { require(inactiveDates >= 0) { "Invalid inactivity count" } }
}

/** A server-derived snapshot with no public constructor/copy or mutation API.
 * Factory arguments are trusted replay results, not client command fields.
 */
class Progression private constructor(val uid: Uid, val cycleId: CycleId, val revision: Revision,
    val computedAt: Instant, val businessDate: LocalDate, val attributes: Map<Attribute, InactivityBalance>,
    val general: GeneralBalance, val classId: String, val stage: Int) {
    companion object {
        fun derived(cycle: Cycle, revision: Revision, computedAt: Instant, attributes: Map<Attribute, InactivityBalance>,
            general: GeneralBalance, classId: String, stage: Int): Progression =
            run {
                require(cycle.isOpen) { "Current progression requires an open cycle" }
                require(revision.value > 0) { "Published progression requires a positive revision" }
                require(!computedAt.isBefore(cycle.startedAt)) { "Progression cannot predate its cycle" }
                require(attributes.keys == Attribute.entries.toSet()) { "Progression requires all six attributes" }
                val gained = attributes.values.sumOf { it.attribute.level - 1 }
                val consumed = (1 until general.level).sumOf { ProgressionThresholds.general(it)!! }
                require(if (general.level == 50) gained >= consumed else gained == consumed + general.remaining) {
                    "General progress must match attribute level gains"
                }
                require(classId.isNotBlank() && classId.none(Char::isISOControl)) { "Invalid class identity" }
                require(stage in 1..3 && (classId != "novato" || stage == 1)) { "Invalid class stage" }
                val snapshot = Collections.unmodifiableMap(EnumMap<Attribute, InactivityBalance>(Attribute::class.java).apply { putAll(attributes) })
                Progression(cycle.uid, cycle.id, revision, computedAt, BusinessTime.date(computedAt), snapshot, general, classId, stage)
            }
        fun initial(cycle: Cycle, revision: Revision): Progression {
            val initial = InitialCharacter
            return derived(cycle, revision, cycle.startedAt,
                initial.attributes.mapValues { (_, value) -> InactivityBalance(AttributeBalance(value.level, value.xp), 0) },
                GeneralBalance(initial.generalLevel, initial.generalRemainder), initial.className.lowercase(Locale.ROOT), initial.stage)
        }
    }
}
