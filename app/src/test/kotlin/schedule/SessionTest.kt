package schedule

import java.lang.reflect.Modifier
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionTest {
    private val date = LocalDate.of(2026, 10, 15)
    private val factories: List<(LocalDate, String, String, List<String>) -> Session> =
        listOf(::Lecture, ::Practice, ::Exam)

    @Test
    fun createsAllFourTypesWithoutChangingValues() {
        val teachers = listOf("Ivan Ivanov", "ivan ivanov")
        for (factory in factories) {
            val session = factory(date, "Domain modeling", "Room 312", teachers)
            assertEquals(date, session.date)
            assertEquals("Domain modeling", session.topic)
            assertEquals("Room 312", locationOf(session))
            assertEquals(teachers, teachersOf(session))
        }
        assertEquals(listOf("Ivanov"), Lecture(date, "Topic", "Room", listOf("Ivanov")).teachers)
        assertEquals(listOf("Ivanov"), Practice(date, "Topic", "Room", listOf("Ivanov")).teachers)
        val cancelled = Cancelled(date, "Lecture postponed")
        assertEquals(date, cancelled.date)
        assertEquals("Lecture postponed", cancelled.topic)
        assertFalse(Cancelled::class.java.methods.any { it.name in listOf("getLocation", "getTeachers") })
    }

    @Test
    fun rejectsInvalidTopicsForEveryType() {
        for (topic in listOf("", " ", "\t", " Topic", "Topic ", "\u00a0Topic", "To\npic", "To\u0000pic", "To\u007fpic")) {
            for (factory in factories) {
                assertInvalid { factory(date, topic, "Room", listOf("Ivanov", "Petrov")) }
            }
            assertInvalid { Cancelled(date, topic) }
        }
    }

    @Test
    fun checksYearBoundsInEveryConstructor() {
        for (year in listOf(0, 10000)) {
            val invalidDate = LocalDate.of(year, 1, 1)
            for (factory in factories) {
                assertInvalid { factory(invalidDate, "Topic", "Room", listOf("Ivanov", "Petrov")) }
            }
            assertInvalid { Cancelled(invalidDate, "Topic") }
        }
        for (year in listOf(1, 9999)) {
            val boundary = LocalDate.of(year, 12, 31)
            for (factory in factories) {
                assertEquals(boundary, factory(boundary, "Topic", "Room", listOf("Ivanov", "Petrov")).date)
            }
            assertEquals(boundary, Cancelled(boundary, "Topic").date)
        }
    }

    @Test
    fun rejectsInvalidLocationsAndTeacherNamesWithoutRepairingThem() {
        val invalidValues = listOf("", " ", "-", " Room", "Room ", "Room\t312", "Room\u0085")
        for (factory in factories) {
            for (value in invalidValues) {
                assertInvalid { factory(date, "Topic", value, listOf("Ivanov", "Petrov")) }
                assertInvalid { factory(date, "Topic", "Room", listOf("Ivanov", value)) }
            }
            assertInvalid { factory(date, "Topic", "Room", emptyList()) }
            assertInvalid { factory(date, "Topic", "Room", listOf("Ivanov", "Ivanov")) }
        }
        assertInvalid { Exam(date, "Topic", "Room", listOf("Ivanov")) }
    }

    @Test
    fun changingOriginalListDoesNotChangeAnySession() {
        for (factory in factories) {
            val source = mutableListOf("Ivanov", "Petrov")
            val session = factory(date, "Topic", "Room", source)
            source[0] = "Sidorov"
            source.clear()
            assertEquals(listOf("Ivanov", "Petrov"), teachersOf(session))
        }
    }

    @Test
    fun exposedTeacherListsCannotBeModifiedEvenAfterAMutableCast() {
        for (factory in factories) {
            val session = factory(date, "Topic", "Room", listOf("Ivanov", "Petrov"))
            val exposed = teachersOf(session) as MutableList<String>
            assertFailsWith<UnsupportedOperationException> { exposed.clear() }
            assertFailsWith<UnsupportedOperationException> { exposed[0] = "Sidorov" }
            assertFailsWith<UnsupportedOperationException> { exposed.add("Sidorov") }
            assertEquals(listOf("Ivanov", "Petrov"), teachersOf(session))
        }
    }

    @Test
    fun sessionClassesAreFinalAndHaveNoCopyOrSetters() {
        for (type in listOf(Lecture::class.java, Practice::class.java, Exam::class.java, Cancelled::class.java)) {
            assertTrue(Modifier.isFinal(type.modifiers))
            assertFalse(type.methods.any { it.name == "copy" || it.name.startsWith("set") })
        }
    }

    private fun assertInvalid(action: () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException>(block = action)
        assertTrue(!error.message.isNullOrBlank())
    }

    private fun teachersOf(session: Session): List<String> = when (session) {
        is Lecture -> session.teachers
        is Practice -> session.teachers
        is Exam -> session.teachers
        else -> error("Expected an active session")
    }

    private fun locationOf(session: Session): String = when (session) {
        is Lecture -> session.location
        is Practice -> session.location
        is Exam -> session.location
        else -> error("Expected an active session")
    }
}
