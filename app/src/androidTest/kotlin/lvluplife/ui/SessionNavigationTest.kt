package lvluplife.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import lvluplife.MainActivity
import lvluplife.application.SessionRoute
import lvluplife.data.ApiFailureCode
import lvluplife.data.SessionToken
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val controller get() = compose.activity.sessionController
    private fun login(uid: String) = compose.runOnIdle { controller.authenticate(SessionToken(uid, "synthetic-token")) }

    @Test fun logoutRemovesPrivateScreenAndBackCannotRestoreIt() {
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        login("owner-a")
        compose.runOnIdle { controller.confirm(controller.beginLoad("progress"), "private-a") }
        compose.runOnIdle {
            compose.activity.setContent {
                MaterialTheme {
                    SessionHost(controller) { view ->
                        val privateText = remember { (view.loads["progress"] as? LoadState.Confirmed)?.value as? String }
                        privateText?.let { Text(it) }
                    }
                }
            }
        }
        compose.onNodeWithText("private-a").assertIsDisplayed()
        compose.onNodeWithText("Historial").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(listOf(SessionRoute.HOME), controller.state.value.routes) }
        compose.onNodeWithText("Historial").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        compose.onNodeWithText("private-a", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertFalse(controller.back()) }
        login("owner-b")
        compose.onNodeWithText("private-a").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(SessionRoute.HOME), controller.state.value.routes)
            assertTrue(controller.state.value.loads.isEmpty())
        }
    }

    @Test fun emptyErrorStaleAndPendingHaveDistinctVisibleMessages() {
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        login("owner-a")
        compose.runOnIdle { controller.empty(controller.beginLoad("progress")) }
        compose.onNodeWithText("Sin resultados").assertIsDisplayed()
        compose.runOnIdle { controller.fail(controller.beginLoad("progress"), ApiFailureCode.TIMEOUT) }
        compose.onNodeWithText("No pudimos cargar los datos. Intentá de nuevo.").assertIsDisplayed()
        compose.onNodeWithText("Sin resultados").assertDoesNotExist()
        compose.runOnIdle {
            controller.confirm(controller.beginLoad("progress"), "revision-1")
            controller.fail(controller.beginLoad("progress"), ApiFailureCode.NETWORK_ERROR)
        }
        compose.onNodeWithText("Datos desactualizados. Volvé a intentar.").assertIsDisplayed()
        compose.runOnIdle {
            controller.pending(controller.beginLoad("progress"), lvluplife.application.OperationRef("owner-a", "123e4567-e89b-42d3-a456-426614174000"))
        }
        compose.onNodeWithText("Confirmación pendiente").assertIsDisplayed()
        compose.onNodeWithText("Datos confirmados").assertDoesNotExist()
    }

    @Test fun expirationClearsNavigationAndLateAccountDataIsNeverRendered() {
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        login("owner-a")
        val old = compose.runOnIdle { controller.beginLoad("progress") }
        compose.runOnIdle {
            controller.navigate(SessionRoute.HISTORY)
            controller.fail(controller.beginLoad("history"), ApiFailureCode.AUTH_REQUIRED)
        }
        compose.onNodeWithText("Iniciá sesión para continuar").assertIsDisplayed()
        login("owner-b")
        compose.runOnIdle { controller.confirm(old, "private-a") }
        compose.onNodeWithText("private-a", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertTrue(controller.state.value.loads.isEmpty()) }
    }
}
