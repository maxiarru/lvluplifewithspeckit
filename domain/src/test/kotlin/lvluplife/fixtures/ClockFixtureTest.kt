package lvluplife.fixtures

import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.*

class ClockFixtureTest {
    @Test fun advanceIsReproducibleAcrossIndependentScenarios() {
        val first = MutableClock()
        val second = MutableClock()
        listOf(first, second).forEach { it.advance(Duration.ofSeconds(299)) }
        assertEquals(first.instant(), second.instant())
        assertEquals(ScenarioFixtures.START.plusSeconds(299), first.instant())
        first.advance(Duration.ofSeconds(1))
        assertEquals(ScenarioFixtures.START.plusSeconds(300), first.instant())
        assertEquals(299, Duration.between(ScenarioFixtures.START, second.instant()).seconds)
    }

    @Test fun explicitBusinessZonePreservesInstant() {
        val clock = MutableClock()
        val business = clock.withZone(ZoneOffset.ofHours(-3))
        assertEquals(clock.instant(), business.instant())
        assertEquals(ZoneOffset.ofHours(-3), business.zone)
        assertFailsWith<IllegalArgumentException> { clock.advance(Duration.ofSeconds(-1)) }
        assertNotEquals(ScenarioFixtures.ACCOUNT_A, ScenarioFixtures.ACCOUNT_B)
    }
}
