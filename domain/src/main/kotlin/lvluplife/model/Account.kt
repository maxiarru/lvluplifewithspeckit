package lvluplife.model

import java.time.Instant
import java.util.Collections
import java.util.Locale

@JvmInline
value class Username(val value: String) {
    init { require(Regex("[A-Za-z0-9_]{1,15}").matches(value)) { "Invalid username" } }
    val key: String get() = value.lowercase(Locale.ROOT)
}

enum class Attribute { STR, DEX, INT, WIT, MEN, CON }

data object InitialAttribute {
    val level: Int = 1
    val xp: Int = 0
}

data object InitialCharacter {
    val className: String = "Novato"
    val stage: Int = 1
    val generalLevel: Int = 1
    val generalRemainder: Int = 0
    val attributes: Map<Attribute, InitialAttribute> = Collections.unmodifiableMap(Attribute.entries.associateWith { InitialAttribute })
}

class AccountStart internal constructor(val account: Account, val cycle: Cycle) {
    val character = InitialCharacter
}

data class Account(val uid: Uid, val username: Username, val createdAt: Instant,
    val currentCycleId: CycleId, val revision: Revision, val schemaVersion: Int = 1) {
    init {
        require(revision.value > 0) { "Invalid account revision" }
        require(schemaVersion > 0) { "Invalid account schema version" }
    }
    companion object {
        /** No IO/clock/ID allocation. Persistence enforces uniqueness and supplies the accepted revision. */
        fun start(uid: Uid, username: String, cycleId: CycleId, now: Instant,
            revision: Revision = Revision(1), rulesVersion: RulesVersion = RulesVersion.CURRENT): AccountStart =
            AccountStart(Account(uid, Username(username), now, cycleId, revision), Cycle(uid, cycleId, now, rulesVersion))
    }
}
