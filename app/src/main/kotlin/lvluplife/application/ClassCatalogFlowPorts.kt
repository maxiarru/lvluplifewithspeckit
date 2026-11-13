package lvluplife.application

/** Server-authored visual metadata; Android only compares minimumLevel with confirmed general level. */
data class ClassStageImage(val stage: Int, val resourceId: String, val minimumLevel: Int)

/** Server-authored class condition, shown as data; Android never classifies attributes itself. */
sealed interface ClassCondition {
    /** Novato: all six attributes at level 1. */
    data object InitialLevels : ClassCondition
    /** Pure or pair classes: exactly these attributes are balanced, in specification order. */
    data class BalancedAttributes(val attributes: List<String>) : ClassCondition
    /** Multiple balance classes: exactly this many balanced attributes, whichever they are. */
    data class BalancedCount(val count: Int) : ClassCondition
}

data class ClassCatalogEntry(val id: String, val name: String, val images: List<ClassStageImage>, val condition: ClassCondition)
