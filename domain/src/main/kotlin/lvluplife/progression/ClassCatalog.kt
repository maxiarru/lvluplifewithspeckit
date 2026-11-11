package lvluplife.progression

import lvluplife.model.Attribute
import lvluplife.model.Attribute.*
import java.util.Collections
import java.util.EnumSet

class ClassDefinition internal constructor(val id: String, val name: String, attributes: Set<Attribute>,
    val balancedCount: Int?, val initialOnly: Boolean, stages: List<Int>) {
    val attributes: Set<Attribute> = Collections.unmodifiableSet(EnumSet.noneOf(Attribute::class.java).apply { addAll(attributes) })
    val stages: List<Int> = Collections.unmodifiableList(stages.toList())
}

/** Data-only class/stage catalogue; image bindings belong to US5. */
object ClassCatalog {
    private fun entry(id: String, name: String, vararg attributes: Attribute) =
        ClassDefinition(id, name, attributes.toSet(), attributes.size, false, listOf(1, 2, 3))
    private fun balanced(id: String, name: String, count: Int) =
        ClassDefinition(id, name, emptySet(), count, false, listOf(1, 2, 3))

    val classes: List<ClassDefinition> = Collections.unmodifiableList(listOf(
        ClassDefinition("novato", "Novato", emptySet(), null, true, listOf(1)),
        entry("guerrero", "Guerrero", STR), entry("explorador", "Explorador", DEX),
        entry("guardian", "Guardián", CON), entry("sabio", "Sabio", INT),
        entry("monje", "Monje", WIT), entry("mistico", "Místico", MEN),
        entry("maestro-de-armas", "Maestro de Armas", STR, DEX),
        entry("paladin", "Paladín", STR, CON), entry("guerrero-arcano", "Guerrero Arcano", STR, INT),
        entry("luchador-monje", "Luchador Monje", STR, WIT), entry("guerrero-mistico", "Guerrero Místico", STR, MEN),
        entry("agil-guardian", "Ágil Guardián", DEX, CON), entry("artifice", "Artífice", DEX, INT),
        entry("ninja", "Ninja", DEX, WIT), entry("explorador-de-suenos", "Explorador de Sueños", DEX, MEN),
        entry("guardian-de-la-sabiduria", "Guardián de la Sabiduría", CON, INT),
        entry("monje-guardian", "Monje Guardián", CON, WIT), entry("druida", "Druida", CON, MEN),
        entry("ilusionista", "Ilusionista", INT, WIT), entry("oraculo", "Oráculo", INT, MEN),
        entry("monje-espiritual", "Monje Espiritual", WIT, MEN),
        balanced("aventurero-versatil", "Aventurero Versátil", 3),
        balanced("maestro-del-equilibrio", "Maestro del Equilibrio", 4),
        balanced("heroe-ascendente", "Héroe Ascendente", 5),
        balanced("heroe-integral", "Héroe Integral", 6),
    ))
    val stageMinimums: Map<Int, Int> = Collections.unmodifiableMap(linkedMapOf(1 to 1, 2 to 15, 3 to 35))
    private val byId = classes.associateBy { it.id }
    private val byName = classes.associateBy { it.name }
    fun find(id: String): ClassDefinition? = byId[id]
    fun named(name: String): ClassDefinition? = byName[name]
}
