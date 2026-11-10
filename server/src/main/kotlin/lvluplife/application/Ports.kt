package lvluplife.application

import java.time.Instant
import lvluplife.model.Uid

fun interface AuthoritativeClock {
    fun now(): Instant
}

fun interface IdentityVerifier {
    /** Null means invalid identity; unavailable dependencies must throw, never authenticate. */
    fun verify(idToken: String): Uid?
}

interface UserDocumentStore {
    /** Empty path is the user's root; other paths are collection/document pairs below it. */
    fun read(uid: Uid, path: List<String>): Map<String, Any?>?
    fun write(uid: Uid, path: List<String>, fields: Map<String, Any?>)
}
