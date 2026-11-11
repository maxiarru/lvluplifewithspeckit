package lvluplife.progression

import lvluplife.model.Attribute
import java.util.Collections

/** Exact balance classification; stage availability follows the current general level. */
object ClassRules {
    fun name(levels: Map<Attribute, Int>): String {
        require(levels.keys == Attribute.entries.toSet()) { "All six attributes are required" }
        require(levels.values.all { it in 1..50 }) { "Attribute levels must be in 1..50" }
        if (levels.values.all { it == 1 }) return ClassCatalog.find("novato")!!.name
        val maximum = levels.values.max()
        val balanced = levels.filterValues { it * 5 >= maximum * 4 }.keys
        return ClassCatalog.classes.single {
            !it.initialOnly && it.balancedCount == balanced.size &&
                (it.attributes.isEmpty() || it.attributes == balanced)
        }.name
    }
    fun appearance(levels: Map<Attribute, Int>, generalLevel: Int): ClassAppearance {
        val name = name(levels)
        return ClassAppearance(name, unlockedStages(name, generalLevel).last())
    }
    fun unlockedStages(className: String, generalLevel: Int): List<Int> {
        require(generalLevel in 1..50) { "General level must be in 1..50" }
        val definition = requireNotNull(ClassCatalog.named(className)) { "Unknown class" }
        return Collections.unmodifiableList(definition.stages.filter { ClassCatalog.stageMinimums.getValue(it) <= generalLevel })
    }
    fun catalogStages(): Map<String, List<Int>> =
        Collections.unmodifiableMap(ClassCatalog.classes.associate { it.name to it.stages })
}

data class ClassAppearance(val className: String, val stage: Int) {
    val classId: String get() = requireNotNull(ClassCatalog.named(className)) { "Unknown class" }.id
}
