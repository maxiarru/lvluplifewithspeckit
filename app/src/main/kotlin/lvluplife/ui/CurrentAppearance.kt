package lvluplife.ui

/** Current character and catalogue share the audited resource bindings. */
internal object CurrentAppearance {
    fun image(classId: String, stage: Int): Int? = ClassAppearance.binding(classId, stage)?.drawable
}
