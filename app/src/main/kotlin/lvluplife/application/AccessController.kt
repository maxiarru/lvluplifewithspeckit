package lvluplife.application

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lvluplife.data.*

data class AccessView(val busy: Boolean = false, val profile: ProfileLookup? = null,
    val username: String = "", val error: String? = null, val pending: OperationSnapshot? = null,
    val unresolvedIntent: Boolean = false)

/** Main-thread UI controller. Every asynchronous result is scoped to a session generation. */
class AccessController(private val session: SessionController, private val google: GoogleSignIn,
    private val accounts: AccessAccounts, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(AccessView())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var intent: String? = null
    private fun current(g: Long, uid: String? = null) = generation == g && (uid == null || session.current()?.uid == uid)

    fun start() { if (session.current() == null) authenticate(true) else retryLookup() }
    fun signIn() = authenticate(false)
    private fun authenticate(restore: Boolean) {
        if (mutable.value.busy) return
        val g = ++generation; val epoch = session.authenticationEpoch()
        mutable.value = AccessView(busy = true)
        job = scope.launch {
            try {
                val result = if (restore) google.restore() else google.signIn()
                if (!current(g)) return@launch
                when (result) {
                    is SignInOutcome.Authenticated -> {
                        val accepted = withContext(Dispatchers.IO) { session.authenticate(result.session, epoch) }
                        if (accepted && current(g, result.session.uid)) {
                            withContext(Dispatchers.IO) { session.recoverPending() }
                            if (current(g, result.session.uid)) lookup(g, result.session.uid)
                            else if (current(g)) logout()
                        }
                    }
                    SignInOutcome.Cancelled -> mutable.value = AccessView()
                    else -> mutable.value = AccessView(error = "No pudimos iniciar sesión. Intentá de nuevo.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(g)) mutable.value = AccessView(error = "No pudimos iniciar sesión. Intentá de nuevo.") }
            finally { if (current(g)) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun logout() {
        ++generation; job?.cancel(); intent = null
        session.logout(); mutable.value = AccessView(busy = true)
        val g = generation
        // Invalidate Firebase before a possible Activity disposal cancels provider cleanup.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { google.signOut() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The local session is already cleared. */ }
            finally { if (current(g)) mutable.value = AccessView() }
        }
    }
    fun retryLookup() {
        if (mutable.value.busy) return
        val uid = session.current()?.uid ?: return
        val g = generation; mutable.value = mutable.value.copy(busy = true, error = null)
        job = scope.launch {
            try { lookup(g, uid) }
            finally { if (current(g, uid)) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private suspend fun lookup(g: Long, uid: String, refresh: Boolean = true) {
        val ticket = session.beginLoad("profile")
        try {
            val result = accounts.load(uid)
            if (!current(g, uid)) return
            when (result) {
                is ProfileLookup.Existing -> {
                    if (result.uid != uid) throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
                    session.confirm(ticket, result); session.showHome(ticket)
                    mutable.value = AccessView(profile = result)
                    intent = null
                }
                ProfileLookup.Required -> {
                    val name = accounts.draft(uid); val pending = accounts.pendingProfile(uid)
                    if (!current(g, uid)) return
                    session.empty(ticket); session.navigate(SessionRoute.PROFILE)
                    mutable.value = AccessView(profile = result, username = name, pending = pending)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (!current(g, uid)) return
            val code = (error as? OperationBlocked)?.code ?: ApiFailureCode.TEMPORARILY_UNAVAILABLE
            if (code == ApiFailureCode.AUTH_REQUIRED) {
                val epoch = session.authenticationEpoch()
                val renewed = if (refresh) {
                    try { google.refresh(uid) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { SignInOutcome.Unavailable }
                } else SignInOutcome.Unavailable
                if (!current(g, uid)) return
                if (renewed is SignInOutcome.Authenticated && renewed.session.uid == uid &&
                    withContext(Dispatchers.IO) { session.authenticate(renewed.session, epoch) }) lookup(g, uid, false)
                else logout()
            } else {
                session.fail(ticket, code)
                mutable.value = mutable.value.copy(profile = null, error = "No pudimos cargar tu cuenta. Volvé a intentar.")
            }
        }
    }
    fun changeName(name: String) {
        if (!mutable.value.busy && mutable.value.pending == null && !mutable.value.unresolvedIntent)
            mutable.value = mutable.value.copy(username = name, error = null)
    }
    fun submit() = save(false)
    fun retryPending() = save(true)
    private fun save(retry: Boolean) {
        val view = mutable.value
        val uid = session.current()?.uid ?: return
        if (view.busy || view.profile != ProfileLookup.Required) return
        if (!retry && (view.pending != null || view.unresolvedIntent)) return
        if (!Regex("[A-Za-z0-9_]{1,15}").matches(view.username)) {
            mutable.value = view.copy(error = "Usá entre 1 y 15 letras, números o guiones bajos."); return
        }
        val g = generation
        val id = intent ?: UUID.randomUUID().toString().also { intent = it }
        mutable.value = view.copy(busy = true, error = null)
        job = scope.launch {
            try {
                accounts.saveName(uid, view.username)
                if (!current(g, uid)) return@launch
                val result = if (retry && view.pending != null) accounts.retry(view.pending.ref)
                    else accounts.complete(uid, id, view.username)
                if (!current(g, uid) || result.ref.uid != uid) return@launch
                when (result.state) {
                    PendingState.CONFIRMED -> lookup(g, uid)
                    PendingState.REJECTED -> {
                        intent = null
                        mutable.value = view.copy(error = if (result.error == ApiFailureCode.USERNAME_UNAVAILABLE)
                            "Ese nombre no está disponible. Elegí otro." else "Revisá el nombre e intentá de nuevo.", pending = null, unresolvedIntent = false)
                    }
                    else -> mutable.value = view.copy(pending = result, unresolvedIntent = false)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (current(g, uid)) {
                    if ((error as? OperationBlocked)?.code == ApiFailureCode.AUTH_REQUIRED) logout()
                    else mutable.value = view.copy(error = "No pudimos confirmar el personaje. Volvé a intentar.", unresolvedIntent = true)
                }
            } finally { if (current(g, uid)) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
