package lvluplife.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Simulated provider/query ports only. No Google UI, Firebase credentials, or remote writes. */
class AccessFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val controller = SessionController()
    private class Provider : GoogleSignIn {
        val attempts = AtomicInteger()
        val signOuts = AtomicInteger()
        val outcomes = ConcurrentLinkedQueue<CompletableDeferred<SignInOutcome>>()
        fun enqueue(outcome: SignInOutcome) { outcomes.add(CompletableDeferred(outcome)) }
        override suspend fun signIn(): SignInOutcome {
            attempts.incrementAndGet()
            return checkNotNull(outcomes.poll()) { "Missing synthetic sign-in result" }.await()
        }
        override suspend fun signOut() { signOuts.incrementAndGet() }
    }
    private class Accounts : AccessAccounts {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val profiles = ConcurrentHashMap<String, ProfileLookup>()
        @Volatile var failure: ApiFailureCode? = null
        @Volatile var delayedA: CompletableDeferred<ProfileLookup>? = null
        override suspend fun load(uid: String): ProfileLookup {
            requests += uid
            failure?.let { throw OperationBlocked(it) }
            if (uid == "owner-a") delayedA?.let { return it.await() }
            return profiles[uid] ?: ProfileLookup.Required
        }
    }
    private fun authenticated(uid: String) = SignInOutcome.Authenticated(SessionToken(uid, "synthetic-access-token"))
    private fun mount(provider: Provider, accounts: Accounts) = compose.runOnIdle {
        compose.activity.setContent { MaterialTheme { AppNavigation(controller, provider, accounts) } }
    }
    private fun enter() {
        compose.onNodeWithText("Continuar con Google").assertExists()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Continuar con Google").filter(isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Continuar con Google").performClick()
    }
    private fun expect(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun waitForAccount(uid: String) = compose.waitUntil(5_000) { controller.state.value.uid == uid }

    @Test fun newIdentityRequiresExplicitProfileCompletionAfterSuccessfulLookup() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(authenticated("owner-a")); mount(provider, accounts)
        enter()
        expect("Elegí tu nombre de usuario")
        compose.onNodeWithText("Cuenta_A").assertDoesNotExist()
        assertEquals(1, provider.attempts.get()); assertEquals(listOf("owner-a"), accounts.requests.toList())
        compose.runOnIdle { assertEquals("owner-a", controller.current()?.uid) }
    }

    @Test fun cancellationReturnsToAccessWithoutQueryingOrCreatingProfileAndAllowsRetry() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(SignInOutcome.Cancelled); provider.enqueue(authenticated("owner-a"))
        accounts.profiles["owner-a"] = ProfileLookup.Existing("owner-a", "Cuenta_A")
        mount(provider, accounts); enter()
        compose.waitUntil(5_000) { provider.attempts.get() == 1 }
        compose.onNodeWithText("Continuar con Google").assertIsEnabled()
        expect("Iniciá sesión para continuar")
        compose.onNodeWithText("Elegí tu nombre de usuario").assertDoesNotExist()
        assertTrue(accounts.requests.isEmpty()); compose.runOnIdle { assertNull(controller.current()) }
        enter(); expect("Cuenta_A")
        assertEquals(2, provider.attempts.get()); assertEquals(listOf("owner-a"), accounts.requests.toList())
    }

    @Test fun deniedAccessShowsSafeErrorAndDoesNotAutomaticallyRetryOrQuery() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(SignInOutcome.Denied); mount(provider, accounts); enter()
        expect("No pudimos iniciar sesión. Intentá de nuevo.")
        compose.onNodeWithText("Continuar con Google").assertIsEnabled()
        compose.onNodeWithText("Elegí tu nombre de usuario").assertDoesNotExist()
        compose.onNodeWithText("synthetic-access-token", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertNull(controller.current()); assertTrue(controller.state.value.loads.isEmpty()) }
        assertEquals(1, provider.attempts.get()); assertTrue(accounts.requests.isEmpty())
    }

    @Test fun existingAccountReturnsToItsProfileWithoutOfferingAnotherRegistration() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(authenticated("owner-a")); provider.enqueue(authenticated("owner-a"))
        accounts.profiles["owner-a"] = ProfileLookup.Existing("owner-a", "Cuenta_A")
        mount(provider, accounts); enter()
        expect("Cuenta_A")
        compose.onNodeWithText("Elegí tu nombre de usuario").assertDoesNotExist()
        compose.onNodeWithText("Cerrar sesión").performClick()
        expect("Continuar con Google")
        compose.onNodeWithText("Cuenta_A").assertDoesNotExist()
        compose.waitUntil(5_000) { provider.signOuts.get() == 1 }
        compose.runOnIdle { assertNull(controller.current()); assertFalse(controller.back()) }
        enter(); expect("Cuenta_A")
        assertEquals(listOf("owner-a", "owner-a"), accounts.requests.toList())
    }

    @Test fun switchingThroughAccessClearsPreviousCachePendingAndBackStack() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(authenticated("owner-a")); provider.enqueue(authenticated("owner-b"))
        accounts.profiles["owner-a"] = ProfileLookup.Existing("owner-a", "Cuenta_A")
        accounts.profiles["owner-b"] = ProfileLookup.Existing("owner-b", "Cuenta_B")
        mount(provider, accounts); enter(); expect("Cuenta_A")
        compose.runOnIdle {
            controller.pending(controller.beginLoad("private-a"), OperationRef("owner-a", "123e4567-e89b-42d3-a456-426614174000"))
            controller.navigate(SessionRoute.HISTORY)
        }
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Cuenta_A").assertDoesNotExist()
        expect("Continuar con Google")
        enter(); expect("Cuenta_B")
        compose.onNodeWithText("Cuenta_A").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("owner-b", controller.current()?.uid)
            assertFalse(controller.state.value.loads.containsKey("private-a"))
            assertTrue(controller.state.value.pending.none { it.ref.uid == "owner-a" })
            assertEquals(listOf(SessionRoute.HOME), controller.state.value.routes)
        }
        assertEquals(listOf("owner-a", "owner-b"), accounts.requests.toList())
    }

    @Test fun doubleTapStartsOnlyOneProviderAttemptWhileAuthenticationIsPending() {
        val provider = Provider(); val accounts = Accounts(); val pending = CompletableDeferred<SignInOutcome>()
        provider.outcomes.add(pending)
        accounts.profiles["owner-a"] = ProfileLookup.Existing("owner-a", "Cuenta_A")
        mount(provider, accounts)
        compose.onNodeWithText("Continuar con Google").performTouchInput { doubleClick() }
        compose.waitUntil(5_000) { provider.attempts.get() == 1 }
        compose.onNodeWithText("Continuar con Google").assertIsNotEnabled()
        assertTrue(accounts.requests.isEmpty())
        pending.complete(authenticated("owner-a"))
        expect("Cuenta_A")
        assertEquals(1, provider.attempts.get()); assertEquals(listOf("owner-a"), accounts.requests.toList())
    }

    @Test fun lateAccountAResponseCannotReplaceAccountBAfterLogout() {
        val provider = Provider(); val accounts = Accounts(); val pendingA = CompletableDeferred<ProfileLookup>()
        provider.enqueue(authenticated("owner-a")); provider.enqueue(authenticated("owner-b"))
        accounts.delayedA = pendingA
        accounts.profiles["owner-b"] = ProfileLookup.Existing("owner-b", "Cuenta_B")
        mount(provider, accounts); enter()
        compose.waitUntil(5_000) { accounts.requests.contains("owner-a") }; waitForAccount("owner-a")
        compose.onNodeWithText("Cerrar sesión").performClick()
        expect("Continuar con Google")
        enter(); expect("Cuenta_B")
        pendingA.complete(ProfileLookup.Existing("owner-a", "Cuenta_A"))
        compose.waitForIdle()
        expect("Cuenta_B"); compose.onNodeWithText("Cuenta_A").assertDoesNotExist()
        compose.runOnIdle { assertEquals("owner-b", controller.current()?.uid) }
        assertEquals(listOf("owner-a", "owner-b"), accounts.requests.toList())
    }

    @Test fun failedProfileLookupIsAnErrorNotANewProfileAndRetryDoesNotSignInAgain() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(authenticated("owner-a")); accounts.failure = ApiFailureCode.TEMPORARILY_UNAVAILABLE
        mount(provider, accounts); enter()
        expect("No pudimos cargar tu cuenta. Volvé a intentar.")
        compose.onNodeWithText("Elegí tu nombre de usuario").assertDoesNotExist()
        accounts.failure = null; accounts.profiles["owner-a"] = ProfileLookup.Existing("owner-a", "Cuenta_A")
        compose.onNodeWithText("Reintentar").performClick()
        expect("Cuenta_A")
        assertEquals(1, provider.attempts.get()); assertEquals(listOf("owner-a", "owner-a"), accounts.requests.toList())
    }

    @Test fun expiredSessionDuringLookupReturnsToAccessUntilExplicitAuthentication() {
        val provider = Provider(); val accounts = Accounts()
        provider.enqueue(authenticated("owner-a")); provider.enqueue(authenticated("owner-b"))
        accounts.failure = ApiFailureCode.AUTH_REQUIRED
        mount(provider, accounts); enter()
        compose.waitUntil(5_000) { accounts.requests.contains("owner-a") }
        expect("Continuar con Google")
        compose.runOnIdle { assertNull(controller.current()); assertTrue(controller.state.value.pending.isEmpty()) }
        assertEquals(1, provider.attempts.get())
        accounts.failure = null; accounts.profiles["owner-b"] = ProfileLookup.Existing("owner-b", "Cuenta_B")
        enter(); expect("Cuenta_B")
        compose.onNodeWithText("Cuenta_A").assertDoesNotExist()
        assertEquals(listOf("owner-a", "owner-b"), accounts.requests.toList())
    }
}
