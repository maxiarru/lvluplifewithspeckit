package lvluplife.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by adapters for the same FirebaseAuth, including successive Activity instances. */
class SignInCoordination {
    internal val serial = Mutex()
    internal val generation = AtomicLong()
}

class FirebaseGoogleSignIn(private val google: GoogleCredentialSource, private val firebase: FirebaseIdentitySource,
    private val coordination: SignInCoordination = SignInCoordination()) : GoogleSignIn {
    override suspend fun signIn(): SignInOutcome = authenticate(picker = true)
    override suspend fun restore(): SignInOutcome = authenticate(picker = false)
    override suspend fun refresh(expectedUid: String): SignInOutcome = authenticate(picker = false, expectedUid = expectedUid, force = true)

    private suspend fun authenticate(picker: Boolean, expectedUid: String? = null, force: Boolean = false): SignInOutcome {
        val generation = coordination.generation.get()
        val caller = currentCoroutineContext()
        return coordination.serial.withLock {
            if (generation != coordination.generation.get()) return@withLock SignInOutcome.Cancelled
            if (!picker && expectedUid != null && firebase.uid() != expectedUid) return@withLock SignInOutcome.Denied
            if (!picker && firebase.uid() == null) return@withLock SignInOutcome.Cancelled
            var touched = false
            try {
                if (picker) {
                    val token = google.request()
                    caller.ensureActive()
                    if (generation != coordination.generation.get()) return@withLock SignInOutcome.Cancelled
                    touched = true
                    // An SDK Task may change auth even after coroutine cancellation. Keep the serial
                    // lease until it settles, then clear stale identity before another attempt starts.
                    withContext(NonCancellable) { firebase.exchange(token) }
                }
                if (!caller.isActive || generation != coordination.generation.get()) {
                    if (touched) firebase.signOut()
                    caller.ensureActive()
                    return@withLock SignInOutcome.Cancelled
                }
                val uid = firebase.uid()
                if (uid == null || !firebase.googleUser()) throw SignInFailure(SignInFailureReason.DENIED)
                touched = true
                val session = withContext(NonCancellable) { firebase.session(force) }
                if (!caller.isActive || generation != coordination.generation.get()) {
                    firebase.signOut(); caller.ensureActive(); return@withLock SignInOutcome.Cancelled
                }
                if (session == null || session.uid != uid || firebase.uid() != uid || !valid(session)) throw SignInFailure(SignInFailureReason.DENIED)
                SignInOutcome.Authenticated(session)
            } catch (cancelled: CancellationException) {
                if (touched) firebase.signOut()
                throw cancelled
            } catch (failure: Exception) {
                if (touched) firebase.signOut()
                when ((failure as? SignInFailure)?.reason) {
                    SignInFailureReason.CANCELLED -> SignInOutcome.Cancelled
                    SignInFailureReason.DENIED -> SignInOutcome.Denied
                    else -> SignInOutcome.Unavailable
                }
            }
        }
    }

    override suspend fun signOut() {
        coordination.generation.incrementAndGet()
        var failed = false
        try { firebase.signOut() } catch (_: Exception) { failed = true }
        try { google.clear() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        if (failed) throw SignInFailure(SignInFailureReason.UNAVAILABLE)
    }

    private fun valid(session: SessionToken) = session.uid.isNotBlank() && session.uid.length <= 128 && '/' !in session.uid &&
        session.uid.none(Char::isISOControl) && session.idToken.isNotEmpty() && session.idToken.all { it.code in 33..126 }
}
