package lvluplife.fixtures

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

enum class FaultPoint { NONE, BEFORE_COMMIT, AFTER_COMMIT }
class InjectedFault(val point: FaultPoint) : RuntimeException("Synthetic failure at $point")

/** Test-only boundary simulator; not a Firestore adapter or evidence of remote atomicity. */
class TestEnvironment {
    val accountA = "synthetic-account-a"
    val accountB = "synthetic-account-b"
    var clock: Clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC)
    var fault = FaultPoint.NONE
    private val committed = mutableMapOf<String, String>()

    fun write(uid: String, value: String): String {
        require(uid == accountA || uid == accountB)
        if (fault == FaultPoint.BEFORE_COMMIT) throw InjectedFault(fault)
        committed[uid] = value
        if (fault == FaultPoint.AFTER_COMMIT) throw InjectedFault(fault)
        return value
    }

    fun read(uid: String): String? = committed[uid]
}
