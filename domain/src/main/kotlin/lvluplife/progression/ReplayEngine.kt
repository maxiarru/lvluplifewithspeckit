package lvluplife.progression

import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import lvluplife.model.*

/** Rebuilds from canonical initial state, never from a previously penalized projection.
 * Records contain server-resolved definitions, never client-supplied XP. No persistence is represented here.
 */
object ReplayEngine {
    fun rebuild(cycle: Cycle, records: List<ActivityRecord>, now: Instant): ReplayResult {
        require(cycle.isOpen) { "Replay requires the current open cycle" }
        require(cycle.rulesVersion == RulesVersion.CURRENT) { "Unsupported progression rules" }
        require(!now.isBefore(cycle.startedAt)) { "Replay cannot precede cycle creation" }
        val businessDate = BusinessTime.date(now)
        val startDate = BusinessTime.date(cycle.startedAt)
        val current = records.filter { it.cycleId == cycle.id }
        require(current.map { it.id }.toSet().size == current.size) { "Duplicate record identity in snapshot" }
        val ordered = current.filter { it.deletedAt == null }.sortedWith(
            compareBy<ActivityRecord> { it.activityDate }.thenBy { it.createdAt }.thenBy { it.id.value })
        for (record in ordered) {
            require(!record.activityDate.isAfter(businessDate)) { "Future activity date in snapshot" }
            require(!record.createdAt.isBefore(cycle.startedAt) && !record.updatedAt.isAfter(now)) {
                "Record chronology is outside the cycle snapshot"
            }
        }

        var attributes = InitialCharacter.attributes.mapValues { (_, initial) ->
            InactivityBalance(AttributeBalance(initial.level, initial.xp), 0)
        }
        var general = GeneralBalance(InitialCharacter.generalLevel, InitialCharacter.generalRemainder)
        val recordXp = linkedMapOf<String, Map<Attribute, Int>>()
        val durations = linkedMapOf<LocalDate, Int>()
        var index = 0
        var date = minOf(startDate, ordered.firstOrNull()?.activityDate ?: startDate)
        while (true) {
            val dailyXp = mutableMapOf<Attribute, Int>()
            var remainingMinutes = 1440
            var mealCount = 0
            var sleepRemainder: SleepRemainder? = null
            while (index < ordered.size && ordered[index].activityDate == date) {
                val record = ordered[index++]
                remainingMinutes -= record.durationMinutes ?: 0
                require(remainingMinutes >= 0) { "Daily duration budget exceeded in snapshot" }
                val capped = attributes.filterValues { it.attribute.level == 50 }.keys
                val awards = when (record.definition.experienceRule) {
                    ExperienceRule.DURATION -> ExperienceRules.duration(requireNotNull(record.durationMinutes),
                        record.definition.primary, record.definition.secondary, dailyXp, capped)
                    ExperienceRule.SLEEP -> {
                        val contribution = ExperienceRules.sleep(requireNotNull(record.durationMinutes), cycle.id,
                            date, sleepRemainder, dailyXp[Attribute.MEN] ?: 0, Attribute.MEN in capped)
                        sleepRemainder = contribution.remainder
                        mapOf(Attribute.MEN to contribution.xp)
                    }
                    ExperienceRule.MEAL -> {
                        val xp = ExperienceRules.meal(mealCount, dailyXp[Attribute.CON] ?: 0, Attribute.CON in capped)
                        mealCount++
                        mapOf(Attribute.CON to xp)
                    }
                }
                val advanced = LevelRules.attributes(attributes.mapValues { it.value.attribute }, awards)
                general = LevelRules.general(general, advanced.levelsGained)
                attributes = advanced.attributes.mapValues { (attribute, balance) ->
                    InactivityBalance(balance, attributes.getValue(attribute).inactiveDates)
                }
                for ((attribute, xp) in awards) dailyXp[attribute] = (dailyXp[attribute] ?: 0) + xp
                recordXp[record.id.value] = Collections.unmodifiableMap(LinkedHashMap(awards))
            }
            if (remainingMinutes < 1440) durations[date] = 1440 - remainingMinutes
            // Backdated records in a new cycle grant XP, but cannot start inactivity before its creation date.
            if (!date.isBefore(startDate)) attributes = attributes.mapValues { (attribute, state) ->
                InactivityRules.closeDate(state, date, businessDate, dailyXp[attribute] ?: 0)
            }
            if (date == businessDate) break
            date = date.plusDays(1)
        }
        return ReplayResult(businessDate, Collections.unmodifiableMap(LinkedHashMap(attributes)), general,
            ClassRules.appearance(attributes.mapValues { it.value.attribute.level }, general.level),
            Collections.unmodifiableList(ordered.map { it.id.value }),
            Collections.unmodifiableMap(recordXp), Collections.unmodifiableMap(durations))
    }
}

data class ReplayResult(val businessDate: LocalDate, val attributes: Map<Attribute, InactivityBalance>,
    val general: GeneralBalance, val appearance: ClassAppearance, val orderedRecordIds: List<String>,
    val recordXp: Map<String, Map<Attribute, Int>>, val durationByDate: Map<LocalDate, Int>)
