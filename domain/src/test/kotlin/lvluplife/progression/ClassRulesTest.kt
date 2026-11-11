package lvluplife.progression

import kotlin.test.*
import lvluplife.model.Attribute
import lvluplife.model.Attribute.*

class ClassRulesTest {
    private fun levels(vararg changes: Pair<Attribute, Int>) = Attribute.entries.associateWith { 1 } + changes.toMap()
    @Test fun classificationRequiresAllSixAttributes() {
        for (missing in Attribute.entries) assertFailsWith<IllegalArgumentException> { ClassRules.name(levels() - missing) }
        assertFailsWith<IllegalArgumentException> { ClassRules.name(emptyMap()) }
    }
    @Test fun invalidLevelsAreRejectedBeforeMultiplyingTheThreshold() {
        for (level in listOf(0, -1, 51, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { ClassRules.name(levels(STR to level)) }
    }
    @Test fun generalBoundsAreCheckedEvenForNovato() {
        for (level in listOf(0, 51, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { ClassRules.appearance(levels(), level) }
            assertFailsWith<IllegalArgumentException> { ClassRules.unlockedStages("Guerrero", level) }
        }
    }
    @Test fun fractionalThresholdAndInputOrderCannotChangeTheSelectedPair() {
        assertEquals("Guerrero", ClassRules.name(levels(STR to 6, DEX to 4)))
        val input = levels(STR to 6, DEX to 5)
        assertEquals("Maestro de Armas", ClassRules.name(input))
        assertEquals("Maestro de Armas", ClassRules.name(input.entries.reversed().associate { it.toPair() }))
    }
    @Test fun multipleBalanceClassesDependOnlyOnCountAcrossAllAttributeSubsets() {
        val expected = mapOf(3 to "Aventurero Versátil", 4 to "Maestro del Equilibrio", 5 to "Héroe Ascendente", 6 to "Héroe Integral")
        for (mask in 0 until 64) {
            val chosen = Attribute.entries.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            if (chosen.size in 3..6) assertEquals(expected.getValue(chosen.size), ClassRules.name(levels(*chosen.map { it to 10 }.toTypedArray())))
        }
    }
    @Test fun catalogAndNestedConditionsAndStageDataAreImmutable() {
        val entries = ClassCatalog.classes
        assertFailsWith<UnsupportedOperationException> { (entries as MutableList).clear() }
        val warrior = assertNotNull(ClassCatalog.find("guerrero"))
        assertFailsWith<UnsupportedOperationException> { (warrior.attributes as MutableSet).add(DEX) }
        assertFailsWith<UnsupportedOperationException> { (warrior.stages as MutableList).clear() }
        assertEquals(mapOf(1 to 1, 2 to 15, 3 to 35), ClassCatalog.stageMinimums)
        assertFailsWith<UnsupportedOperationException> { (ClassCatalog.stageMinimums as MutableMap)[2] = 1 }
        assertEquals(26, ClassCatalog.classes.size); assertEquals(setOf(STR), warrior.attributes)
    }
    @Test fun canonicalIdentifiersMatchNamesAndLookupsWithoutGuessingFilenames() {
        val expected = mapOf("novato" to "Novato", "guerrero" to "Guerrero", "explorador" to "Explorador", "guardian" to "Guardián",
            "sabio" to "Sabio", "monje" to "Monje", "mistico" to "Místico", "maestro-de-armas" to "Maestro de Armas",
            "paladin" to "Paladín", "guerrero-arcano" to "Guerrero Arcano", "luchador-monje" to "Luchador Monje",
            "guerrero-mistico" to "Guerrero Místico", "agil-guardian" to "Ágil Guardián", "artifice" to "Artífice", "ninja" to "Ninja",
            "explorador-de-suenos" to "Explorador de Sueños", "guardian-de-la-sabiduria" to "Guardián de la Sabiduría",
            "monje-guardian" to "Monje Guardián", "druida" to "Druida", "ilusionista" to "Ilusionista", "oraculo" to "Oráculo",
            "monje-espiritual" to "Monje Espiritual", "aventurero-versatil" to "Aventurero Versátil",
            "maestro-del-equilibrio" to "Maestro del Equilibrio", "heroe-ascendente" to "Héroe Ascendente", "heroe-integral" to "Héroe Integral")
        assertEquals(expected, ClassCatalog.classes.associate { it.id to it.name })
        for ((id, name) in expected) { assertEquals(name, ClassCatalog.find(id)?.name); assertEquals(id, ClassCatalog.named(name)?.id) }
    }
    @Test fun unknownIdentifiersAndNamesNeverFallBackToNovato() {
        assertNull(ClassCatalog.find("synthetic-unknown")); assertNull(ClassCatalog.named("synthetic-unknown"))
        assertFailsWith<IllegalArgumentException> { ClassRules.unlockedStages("synthetic-unknown", 20) }
    }
    @Test fun unlockedStagesAndCatalogViewsCannotBeMutated() {
        val unlocked = ClassRules.unlockedStages("Guerrero", 20)
        assertFailsWith<UnsupportedOperationException> { (unlocked as MutableList).clear() }
        val stages = ClassRules.catalogStages()
        assertFailsWith<UnsupportedOperationException> { (stages as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (stages.getValue("Novato") as MutableList).clear() }
        assertEquals(listOf(1, 2), ClassRules.unlockedStages("Guerrero", 20)); assertEquals(listOf(1), stages.getValue("Novato"))
    }
    @Test fun appearanceCarriesTheCanonicalClassIdForProjection() {
        assertEquals("novato", ClassRules.appearance(levels(), 1).classId)
        assertEquals("maestro-de-armas", ClassRules.appearance(levels(STR to 10, DEX to 8), 20).classId)
        assertEquals("agil-guardian", ClassRules.appearance(levels(DEX to 10, CON to 8), 20).classId)
    }
}
