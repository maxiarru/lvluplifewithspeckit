package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.SessionToken
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic confirmed server views and catalogue port; no XP/classification engine or network. */
class ClassCatalogFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val definitions = listOf(
        "novato" to "Novato", "guerrero" to "Guerrero", "explorador" to "Explorador", "guardian" to "Guardián",
        "sabio" to "Sabio", "monje" to "Monje", "mistico" to "Místico", "maestro-de-armas" to "Maestro de Armas",
        "paladin" to "Paladín", "guerrero-arcano" to "Guerrero Arcano", "luchador-monje" to "Luchador Monje",
        "guerrero-mistico" to "Guerrero Místico", "agil-guardian" to "Ágil Guardián", "artifice" to "Artífice",
        "ninja" to "Ninja", "explorador-de-suenos" to "Explorador de Sueños", "guardian-de-la-sabiduria" to "Guardián de la Sabiduría",
        "monje-guardian" to "Monje Guardián", "druida" to "Druida", "ilusionista" to "Ilusionista",
        "oraculo" to "Oráculo", "monje-espiritual" to "Monje Espiritual", "aventurero-versatil" to "Aventurero Versátil",
        "maestro-del-equilibrio" to "Maestro del Equilibrio", "heroe-ascendente" to "Héroe Ascendente", "heroe-integral" to "Héroe Integral",
    )
    private val roman = mapOf(1 to "I", 2 to "II", 3 to "III")
    private val catalog = definitions.map { (id, name) ->
        ClassCatalogEntry(id, name, (if (id == "novato") listOf(1) else listOf(1, 2, 3)).map { stage ->
            ClassStageImage(stage, id.replace('-', '_') + if (stage == 1) "" else "_${roman.getValue(stage).lowercase()}",
                mapOf(1 to 1, 2 to 15, 3 to 35).getValue(stage))
        }, ClassConditionFixtures.of(id))
    }
    private fun progress(level: Int, id: String = "novato", name: String = "Novato", stage: Int = 1) =
        RecordProgressView(10, "synthetic-catalog-cycle", "2026-11-11", level, id, stage,
            listOf("STR", "DEX", "CON", "INT", "WIT", "MEN").associateWith { RecordAttributeView(1, 0) }, name)

    private inner class Records : RecordFlowRecords {
        var fail=false
        var gate: kotlinx.coroutines.CompletableDeferred<List<ClassCatalogEntry>>?=null
        var values=this@ClassCatalogFlowTest.catalog
        override val online = true
        override suspend fun classes(uid: String): List<ClassCatalogEntry> {
            assertEquals("synthetic-catalog-owner", uid)
            if(fail) throw OperationBlocked(lvluplife.data.ApiFailureCode.NETWORK_ERROR)
            return gate?.await() ?: values
        }
        override suspend fun catalog(uid: String) = emptyList<RecordOption>()
        override suspend fun draft(uid: String): RecordDraft? = null
        override suspend fun saveDraft(uid: String, draft: RecordDraft) { error("El catálogo no debe guardar borradores") }
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot = error("El catálogo es de lectura")
        override suspend fun retry(ref: OperationRef): OperationSnapshot = error("El catálogo no debe reenviar operaciones")
    }
    private fun mountCatalog(level: Int, records: Records = Records(), expectList: Boolean = true): SessionController {
        val session = SessionController()
        session.authenticate(SessionToken("synthetic-catalog-owner", "synthetic-catalog-token"))
        compose.runOnIdle { compose.activity.setContent { MaterialTheme {
            SessionHost(session) { RecordFlow(session, records, RecordFlowProgress { progress(level) }) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Nivel general $level").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Clases").assertExists().performClick()
        if(expectList) compose.onNodeWithTag("class-catalog").assertIsDisplayed()
        return session
    }
    private fun checkCatalog(level: Int, unlocked: Set<Int>) {
        mountCatalog(level)
        capture("t069-catalog-start-$level.png")
        val list = compose.onNodeWithTag("class-catalog")
        var index=0
        for (entry in catalog) {
            val stages = entry.images.map { it.stage }
            assertEquals(if (entry.id == "novato") listOf(1) else listOf(1, 2, 3), stages)
            for (stage in stages) {
                val tag = "class-${entry.id}-stage-$stage"
                list.performScrollToIndex(index++)
                val expected = if (entry.id == "novato" || stage in unlocked) "Desbloqueada" else "Bloqueada"
                compose.onNodeWithTag(tag).assertIsDisplayed()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
                compose.onNodeWithTag("$tag-image").assertIsDisplayed()
                    .assertContentDescriptionEquals("${entry.name} · Etapa ${roman.getValue(stage)}")
            }
        }
        compose.onNodeWithTag("class-novato-stage-2").assertDoesNotExist()
        compose.onNodeWithTag("class-novato-stage-3").assertDoesNotExist()
        compose.onNodeWithText("Seleccionar clase").assertDoesNotExist()
        compose.onNodeWithText("Elegir objetivo").assertDoesNotExist()
        capture("t069-catalog-end-$level.png")
    }
    private fun capture(name: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun catalogShowsAll26ClassesFromLevelOneWithOnlyFirstStagesUnlocked() = checkCatalog(1, setOf(1))
    @Test fun catalogAt14ShowsAll25OrdinaryClassesLockedLaterStagesAndNoviceOnlyOne() = checkCatalog(14, setOf(1))
    @Test fun catalogAt15UnlocksSecondStageForAll25ClassesAndKeepsNoviceOnlyOne() = checkCatalog(15, setOf(1, 2))
    @Test fun catalogAt34KeepsThirdStageLockedForAll25ClassesAndNoviceOnlyOne() = checkCatalog(34, setOf(1, 2))
    @Test fun catalogAt35UnlocksAllOrdinaryStagesWithoutInventingNoviceStages() = checkCatalog(35, setOf(1, 2, 3))

    // AC-61: every class shows the attributes or balance condition published by the server, as text.
    private fun conditionText(condition: ClassCondition) = when (condition) {
        ClassCondition.InitialLevels -> "Condición: los seis atributos en nivel 1"
        is ClassCondition.BalancedAttributes ->
            (if (condition.attributes.size == 1) "Atributo equilibrado: " else "Atributos equilibrados: ") + condition.attributes.joinToString(" + ")
        is ClassCondition.BalancedCount -> "Condición: ${condition.count} atributos equilibrados"
    }
    @Test fun catalogShowsEveryClassAttributesOrBalanceConditionAsText() {
        mountCatalog(1)
        compose.onAllNodesWithText("Un atributo está equilibrado si su nivel es al menos el 80 % del mayor nivel de los seis.")
            .assertCountEquals(1)
        val list = compose.onNodeWithTag("class-catalog")
        var index = 0
        for (entry in catalog) {
            list.performScrollToIndex(index)
            compose.onNodeWithTag("class-${entry.id}-stage-1-condition").assertIsDisplayed()
                .assertTextEquals(conditionText(entry.condition))
            index += entry.images.size
        }
        assertEquals("Atributos equilibrados: CON + INT", conditionText(catalog.first { it.id == "guardian-de-la-sabiduria" }.condition))
        capture("t088-catalog-conditions.png")
    }

    @Test fun failedCatalogueCanRetryAndReturnHomeWithoutWritingAnyOperation() {
        val records=Records().apply { fail=true }
        mountCatalog(14,records,expectList=false)
        compose.onNodeWithText("No pudimos actualizar las clases").assertIsDisplayed()
        compose.onNodeWithTag("class-catalog").assertDoesNotExist()
        compose.runOnIdle { records.fail=false }
        compose.onNodeWithText("Reintentar clases").performClick()
        compose.onNodeWithTag("class-catalog").assertIsDisplayed()
        compose.onNodeWithText("Volver a principal").performClick()
        compose.onNodeWithTag("character-class").assertTextEquals("Novato")
        compose.onNodeWithText("Clases y etapas").assertDoesNotExist()
    }
    @Test fun logoutWhileCatalogueIsLoadingDiscardsLateResponseAndPrivateNavigation() {
        val records=Records().apply { gate=kotlinx.coroutines.CompletableDeferred() }
        val session=mountCatalog(15,records,expectList=false)
        compose.onNodeWithText("Cargando clases…").assertIsDisplayed()
        compose.runOnIdle { session.logout();records.gate!!.complete(catalog) }
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        compose.onNodeWithText("Clases y etapas").assertDoesNotExist()
        compose.onNodeWithTag("class-catalog").assertDoesNotExist()
    }
    @Test fun unknownResourceFailsWholeCatalogueInsteadOfShowingPartialOrFallbackImages() {
        val records=Records().apply { values=catalog.mapIndexed { index,entry -> if(index==0)
            entry.copy(images=listOf(entry.images.single().copy(resourceId="missing_resource"))) else entry } }
        mountCatalog(35,records,expectList=false)
        compose.onNodeWithText("No pudimos actualizar las clases").assertIsDisplayed()
        compose.onNodeWithTag("class-catalog").assertDoesNotExist()
        compose.onNodeWithText("Apariencia no disponible").assertDoesNotExist()
    }

    private fun checkAppearance(level: Int, ordinaryStage: Int) {
        var value by mutableStateOf(progress(level))
        compose.runOnIdle { compose.activity.setContent { MaterialTheme { HomeScreen(ProgressView(value, loading = false), {}) } } }
        for ((id, name) in definitions) {
            val stage = if (id == "novato") 1 else ordinaryStage
            compose.runOnIdle { value = progress(level, id, name, stage) }
            compose.onNodeWithTag("character-class").assertTextEquals(name).assertIsDisplayed()
            compose.onNodeWithContentDescription("$name · Etapa ${roman.getValue(stage)}").assertIsDisplayed()
            compose.onNodeWithText("Nivel general $level").assertIsDisplayed()
            compose.onNodeWithText("Apariencia no disponible").assertDoesNotExist()
            if (id == "novato") {
                compose.onNodeWithContentDescription("Novato · Etapa II").assertDoesNotExist()
                compose.onNodeWithContentDescription("Novato · Etapa III").assertDoesNotExist()
            }
        }
    }
    @Test fun homeRendersAll26ConfirmedAppearancesAt14() = checkAppearance(14, 1)
    @Test fun homeRendersAll26ConfirmedAppearancesAt15() = checkAppearance(15, 2)
    @Test fun homeRendersAll26ConfirmedAppearancesAt34() = checkAppearance(34, 2)
    @Test fun homeRendersAll26ConfirmedAppearancesAt35() = checkAppearance(35, 3)
}
