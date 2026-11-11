package lvluplife.progression

import lvluplife.model.Attribute

/** Compile-only T026 contract. Classification/catalog rules are implemented in T034, images in US5. */
object ClassRules {
    fun name(levels: Map<Attribute, Int>): String =
        throw NotImplementedError("Class rules are not implemented (T034)")
    fun appearance(levels: Map<Attribute, Int>, generalLevel: Int): ClassAppearance =
        throw NotImplementedError("Class rules are not implemented (T034)")
    fun unlockedStages(className: String, generalLevel: Int): List<Int> =
        throw NotImplementedError("Class rules are not implemented (T034)")
    fun catalogStages(): Map<String, List<Int>> =
        throw NotImplementedError("Class rules are not implemented (T034)")
}

data class ClassAppearance(val className: String, val stage: Int)
