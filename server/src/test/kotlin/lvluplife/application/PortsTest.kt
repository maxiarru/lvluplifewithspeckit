package lvluplife.application

import java.time.Instant
import lvluplife.model.Uid
import kotlin.test.*

class PortsTest {
    @Test fun persistenceCanBeReplacedWithoutFirebaseAndScopesByUid() {
        val values = mutableMapOf<Pair<Uid, List<String>>, Map<String, Any?>>()
        val store: UserDocumentStore = object : UserDocumentStore {
            override fun read(uid: Uid, path: List<String>) = values[uid to path]
            override fun write(uid: Uid, path: List<String>, fields: Map<String, Any?>) {
                values[uid to path] = fields.toMap()
            }
        }
        val a = Uid("synthetic-a")
        val b = Uid("synthetic-b")
        val path = listOf("records", "synthetic-record")
        store.write(a, path, mapOf("synthetic" to true))
        assertEquals(mapOf("synthetic" to true), store.read(a, path))
        assertNull(store.read(b, path))
    }

    @Test fun timeAndIdentityAreExplicitReplaceablePorts() {
        val instant = Instant.parse("2026-11-10T15:00:00Z")
        val clock = AuthoritativeClock { instant }
        val verifier = IdentityVerifier { token -> if (token == "synthetic-valid") Uid("synthetic-a") else null }
        assertEquals(instant, clock.now())
        assertEquals(Uid("synthetic-a"), verifier.verify("synthetic-valid"))
        assertNull(verifier.verify("invalid"))
    }
}
