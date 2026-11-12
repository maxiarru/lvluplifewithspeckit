package lvluplife.ui

import lvluplife.R

/** Explicit bindings for current appearance; full catalogue/resource QA belongs to US5. */
internal object CurrentAppearance {
    private val images = mapOf(
        "novato" to listOf(R.drawable.novato),
        "guerrero" to listOf(R.drawable.guerrero, R.drawable.guerrero_ii, R.drawable.guerrero_iii),
        "explorador" to listOf(R.drawable.explorador, R.drawable.explorador_ii, R.drawable.explorador_iii),
        "guardian" to listOf(R.drawable.guardian, R.drawable.guardian_ii, R.drawable.guardian_iii),
        "sabio" to listOf(R.drawable.sabio, R.drawable.sabio_ii, R.drawable.sabio_iii),
        "monje" to listOf(R.drawable.monje, R.drawable.monje_ii, R.drawable.monje_iii),
        "mistico" to listOf(R.drawable.mistico, R.drawable.mistico_ii, R.drawable.mistico_iii),
        "maestro-de-armas" to listOf(R.drawable.maestro_de_armas, R.drawable.maestro_de_armas_ii, R.drawable.maestro_de_armas_iii),
        "paladin" to listOf(R.drawable.paladin, R.drawable.paladin_ii, R.drawable.paladin_iii),
        "guerrero-arcano" to listOf(R.drawable.guerrero_arcano, R.drawable.guerrero_arcano_ii, R.drawable.guerrero_arcano_iii),
        "luchador-monje" to listOf(R.drawable.luchador_monje, R.drawable.luchador_monje_ii, R.drawable.luchador_monje_iii),
        "guerrero-mistico" to listOf(R.drawable.guerrero_mistico, R.drawable.guerrero_mistico_ii, R.drawable.guerrero_mistico_iii),
        "agil-guardian" to listOf(R.drawable.agil_guardian, R.drawable.agil_guardian_ii, R.drawable.agil_guardian_iii),
        "artifice" to listOf(R.drawable.artifice, R.drawable.artifice_ii, R.drawable.artifice_iii),
        "ninja" to listOf(R.drawable.ninja, R.drawable.ninja_ii, R.drawable.ninja_iii),
        "explorador-de-suenos" to listOf(R.drawable.explorador_de_suenos, R.drawable.explorador_de_suenos_ii, R.drawable.explorador_de_suenos_iii),
        "guardian-de-la-sabiduria" to listOf(R.drawable.guardian_de_la_sabiduria, R.drawable.guardian_de_la_sabiduria_ii, R.drawable.guardian_de_la_sabiduria_iii),
        "monje-guardian" to listOf(R.drawable.monje_guardian, R.drawable.monje_guardian_ii, R.drawable.monje_guardian_iii),
        "druida" to listOf(R.drawable.druida, R.drawable.druida_ii, R.drawable.druida_iii),
        "ilusionista" to listOf(R.drawable.ilusionista, R.drawable.ilusionista_ii, R.drawable.ilusionista_iii),
        "oraculo" to listOf(R.drawable.orcaculo, R.drawable.oraculo_ii, R.drawable.oraculo_iii),
        "monje-espiritual" to listOf(R.drawable.monje_espiritual, R.drawable.monje_espiritual_ii, R.drawable.monje_espiritual_iii),
        "aventurero-versatil" to listOf(R.drawable.aventurero_versatil, R.drawable.aventurero_versatil_ii, R.drawable.aventurero_versatil_iii),
        "maestro-del-equilibrio" to listOf(R.drawable.maestro_del_equilibrio, R.drawable.maestro_del_equilibrio_ii, R.drawable.maestro_del_equilibrio_iii),
        "heroe-ascendente" to listOf(R.drawable.heroe_ascendente, R.drawable.heroe_ascendente_ii, R.drawable.heroe_ascendente_iii),
        "heroe-integral" to listOf(R.drawable.heroe_integral, R.drawable.heroe_integral_ii, R.drawable.heroe_integral_iii),
    )
    fun image(classId: String, stage: Int): Int? =
        ClassAppearance.binding(classId, stage)?.drawable ?: images[classId]?.getOrNull(stage - 1)
}
