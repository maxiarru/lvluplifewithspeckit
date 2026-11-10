package lvluplife.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

private const val A = "synthetic-account-a"
private const val B = "synthetic-account-b"
private const val OPERATION = "123e4567-e89b-42d3-a456-426614174000"
private fun operation(uid: String = A, state: PendingState = PendingState.PREPARED) = PendingOperation(
    uid, OPERATION, "CREATE_RECORD", """{"activityId":"strength"}""", "synthetic-hash", "cycle-a", state,
)

@RunWith(AndroidJUnit4::class)
class PendingStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withStore(test: (PendingStore) -> Unit) {
        val name = "test-${UUID.randomUUID()}.db"
        try { PendingDatabase.open(context, name).use(test) }
        finally { context.deleteDatabase(name) }
    }

    @Test fun preparedEnvelopeIsDurableBeforeAnyTransportRuns() {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            PendingDatabase.open(context, name).use { it.prepare(operation()) }
            PendingDatabase.open(context, name).use {
                assertEquals(listOf(operation()), it.pendingFor(A))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun compositeIdentityAndDraftsAreScopedToOriginalUid() = withStore { store ->
        store.prepare(operation(A))
        store.prepare(operation(B))
        store.saveDraft(Draft(A, "draft-a"))
        store.saveDraft(Draft(B, "draft-b"))
        assertEquals(listOf(operation(A)), store.pendingFor(A))
        assertEquals(listOf(operation(B)), store.pendingFor(B))
        assertEquals(Draft(A, "draft-a"), store.draftFor(A))
        assertEquals(Draft(B, "draft-b"), store.draftFor(B))
        assertTrue(store.pendingFor("another-account").isEmpty())
    }

    @Test fun absentReceiptKeepsUnknownAndCannotRemovePending() = withStore { store ->
        store.prepare(operation(state = PendingState.UNKNOWN))
        store.resolveAbsent(A, OPERATION)
        assertEquals(PendingState.UNKNOWN, store.pendingFor(A).single().state)
        assertThrows(IllegalStateException::class.java) { store.removeDefinitive(A, OPERATION) }
        assertEquals(1, store.pendingFor(A).size)
    }

    @Test fun onlyDefinitiveResultsAllowRemovalWithoutAffectingOtherAccount() = withStore { store ->
        store.prepare(operation(A))
        store.prepare(operation(B))
        store.updateState(A, OPERATION, PendingState.CONFIRMED)
        store.removeDefinitive(A, OPERATION)
        assertTrue(store.pendingFor(A).isEmpty())
        assertEquals(listOf(operation(B)), store.pendingFor(B))
    }

    @Test fun retriesCannotReplaceEnvelopeForExistingIdentity() = withStore { store ->
        val original = operation()
        store.prepare(original)
        store.prepare(original)
        assertThrows(IllegalArgumentException::class.java) {
            store.prepare(original.copy(envelope = "changed", requestHash = "changed"))
        }
        assertEquals(listOf(original), store.pendingFor(A))
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProcessStage

/** Executed one method per instrumentation process by scripts/test-pending-process.ps1. */
@RunWith(AndroidJUnit4::class)
class PendingStoreProcessStages {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "process-death-pending.db"

    @Test @ProcessStage fun persistBeforeProcessDeath() {
        context.deleteDatabase(name)
        PendingDatabase.open(context, name).use { store ->
            store.prepare(operation(state = PendingState.SENDING))
            store.saveDraft(Draft(A, "retained-draft"))
        }
    }

    @Test @ProcessStage fun recoverAfterProcessDeath() {
        try {
            PendingDatabase.open(context, name).use { store ->
                val recovered = store.pendingFor(A).single()
                assertEquals(OPERATION, recovered.operationId)
                assertEquals(A, recovered.uid)
                assertTrue(recovered.state in setOf(PendingState.SENDING, PendingState.UNKNOWN))
                assertTrue(store.pendingFor(B).isEmpty())
                assertNull(store.draftFor(B))
                assertEquals(Draft(A, "retained-draft"), store.draftFor(A))
            }
        } finally { context.deleteDatabase(name) }
    }
}
