package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Accounts : AccessAccounts {
        val intents = mutableListOf<String>(); var name = ""; var reject = true
        var delay: CompletableDeferred<Unit>? = null; var unknown = false; var retries = 0
        private var ref: OperationRef? = null
        override suspend fun load(uid: String): ProfileLookup = if (name.isEmpty()) ProfileLookup.Required else ProfileLookup.Existing(uid, name)
        override suspend fun complete(uid: String, intentId: String, username: String): OperationSnapshot {
            intents += intentId; val ref = OperationRef(uid, UUID.randomUUID().toString()).also { ref = it }
            delay?.await()
            if (unknown) return OperationSnapshot(ref, PendingState.UNKNOWN)
            if (reject) return OperationSnapshot(ref, PendingState.REJECTED, ApiFailureCode.USERNAME_UNAVAILABLE)
            name = username; return OperationSnapshot(ref, PendingState.CONFIRMED)
        }
        override suspend fun retry(ref: OperationRef): OperationSnapshot {
            assertEquals(this.ref, ref); retries++; return OperationSnapshot(ref, PendingState.UNKNOWN)
        }
    }
    private fun mount(accounts: Accounts) {
        val google = object : GoogleSignIn {
            override suspend fun signIn() = SignInOutcome.Authenticated(SessionToken("owner-a", "synthetic-token"))
            override suspend fun signOut() = Unit
        }
        val session = SessionController()
        compose.runOnIdle { compose.activity.setContent { MaterialTheme { AppNavigation(session, google, accounts) } } }
        compose.onNodeWithText("Continuar con Google").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Nombre de usuario").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun takenNameRetainsInputAndCorrectionCreatesANewIntent() {
        val accounts = Accounts(); mount(accounts)
        compose.onNodeWithText("Nombre de usuario").performTextInput("Taken")
        compose.onNodeWithText("Crear personaje").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ese nombre no está disponible. Elegí otro.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Taken").assertIsDisplayed()
        accounts.reject = false
        compose.onNodeWithText("Nombre de usuario").performTextReplacement("Available")
        compose.onNodeWithText("Crear personaje").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Available").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, accounts.intents.size); assertNotEquals(accounts.intents[0], accounts.intents[1])
    }
    @Test fun savingDisablesDoubleTapWhileTheRequestIsInFlight() {
        val accounts = Accounts().apply { reject = false; delay = CompletableDeferred() }; mount(accounts)
        compose.onNodeWithText("Nombre de usuario").performTextInput("Player")
        compose.onNodeWithText("Crear personaje").performTouchInput { doubleClick() }
        compose.waitUntil(5_000) { accounts.intents.size == 1 }
        compose.onNodeWithText("Crear personaje").assertIsNotEnabled()
        accounts.delay!!.complete(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Player").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, accounts.intents.size)
    }
    @Test fun unknownResultKeepsInputAndRetriesOriginalOperationWithoutNewSubmission() {
        val accounts = Accounts().apply { unknown = true }; mount(accounts)
        compose.onNodeWithText("Nombre de usuario").performTextInput("Player")
        compose.onNodeWithText("Crear personaje").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Confirmación pendiente").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crear personaje").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitUntil(5_000) { accounts.retries == 1 }
        assertEquals(1, accounts.intents.size)
    }
}
