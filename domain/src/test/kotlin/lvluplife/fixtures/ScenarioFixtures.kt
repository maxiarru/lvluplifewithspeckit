package lvluplife.fixtures

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object ScenarioFixtures {
    const val ACCOUNT_A = "synthetic-account-a"
    const val ACCOUNT_B = "synthetic-account-b"
    val START: Instant = Instant.parse("2026-11-10T15:00:00Z")
}

class MutableClock(
    private var current: Instant = ScenarioFixtures.START,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    fun advance(duration: Duration) {
        require(!duration.isNegative)
        current = current.plus(duration)
    }
}
