package lvluplife.ui

import lvluplife.application.ClassCondition

/** Synthetic copy of the server-published class conditions (spec "Tabla de Clases"); UI tests only display them. */
object ClassConditionFixtures {
    private val pairs = mapOf(
        "guerrero" to listOf("STR"), "explorador" to listOf("DEX"), "guardian" to listOf("CON"),
        "sabio" to listOf("INT"), "monje" to listOf("WIT"), "mistico" to listOf("MEN"),
        "maestro-de-armas" to listOf("STR", "DEX"), "paladin" to listOf("STR", "CON"),
        "guerrero-arcano" to listOf("STR", "INT"), "luchador-monje" to listOf("STR", "WIT"),
        "guerrero-mistico" to listOf("STR", "MEN"), "agil-guardian" to listOf("DEX", "CON"),
        "artifice" to listOf("DEX", "INT"), "ninja" to listOf("DEX", "WIT"), "explorador-de-suenos" to listOf("DEX", "MEN"),
        "guardian-de-la-sabiduria" to listOf("CON", "INT"), "monje-guardian" to listOf("CON", "WIT"),
        "druida" to listOf("CON", "MEN"), "ilusionista" to listOf("INT", "WIT"), "oraculo" to listOf("INT", "MEN"),
        "monje-espiritual" to listOf("WIT", "MEN"),
    )
    private val multiple = mapOf("aventurero-versatil" to 3, "maestro-del-equilibrio" to 4, "heroe-ascendente" to 5, "heroe-integral" to 6)

    fun of(id: String): ClassCondition = when (id) {
        "novato" -> ClassCondition.InitialLevels
        in pairs -> ClassCondition.BalancedAttributes(pairs.getValue(id))
        in multiple -> ClassCondition.BalancedCount(multiple.getValue(id))
        else -> error("Clase sintética desconocida: $id")
    }
}
