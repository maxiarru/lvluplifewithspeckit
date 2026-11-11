package lvluplife.model

import java.util.Collections
import lvluplife.model.Attribute.*

internal fun requireDocumentId(value: String) {
    require(value.isNotBlank() && value != "." && value != ".." && '/' !in value && value.none(Char::isISOControl) &&
        value.toByteArray(Charsets.UTF_8).size <= 1500 && !Regex("__.*__").matches(value)) { "Invalid document identity" }
}

@JvmInline value class ActivityId(val value: String) { init { requireDocumentId(value) } }
enum class ActivityMode { DURATION, OCCURRENCE }
enum class ExperienceRule { DURATION, SLEEP, MEAL }

/** Immutable server-resolved definition; records retain a snapshot instead of trusting client attributes. */
data class ActivityDefinition(val id: ActivityId, val name: String, val category: String, val mode: ActivityMode,
    val primary: Attribute, val secondary: Attribute? = null, val experienceRule: ExperienceRule = ExperienceRule.DURATION) {
    init {
        require(name == name.trim() && name.codePointCount(0, name.length) in 1..50) { "Invalid activity name" }
        require(category.isNotBlank()) { "Invalid activity category" }
        require(secondary == null || secondary != primary) { "Activity attributes must differ" }
        when (experienceRule) {
            ExperienceRule.DURATION -> require(mode == ActivityMode.DURATION && primary != CON && secondary != CON &&
                id.value !in setOf("sleep", "meal")) { "Invalid ordinary duration definition" }
            ExperienceRule.SLEEP -> require(id.value == "sleep" && mode == ActivityMode.DURATION && primary == MEN && secondary == null) {
                "Sleep is reserved for the basic sleep activity"
            }
            ExperienceRule.MEAL -> require(id.value == "meal" && mode == ActivityMode.OCCURRENCE && primary == CON && secondary == null) {
                "Constitution is reserved for the basic meal activity"
            }
        }
    }
}

object ActivityCatalog {
    val activities: List<ActivityDefinition> = Collections.unmodifiableList(listOf(
        ActivityDefinition(ActivityId("strength"), "Entrenamiento de fuerza", "Actividad física", ActivityMode.DURATION, STR),
        ActivityDefinition(ActivityId("aerobic"), "Actividad aeróbica", "Actividad física", ActivityMode.DURATION, DEX),
        ActivityDefinition(ActivityId("meal"), "Comida saludable", "Nutrición", ActivityMode.OCCURRENCE, CON, experienceRule = ExperienceRule.MEAL),
        ActivityDefinition(ActivityId("study"), "Leer / estudiar", "Aprendizaje", ActivityMode.DURATION, INT),
        ActivityDefinition(ActivityId("hobby"), "Ocio / hobby", "Bienestar", ActivityMode.DURATION, WIT),
        ActivityDefinition(ActivityId("sleep"), "Dormir", "Descanso", ActivityMode.DURATION, MEN, experienceRule = ExperienceRule.SLEEP),
        ActivityDefinition(ActivityId("crossfit"), "CrossFit", "Actividad física", ActivityMode.DURATION, STR, DEX),
        ActivityDefinition(ActivityId("martial-arts"), "Artes marciales", "Actividad física", ActivityMode.DURATION, DEX, STR),
        ActivityDefinition(ActivityId("yoga"), "Yoga", "Bienestar físico", ActivityMode.DURATION, WIT, DEX),
        ActivityDefinition(ActivityId("practical-skill"), "Aprender una habilidad práctica", "Aprendizaje", ActivityMode.DURATION, INT, DEX),
        ActivityDefinition(ActivityId("strategy-games"), "Juegos de estrategia", "Ocio / aprendizaje", ActivityMode.DURATION, WIT, INT),
    ))
    private val byId = activities.associateBy { it.id }
    fun find(id: ActivityId): ActivityDefinition? = byId[id]
}
