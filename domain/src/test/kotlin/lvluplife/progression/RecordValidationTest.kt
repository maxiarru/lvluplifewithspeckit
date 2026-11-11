package lvluplife.progression

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*

class RecordValidationTest {
    private val day = LocalDate.parse("2026-11-11")
    private val cycle = CycleId("synthetic-cycle")
    private val now = Instant.parse("2026-11-11T15:00:00Z")
    private fun duration(id: String, minutes: Int) = ActivityRecord.create(RecordId(id), cycle,
        ActivityCatalog.find(ActivityId("strength"))!!, day, minutes, now)
    private fun validate(hours: String, minutes: String, records: List<ActivityRecord> = emptyList()) =
        RecordValidation.durationMinutes(BigDecimal(hours), BigDecimal(minutes), cycle, day, records)

    @Test fun integralDecimalRepresentationsAreAcceptedWithoutRounding() {
        assertEquals(90, validate("1.000", "30.00"))
        assertEquals(60, validate("1E+0", "0.000"))
        assertEquals(10, validate("0", "1E+1"))
    }
    @Test fun arbitrarilySmallFractionsAndHugeMinutesAreRejected() {
        for ((hours, minutes) in listOf("1.00000000000000000001" to "0",
            "0" to "0.00000000000000000001", "0" to "9223372036854775808")) {
            assertFailsWith<IllegalArgumentException> { validate(hours, minutes) }
        }
    }
    @Test fun extremeDatesAreRejectedWithoutSubtractingFromTheRequestedDate() {
        for (date in listOf(LocalDate.MIN, LocalDate.MAX))
            assertFailsWith<IllegalArgumentException> { RecordValidation.activityDate(date, now) }
    }
    @Test fun alreadyOverdrawnSnapshotCannotAdmitAnyDuration() {
        assertFailsWith<IllegalArgumentException> {
            validate("0", "1", listOf(duration("a", 1440), duration("b", 1440), duration("c", 1440)))
        }
    }
    @Test fun validationDoesNotMutateRecordsOrTheirOrder() {
        val records = mutableListOf(duration("b", 60), duration("a", 60))
        val before = records.toList()
        assertEquals(1320, validate("22", "0", records))
        assertEquals(before, records)
        assertEquals(listOf("b", "a"), records.map { it.id.value })
    }
    @Test fun utcThreeDateWindowCrossesYearBoundary() {
        val at = Instant.parse("2027-01-01T03:00:00Z")
        for (date in listOf("2027-01-01", "2026-12-31", "2026-12-30"))
            RecordValidation.activityDate(LocalDate.parse(date), at)
        assertFailsWith<IllegalArgumentException> { RecordValidation.activityDate(LocalDate.parse("2026-12-29"), at) }
    }
}
