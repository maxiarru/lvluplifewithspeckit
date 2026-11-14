package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.concurrent.atomic.AtomicInteger
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import lvluplife.data.SessionToken
import lvluplife.ui.theme.LvlUpTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Tab bar contract (US2, FR-005/FR-006). Synthetic ports: no network, Firebase or XP calculation. */
class MainTabsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val session = SessionController()
    private val progressCalls = AtomicInteger()
    private val progress = RecordFlowProgress {
        progressCalls.incrementAndGet()
        RecordProgressView(10, "synthetic-cycle", "2026-11-14", 1, "novato", 1,
            listOf("STR", "DEX", "INT", "WIT", "MEN", "CON").associateWith { RecordAttributeView(1, 0, 30) })
    }
    private val records = object : RecordFlowRecords {
        override val online = true
        override suspend fun catalog(uid: String) = listOf(RecordOption("strength", "Entrenamiento de fuerza", true))
        override suspend fun draft(uid: String): RecordDraft? = null
        override suspend fun saveDraft(uid: String, draft: RecordDraft) = Unit
        override suspend fun pending(uid: String) = emptyList<OperationSnapshot>()
        override suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot =
            throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        override suspend fun retry(ref: OperationRef): OperationSnapshot = throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
    }
    private val history = HistoryFlowHistory { _, selection ->
        HistoryPage(1, selection.date, selection.date, emptyList())
    }

    private fun tab(route: SessionRoute) = hasText(tabLabel(route)) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun mount() {
        session.authenticate(SessionToken("synthetic-tabs", "synthetic-tabs-token"))
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme {
            SessionHost(session) { MainTabs(session, "Ana", session::logout, records, progress, history = history) }
        } } }
        compose.waitUntil(5_000) { progressCalls.get() == 1 }
    }

    private fun assertOnlySelected(route: SessionRoute) {
        compose.runOnIdle { assertEquals(listOf(route), session.state.value.routes) }
        SessionRoute.tabs.forEach { other ->
            if (other == route) compose.onNode(tab(other)).assertIsDisplayed().assertIsSelected()
            else compose.onNode(tab(other)).assertIsDisplayed().assertIsNotSelected()
        }
        compose.onAllNodesWithTag("tab-indicator", useUnmergedTree = true).assertCountEquals(1)
        // Unmerged tree: the label is a child of the tab node, next to the indicator.
        val unmergedTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasAnyDescendant(hasText(tabLabel(route)))
        compose.onNode(hasTestTag("tab-indicator") and hasAnyAncestor(unmergedTab), useUnmergedTree = true).assertExists()
    }

    @Test fun eachTabOpensWithOneTapIsAnnouncedAsSelectedAndKeepsLoadedProgress() {
        mount()
        assertOnlySelected(SessionRoute.HOME)
        listOf(SessionRoute.ATTRIBUTES, SessionRoute.INFO, SessionRoute.HISTORY, SessionRoute.HOME,
            SessionRoute.HISTORY, SessionRoute.ATTRIBUTES, SessionRoute.HOME).forEach { route ->
            compose.onNode(tab(route)).performClick()
            assertOnlySelected(route)
        }
        compose.onNodeWithText("Ana").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(1, progressCalls.get())
    }

    @Test fun backFromAnotherTabReturnsToPjAndInfoLogsOut() {
        mount()
        compose.onNode(tab(SessionRoute.INFO)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertOnlySelected(SessionRoute.HOME)
        compose.onNode(tab(SessionRoute.INFO)).performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(0)
    }
}
