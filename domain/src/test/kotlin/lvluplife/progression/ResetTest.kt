package lvluplife.progression

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*
import lvluplife.record.CorrectionRules

/** Literal reset contracts over pure domain values; database preservation/races belong to T072/T075. */
class ResetTest {
    private val now=Instant.parse("2026-11-12T18:00:00Z")
    private val day=LocalDate.parse("2026-11-12")
    private val uid=Uid("synthetic-reset-owner")
    private val oldId=CycleId("synthetic-old-cycle")
    private val newId=CycleId("synthetic-new-cycle")
    private val account=Account(uid,Username("Reset_Hero"),Instant.parse("2026-10-01T15:00:00Z"),oldId,Revision(27),schemaVersion=3)
    private val oldCycle=Cycle(uid,oldId,Instant.parse("2026-11-01T15:00:00Z"))
    private fun reset(at: Instant=now)=ResetRules.reset(account,oldCycle,newId,at,confirmed=true)
    private fun record(id: String,cycle: CycleId=oldId,activity: String="strength",minutes: Int?=30,
        date: LocalDate=day,created: Instant=now.minusSeconds(60)) = ActivityRecord.create(RecordId(id),cycle,
            ActivityCatalog.find(ActivityId(activity))!!,date,minutes,created)
    private fun replay(transition: ResetTransition,records: List<ActivityRecord>,at: Instant=now.plusSeconds(10)) =
        ReplayEngine.rebuild(transition.newCycle,records,at)

    @Test fun closesOnlyCurrentCycleAndStartsDistinctCycleAtExactAuthoritativeInstant() {
        val at=now.plusNanos(123);val result=reset(at)
        assertEquals(oldCycle.copy(closedAt=at),result.closedCycle)
        assertEquals(Cycle(uid,newId,at,oldCycle.rulesVersion),result.newCycle)
        assertFalse(result.closedCycle.isOpen);assertTrue(result.newCycle.isOpen)
        assertTrue(oldCycle.isOpen);assertEquals(oldId,account.currentCycleId)
    }
    @Test fun initialProjectionHasSixLevelOneZeroXpAndNoGeneralRemainderOrInactivity() {
        val result=reset();val p=result.progression
        assertEquals(uid,p.uid);assertEquals(newId,p.cycleId);assertEquals(Revision(28),p.revision)
        assertEquals(now,p.computedAt);assertEquals(day,p.businessDate)
        assertEquals(Attribute.entries.toSet(),p.attributes.keys)
        for(balance in p.attributes.values) assertEquals(InactivityBalance(AttributeBalance(1,0),0),balance)
        assertEquals(GeneralBalance(1,0),p.general);assertEquals("novato",p.classId);assertEquals(1,p.stage)
    }
    @Test fun keepsAccountIdentityUsernameCreationSchemaAndOwnedDefinitionHistoryValues() {
        val activity=CustomActivity.create(uid,ActivityId("synthetic-owned"),"Actividad propia","Rutina",STR,DEX,
            Instant.parse("2026-10-15T15:00:00Z"))
        val original=record("old-record");val deleted=record("old-deleted").markDeleted(now.minusSeconds(30))
        val history=listOf(original,deleted);val definitions=listOf(activity)
        val result=reset()
        assertEquals(account.copy(currentCycleId=newId,revision=Revision(28)),result.account)
        assertEquals(Revision(27),account.revision);assertEquals(Username("Reset_Hero"),result.account.username)
        assertSame(activity,definitions.single());assertEquals(ActivityId("synthetic-owned"),activity.id)
        assertEquals(listOf(original,deleted),history);assertEquals(oldId,original.cycleId)
        assertEquals(1L,original.version);assertNull(original.deletedAt)
        assertEquals(2L,deleted.version);assertEquals(now.minusSeconds(30),deleted.deletedAt)
    }
    @Test fun oldDailyStrengthCapDoesNotConsumeNewCycleAward() {
        val old=record("old-cap",minutes=240)
        assertEquals(mapOf(STR to 48),ReplayEngine.rebuild(oldCycle,listOf(old),now).recordXp.getValue(old.id.value))
        val result=reset();val fresh=record("new-strength",newId,minutes=30,created=now.plusSeconds(1))
        val rebuilt=replay(result,listOf(old,fresh))
        assertEquals(mapOf(STR to 6),rebuilt.recordXp.getValue("new-strength"))
        assertEquals(AttributeBalance(1,6),rebuilt.attributes.getValue(STR).attribute)
        assertEquals(listOf("new-strength"),rebuilt.orderedRecordIds)
        assertEquals(GeneralBalance(1,0),rebuilt.general);assertEquals(ClassAppearance("Novato",1),rebuilt.appearance)
    }
    @Test fun old24HoursDoNotConsumeNewCycleDurationAndOneHourLeaves23Available() {
        val old=record("old-full-day",minutes=1440);val result=reset()
        val minutes=RecordValidation.durationMinutes(BigDecimal.ONE,BigDecimal.ZERO,newId,day,listOf(old))
        assertEquals(60,minutes)
        val fresh=record("new-hour",newId,minutes=minutes,created=now.plusSeconds(1))
        assertEquals(mapOf(day to 60),replay(result,listOf(old,fresh)).durationByDate)
        assertEquals(1380,RecordValidation.durationMinutes(BigDecimal(23),BigDecimal.ZERO,newId,day,listOf(old,fresh)))
        assertFailsWith<IllegalArgumentException> {
            RecordValidation.durationMinutes(BigDecimal(23),BigDecimal.ONE,newId,day,listOf(old,fresh))
        }
        assertEquals(1440,old.durationMinutes);assertNull(old.deletedAt)
    }
    @Test fun fourPreviousMealsDoNotConsumeNewCycleFourScoringMeals() {
        val old=(1..4).map { record("old-meal-$it",activity="meal",minutes=null,created=now.minusSeconds(60-it.toLong())) }
        val result=reset()
        val fresh=(1..5).map { record("new-meal-$it",newId,"meal",null,created=now.plusSeconds(it.toLong())) }
        val rebuilt=replay(result,old+fresh)
        for(i in 1..4) assertEquals(mapOf(CON to 7),rebuilt.recordXp.getValue("new-meal-$i"))
        assertEquals(mapOf(CON to 0),rebuilt.recordXp.getValue("new-meal-5"))
        assertEquals(AttributeBalance(1,28),rebuilt.attributes.getValue(CON).attribute)
        assertEquals(5,rebuilt.orderedRecordIds.size);assertEquals(4,old.size)
    }
    @Test fun old59SleepMinutesCannotCombineWithNewSingleMinute() {
        val old=record("old-sleep",activity="sleep",minutes=59);val result=reset()
        val first=record("new-sleep-first",newId,"sleep",1,created=now.plusSeconds(1))
        val one=replay(result,listOf(old,first))
        assertEquals(mapOf(MEN to 0),one.recordXp.getValue("new-sleep-first"))
        assertEquals(AttributeBalance(1,0),one.attributes.getValue(MEN).attribute)
        val second=record("new-sleep-second",newId,"sleep",59,created=now.plusSeconds(2))
        val complete=replay(result,listOf(old,first,second))
        assertEquals(mapOf(MEN to 3),complete.recordXp.getValue("new-sleep-second"))
        assertEquals(AttributeBalance(1,3),complete.attributes.getValue(MEN).attribute)
    }
    @Test fun oldMentalSleepCapDoesNotConsumeNewCycleMentalCap() {
        val old=record("old-mental-cap",activity="sleep",minutes=600);val result=reset()
        val fresh=record("new-mental-cap",newId,"sleep",600,created=now.plusSeconds(1))
        val rebuilt=replay(result,listOf(old,fresh))
        assertEquals(mapOf(MEN to 30),rebuilt.recordXp.getValue("new-mental-cap"))
        assertEquals(AttributeBalance(2,0),rebuilt.attributes.getValue(MEN).attribute)
        assertEquals(GeneralBalance(2,0),rebuilt.general)
    }
    @Test fun yesterdayAndDayBeforeRecordsCreatedAfterResetBelongToNewCycleAndGrantXp() {
        val result=reset()
        val fresh=(1..2).map { age ->
            val date=day.minusDays(age.toLong());RecordValidation.activityDate(date,now)
            record("new-backdated-$age",newId,date=date,created=now.plusSeconds(age.toLong()))
        }
        val rebuilt=replay(result,fresh)
        assertEquals(AttributeBalance(1,12),rebuilt.attributes.getValue(STR).attribute)
        assertEquals(0,rebuilt.attributes.getValue(STR).inactiveDates)
        assertTrue(fresh.all { it.cycleId==newId && it.createdAt>=result.newCycle.startedAt })
        assertEquals(setOf("new-backdated-1","new-backdated-2"),rebuilt.recordXp.keys)
    }
    @Test fun oldInactivityDoesNotPenalizeNewCycleAtCreation() {
        assertEquals(11,ReplayEngine.rebuild(oldCycle,emptyList(),now).attributes.getValue(STR).inactiveDates)
        val result=reset()
        val rebuilt=replay(result,emptyList(),now)
        for(balance in rebuilt.attributes.values) assertEquals(InactivityBalance(AttributeBalance(1,0),0),balance)
        assertEquals(GeneralBalance(1,0),rebuilt.general)
        assertTrue(rebuilt.recordXp.isEmpty());assertTrue(rebuilt.durationByDate.isEmpty())
    }
    @Test fun sundayBackdateAfterMondayResetDoesNotCountSundayAsNewCycleInactivity() {
        val monday=Instant.parse("2026-11-09T23:30:00Z");val result=reset(monday)
        val sunday=record("new-sunday",newId,date=LocalDate.parse("2026-11-08"),created=monday.plusSeconds(1))
        val tuesday=replay(result,listOf(sunday),Instant.parse("2026-11-10T03:00:00Z"))
        assertEquals(InactivityBalance(AttributeBalance(1,6),1),tuesday.attributes.getValue(STR))
        assertEquals(1,tuesday.attributes.getValue(DEX).inactiveDates)
    }
    @Test fun firstSixClosedDatesHaveNoPenaltyAndSeventhDeductsOnlyOnceFromNewCycle() {
        val result=reset();val backdated=record("new-yesterday",newId,date=day.minusDays(1),created=now.plusSeconds(1))
        val six=replay(result,listOf(backdated),now.plusSeconds(6*86400))
        assertEquals(InactivityBalance(AttributeBalance(1,6),6),six.attributes.getValue(STR))
        val seven=replay(result,listOf(backdated),now.plusSeconds(7*86400))
        assertEquals(InactivityBalance(AttributeBalance(1,1),7),seven.attributes.getValue(STR))
        assertEquals(seven,replay(result,listOf(backdated),now.plusSeconds(7*86400)))
    }
    @Test fun recentOldRecordCannotBeCorrectedOrDeletedThroughNewCycle() {
        val old=record("recent-old",created=now.minusSeconds(1));val result=reset()
        assertFailsWith<IllegalArgumentException> {
            CorrectionRules.edit(old,result.newCycle,day,old.definition,BigDecimal.ZERO,BigDecimal(35),listOf(old),now.plusSeconds(1))
        }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(old,result.newCycle,now.plusSeconds(1)) }
        assertEquals(1L,old.version);assertNull(old.deletedAt)
    }
    @Test fun missingExplicitConfirmationCannotProduceTransition() {
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account,oldCycle,newId,now,confirmed=false) }
        assertTrue(oldCycle.isOpen);assertEquals(oldId,account.currentCycleId);assertEquals(Revision(27),account.revision)
    }
    @Test fun alreadyClosedCycleAndReusedCurrentIdentityAreRejected() {
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account,oldCycle.copy(closedAt=now),newId,now,true) }
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account,oldCycle,oldId,now,true) }
    }
    @Test fun cycleOwnerAndAccountCurrentPointerMustMatch() {
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account,oldCycle.copy(uid=Uid("synthetic-other")),newId,now,true) }
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account.copy(currentCycleId=CycleId("another-cycle")),oldCycle,newId,now,true) }
    }
    @Test fun resetCannotPrecedeCycleCreation() {
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account,oldCycle,newId,oldCycle.startedAt.minusNanos(1),true) }
    }
    @Test fun unsupportedProgressionRulesCannotBeSilentlyReplaced() {
        assertFailsWith<IllegalArgumentException> { ResetRules.reset(account,oldCycle.copy(rulesVersion=RulesVersion(2)),newId,now,true) }
    }
    @Test fun revisionOverflowCannotPublishWrappedRevision() {
        assertFailsWith<ArithmeticException> { ResetRules.reset(account.copy(revision=Revision(Long.MAX_VALUE)),oldCycle,newId,now,true) }
    }
    @Test fun sameExplicitInputsYieldSameTransitionValuesWithoutAllocatingAnotherIdentity() {
        val first=reset();val second=reset()
        assertEquals(first.account,second.account);assertEquals(first.closedCycle,second.closedCycle);assertEquals(first.newCycle,second.newCycle)
        assertEquals(first.progression.attributes,second.progression.attributes)
        assertEquals(first.progression.general,second.progression.general)
        assertEquals(first.progression.revision,second.progression.revision)
        assertEquals(first.progression.computedAt,second.progression.computedAt)
    }

    @Test fun existingReplaySeparatesOldCapsMealsSleepRemainderAndDurationFromExplicitNewCycle() {
        val freshCycle=Cycle(uid,newId,now)
        val previous=listOf(record("old-strength-cap",minutes=240),record("old-sleep-cap-remainder",activity="sleep",minutes=659))+
            (1..4).map { record("old-meal-$it",activity="meal",minutes=null) }
        val fresh=listOf(record("new-strength",newId,created=now.plusSeconds(1)),
            record("new-sleep",newId,"sleep",1,created=now.plusSeconds(2)),
            record("new-meal",newId,"meal",null,created=now.plusSeconds(3)))
        val result=ReplayEngine.rebuild(freshCycle,previous+fresh,now.plusSeconds(10))
        assertEquals(AttributeBalance(1,6),result.attributes.getValue(STR).attribute)
        assertEquals(AttributeBalance(1,0),result.attributes.getValue(MEN).attribute)
        assertEquals(AttributeBalance(1,7),result.attributes.getValue(CON).attribute)
        assertEquals(mapOf(day to 31),result.durationByDate)
        assertEquals(setOf("new-strength","new-sleep","new-meal"),result.recordXp.keys)
        assertEquals(6,previous.size);assertTrue(previous.all { it.deletedAt==null && it.version==1L })
    }
    @Test fun existingReplayAcceptsBackdatedNewCycleRecordWithoutPreStartInactivity() {
        val monday=Instant.parse("2026-11-09T23:30:00Z")
        val freshCycle=Cycle(uid,newId,monday)
        val sunday=record("new-sunday",newId,date=LocalDate.parse("2026-11-08"),created=monday.plusSeconds(1))
        val result=ReplayEngine.rebuild(freshCycle,listOf(sunday),Instant.parse("2026-11-10T03:00:00Z"))
        assertEquals(InactivityBalance(AttributeBalance(1,6),1),result.attributes.getValue(STR))
        assertEquals(1,result.attributes.getValue(DEX).inactiveDates)
    }
    @Test fun existingReplayStartsNewGracePeriodAndDoesNotApplySeventhDatePenaltyTwice() {
        val freshCycle=Cycle(uid,newId,now)
        val yesterday=record("new-yesterday",newId,date=day.minusDays(1),created=now.plusSeconds(1))
        val six=ReplayEngine.rebuild(freshCycle,listOf(yesterday),now.plusSeconds(6*86400L))
        val seven=ReplayEngine.rebuild(freshCycle,listOf(yesterday),now.plusSeconds(7*86400L))
        assertEquals(InactivityBalance(AttributeBalance(1,6),6),six.attributes.getValue(STR))
        assertEquals(InactivityBalance(AttributeBalance(1,1),7),seven.attributes.getValue(STR))
        assertEquals(seven,ReplayEngine.rebuild(freshCycle,listOf(yesterday),now.plusSeconds(7*86400L)))
    }
}
