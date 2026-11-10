package lvluplife.persistence

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.Firestore
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import lvluplife.application.UserDocumentStore
import lvluplife.model.Uid

class PersistenceUnavailable : RuntimeException("Persistence temporarily unavailable")

/** Low-level IO adapter. Application mutations must use the revision transaction protocol. */
class FirestoreAccess(private val firestore: Firestore) : UserDocumentStore {
    override fun read(uid: Uid, path: List<String>): Map<String, Any?>? {
        val documentPath = pathFor(uid, path)
        return safeRequest { await(firestore.document(documentPath).get()).data }
    }

    override fun write(uid: Uid, path: List<String>, fields: Map<String, Any?>) {
        val documentPath = pathFor(uid, path)
        safeRequest { await(firestore.document(documentPath).set(fields)); Unit }
    }

    private fun pathFor(uid: Uid, path: List<String>): String {
        require(path.size % 2 == 0) { "Expected relative collection/document pairs" }
        require(path.all { it.isNotBlank() && '/' !in it && it != "." && it != ".." &&
            it.none(Char::isISOControl) && it.toByteArray(Charsets.UTF_8).size <= 1500 }) { "Invalid relative document path" }
        return (listOf("users", uid.value) + path).joinToString("/")
    }

    private fun <T> await(future: ApiFuture<T>): T = try {
        future.get(10, TimeUnit.SECONDS)
    } catch (failure: Exception) {
        future.cancel(true)
        throw failure
    }

    private fun <T> safeRequest(request: () -> T): T = try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        throw PersistenceUnavailable()
    } catch (_: Exception) {
        // Firestore exceptions can contain private document paths and request metadata.
        // Do not log them, return them, or retain the original cause.
        throw PersistenceUnavailable()
    }
}
