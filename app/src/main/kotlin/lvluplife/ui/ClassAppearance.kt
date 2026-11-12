package lvluplife.ui

/** Audited US5 batch one. Stable resource names are shared with the public catalogue. */
internal data class ClassVisual(val classId: String, val stage: Int, val resourceId: String, val drawable: Int)
internal object ClassAppearance {
    val batchOne: List<ClassVisual> = listOf(
        ClassVisual("novato", 1, "novato", lvluplife.R.drawable.novato),
        ClassVisual("guerrero", 1, "guerrero", lvluplife.R.drawable.guerrero),
        ClassVisual("guerrero", 2, "guerrero_ii", lvluplife.R.drawable.guerrero_ii),
        ClassVisual("guerrero", 3, "guerrero_iii", lvluplife.R.drawable.guerrero_iii),
        ClassVisual("explorador", 1, "explorador", lvluplife.R.drawable.explorador),
        ClassVisual("explorador", 2, "explorador_ii", lvluplife.R.drawable.explorador_ii),
        ClassVisual("explorador", 3, "explorador_iii", lvluplife.R.drawable.explorador_iii),
        ClassVisual("guardian", 1, "guardian", lvluplife.R.drawable.guardian),
        ClassVisual("guardian", 2, "guardian_ii", lvluplife.R.drawable.guardian_ii),
        ClassVisual("guardian", 3, "guardian_iii", lvluplife.R.drawable.guardian_iii),
        ClassVisual("sabio", 1, "sabio", lvluplife.R.drawable.sabio),
        ClassVisual("sabio", 2, "sabio_ii", lvluplife.R.drawable.sabio_ii),
        ClassVisual("sabio", 3, "sabio_iii", lvluplife.R.drawable.sabio_iii),
        ClassVisual("monje", 1, "monje", lvluplife.R.drawable.monje),
        ClassVisual("monje", 2, "monje_ii", lvluplife.R.drawable.monje_ii),
        ClassVisual("monje", 3, "monje_iii", lvluplife.R.drawable.monje_iii),
        ClassVisual("mistico", 1, "mistico", lvluplife.R.drawable.mistico),
        ClassVisual("mistico", 2, "mistico_ii", lvluplife.R.drawable.mistico_ii),
        ClassVisual("mistico", 3, "mistico_iii", lvluplife.R.drawable.mistico_iii),
        ClassVisual("maestro-de-armas", 1, "maestro_de_armas", lvluplife.R.drawable.maestro_de_armas),
        ClassVisual("maestro-de-armas", 2, "maestro_de_armas_ii", lvluplife.R.drawable.maestro_de_armas_ii),
        ClassVisual("maestro-de-armas", 3, "maestro_de_armas_iii", lvluplife.R.drawable.maestro_de_armas_iii),
        ClassVisual("paladin", 1, "paladin", lvluplife.R.drawable.paladin),
        ClassVisual("paladin", 2, "paladin_ii", lvluplife.R.drawable.paladin_ii),
        ClassVisual("paladin", 3, "paladin_iii", lvluplife.R.drawable.paladin_iii),
        ClassVisual("guerrero-arcano", 1, "guerrero_arcano", lvluplife.R.drawable.guerrero_arcano),
        ClassVisual("guerrero-arcano", 2, "guerrero_arcano_ii", lvluplife.R.drawable.guerrero_arcano_ii),
        ClassVisual("guerrero-arcano", 3, "guerrero_arcano_iii", lvluplife.R.drawable.guerrero_arcano_iii),
        ClassVisual("luchador-monje", 1, "luchador_monje", lvluplife.R.drawable.luchador_monje),
        ClassVisual("luchador-monje", 2, "luchador_monje_ii", lvluplife.R.drawable.luchador_monje_ii),
        ClassVisual("luchador-monje", 3, "luchador_monje_iii", lvluplife.R.drawable.luchador_monje_iii),
        ClassVisual("guerrero-mistico", 1, "guerrero_mistico", lvluplife.R.drawable.guerrero_mistico),
        ClassVisual("guerrero-mistico", 2, "guerrero_mistico_ii", lvluplife.R.drawable.guerrero_mistico_ii),
        ClassVisual("guerrero-mistico", 3, "guerrero_mistico_iii", lvluplife.R.drawable.guerrero_mistico_iii),
        ClassVisual("agil-guardian", 1, "agil_guardian", lvluplife.R.drawable.agil_guardian),
        ClassVisual("agil-guardian", 2, "agil_guardian_ii", lvluplife.R.drawable.agil_guardian_ii),
        ClassVisual("agil-guardian", 3, "agil_guardian_iii", lvluplife.R.drawable.agil_guardian_iii),
    )
    private val byStage = batchOne.associateBy { it.classId to it.stage }
    fun binding(classId: String, stage: Int): ClassVisual? = byStage[classId to stage]
}
