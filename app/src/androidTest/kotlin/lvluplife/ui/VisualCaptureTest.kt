package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import lvluplife.data.SessionToken
import lvluplife.ui.theme.LvlUpTheme
import org.junit.Rule
import org.junit.Test

/**
 * T014: captures PJ, Atributos, Info, Registrar and Historial with synthetic data for the owner's review (SC-005).
 * Files go to the app's private `files/visual/` and are copied to specs/002-identidad-visual/visual/. Run it with
 * `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` so the files survive the run.
 */
class VisualCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val session = SessionController()
    private val today = LocalDate.now(ZoneOffset.ofHours(-3))
    private val progress = RecordFlowProgress {
        RecordProgressView(40, "synthetic-cycle", today.toString(), 20, "guerrero", 2, mapOf(
            "STR" to RecordAttributeView(14, 95, 420), "DEX" to RecordAttributeView(9, 60, 270),
            "CON" to RecordAttributeView(10, 200, 300), "INT" to RecordAttributeView(6, 30, 180),
            "WIT" to RecordAttributeView(8, 110, 240), "MEN" to RecordAttributeView(11, 150, 330)), className = "Guerrero")
    }
    private val records = object : RecordFlowRecords {
        override val online = true
        override suspend fun catalog(uid: String) = listOf(
            RecordOption("strength", "Entrenamiento de fuerza", true, "Actividad física", "STR", null),
            RecordOption("aerobic", "Actividad aeróbica", true, "Actividad física", "DEX", null),
            RecordOption("meal", "Comida saludable", false, "Alimentación", "CON", null),
            RecordOption("sleep", "Dormir", true, "Descanso", "MEN", null),
            RecordOption("crossfit", "CrossFit", true, "Actividad física", "STR", "DEX"))
        override suspend fun draft(uid: String): RecordDraft? = null
        override suspend fun saveDraft(uid: String, draft: RecordDraft) = Unit
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot =
            throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        override suspend fun retry(ref: OperationRef): OperationSnapshot = throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
    }
    // Same ports as the real app: without them Registrar would not draw «+ Crear actividad personalizada».
    private val customActivities = object : CustomActivityFlowActivities {
        override val online = true
        override suspend fun list(uid: String) = emptyList<CustomActivityOption>()
        override suspend fun draft(uid: String): CustomActivityDraft? = null
        override suspend fun saveDraft(uid: String, draft: CustomActivityDraft) = Unit
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: CustomActivityDraft): OperationSnapshot =
            throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        override suspend fun retry(ref: OperationRef): OperationSnapshot = throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
    }
    private val history = HistoryFlowHistory { _, selection ->
        val day = today.toString()
        HistoryPage(3, selection.date, today.plusDays(1).toString(), listOf(
            HistoryItem("r1", "synthetic-cycle", "strength", "Entrenamiento de fuerza", day, 45, Instant.EPOCH, 1, false),
            HistoryItem("r2", "synthetic-cycle", "meal", "Comida saludable", day, null, Instant.EPOCH, 1, false),
            HistoryItem("r3", "synthetic-cycle", "sleep", "Dormir", day, 480, Instant.EPOCH, 1, false)))
    }

    private fun save(name: String) {
        compose.waitForIdle()
        // Private files dir: readable with `adb exec-out run-as lvluplife.android cat files/visual/<name>`.
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "visual").apply { mkdirs() }
        java.io.File(dir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun captureMainScreens() {
        session.authenticate(SessionToken("synthetic-visual", "synthetic-visual-token"))
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme {
            SessionHost(session) { MainTabs(session, "Ana", session::logout, records, progress, customActivities, history = history) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pj-record").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        save("pj.png")
        compose.onNode(isTab(SessionRoute.ATTRIBUTES)).performClick(); save("atributos.png")
        compose.onNode(isTab(SessionRoute.INFO)).performClick(); save("info.png")
        compose.onNode(isTab(SessionRoute.HISTORY)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Dormir").fetchSemanticsNodes().isNotEmpty() }
        save("historial.png")
        compose.onNode(isTab(SessionRoute.HOME)).performClick()
        compose.onNodeWithTag("pj-record").performScrollTo().performClick()
        compose.onNodeWithTag("activity-crossfit").performScrollTo().performClick()
        compose.onNodeWithTag("record-create-custom").assertExists()
        compose.onNodeWithTag("record-date-0").performScrollTo()
        save("registrar.png")
    }
}
