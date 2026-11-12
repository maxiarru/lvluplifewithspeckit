package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.DocumentSnapshot
import com.google.api.core.ApiFuture
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import lvluplife.model.*

data class HistorySnapshot(val revision: Revision, val records: List<ActivityRecord>)

/** Read-only internal pagination; a changed account revision discards every page before retrying. */
class HistoryStore(private val firestore: Firestore) {
    private val accounts = AccountStore(firestore)
    private val records = RecordStore(firestore)

    fun read(uid: Uid, start: LocalDate, endExclusive: LocalDate): HistorySnapshot = safeRead {
        repeat(4) {
            val before = accounts.root(uid)
            val account = accounts.decode(uid, before) ?: throw PersistenceUnavailable()
            val query = firestore.collection("users/${uid.value}/records")
                .whereGreaterThanOrEqualTo("activityDate", start.toString())
                .whereLessThan("activityDate", endExclusive.toString())
                .orderBy("activityDate").orderBy(FieldPath.documentId()).limit(250)
            val documents = mutableListOf<DocumentSnapshot>()
            var cursor: DocumentSnapshot? = null
            while (true) {
                val page = await((cursor?.let { query.startAfter(it) } ?: query).get()).documents
                documents.addAll(page)
                if (page.size < 250) break
                cursor = page.last()
            }
            if (before != accounts.root(uid)) return@repeat
            // No partial success: decoding any returned document can fail the complete period.
            val decoded = documents.map { records.record(it.id, requireNotNull(it.data)) }
            require(decoded.map { it.id }.toSet().size == decoded.size) { "Duplicate paginated record identity" }
            require(decoded.all { it.activityDate >= start && it.activityDate < endExclusive }) { "Record outside requested period" }
            return@safeRead HistorySnapshot(account.revision, Collections.unmodifiableList(decoded))
        }
        throw PersistenceUnavailable()
    }

    private fun <T> await(future: ApiFuture<T>): T = try { future.get(10, TimeUnit.SECONDS) }
    catch (failure: Exception) { future.cancel(true); throw failure }

    private fun <T> safeRead(block: () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw PersistenceUnavailable() }
    catch (_: Exception) { throw PersistenceUnavailable() }
}
