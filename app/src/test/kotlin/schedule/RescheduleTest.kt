package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class RescheduleTest {
    private val originalDate = LocalDate.of(2026, 10, 15)
    private val factories: List<(LocalDate, String, String, List<String>) -> Session> =
        listOf(::Lecture, ::Practice, ::Exam)

    @Test
    fun reschedulesEveryActiveTypeWithoutChangingTheOriginal() {
        val newDate = LocalDate.of(2027, 2, 3)
        for (factory in factories) {
            val names = mutableListOf("Petrov", "Ivanov", "ivanov")
            val original = factory(originalDate, "Domain modeling", "Room 312", names)
            val before = formatSession(original, true)
            val moved = assertIs<RescheduleResult.Success>(original.reschedule(newDate)).session
            names.clear()

            assertNotSame(original, moved)
            assertEquals(original::class, moved::class)
            assertEquals(newDate, moved.date)
            assertEquals("Domain modeling", moved.topic)
            assertEquals(
                "2027-02-03 | ${sessionType(original)} | Domain modeling | Room 312 | Petrov, Ivanov, ivanov",
                formatSession(moved, true),
            )
            assertEquals(originalDate, original.date)
            assertEquals(before, formatSession(original, true))
            val teachers = when (moved) {
                is Lecture -> moved.teachers
                is Practice -> moved.teachers
                is Exam -> moved.teachers
                is Cancelled -> error("Expected an active session")
            }
            assertFailsWith<UnsupportedOperationException> { (teachers as MutableList<String>).clear() }
            assertEquals(before, formatSession(original, true))
        }
    }

    @Test
    fun rejectsDatesOutsideDomainForEveryActiveType() {
        for (factory in factories) {
            val original = factory(originalDate, "Topic", "Room", listOf("Ivanov", "Petrov"))
            for (date in listOf(LocalDate.MIN, LocalDate.of(0, 1, 1), LocalDate.of(10000, 1, 1), LocalDate.MAX)) {
                assertEquals(RescheduleResult.InvalidDate(date), original.reschedule(date))
                assertEquals(originalDate, original.date)
            }
        }
    }

    @Test
    fun acceptsBoundaryYearsLeapDayAndSameDateAndAlwaysCreatesANewObject() {
        for (factory in factories) {
            val original = factory(originalDate, "Topic", "Room", listOf("Ivanov", "Petrov"))
            for (date in listOf(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), LocalDate.of(2028, 2, 29), originalDate)) {
                val moved = assertIs<RescheduleResult.Success>(original.reschedule(date)).session
                assertNotSame(original, moved)
                assertEquals(original::class, moved::class)
                assertEquals(date, moved.date)
                assertEquals(originalDate, original.date)
            }
        }
    }

    @Test
    fun cancelledSessionReturnsTypedRejectionEvenWithAnInvalidTargetDate() {
        val cancelled = Cancelled(originalDate, "Postponed")
        for (date in listOf(LocalDate.of(2026, 10, 20), LocalDate.of(0, 1, 1))) {
            assertEquals(RescheduleResult.CancelledSession, cancelled.reschedule(date))
            assertEquals(originalDate, cancelled.date)
            assertEquals("Postponed", cancelled.topic)
        }
    }
}
