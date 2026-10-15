package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionParserTest {
    private fun row(
        date: String = "2026-10-05",
        type: String = "LECTURE",
        topic: String = "Topic",
        location: String = "Room 1",
        teachers: String = "Ivanov",
    ) = listOf(date, type, topic, location, teachers).joinToString("|")

    private fun reason(line: String): RejectionReason =
        assertIs<ParseResult.Rejected>(parseSessionLine(7, line)).also {
            assertEquals(7L, it.lineNumber)
        }.reason

    @Test
    fun parsesAllTypesAndKeepsOriginalValues() {
        for (type in SessionType.entries) {
            val raw = if (type == SessionType.CANCELLED) {
                row(type = type.name, location = "-", teachers = "-")
            } else {
                row(type = type.name, topic = "Domain modeling", teachers = "Petrov,Ivanov,ivanov")
            }
            val session = assertIs<ParseResult.Success>(parseSessionLine(1, raw)).session
            assertEquals(type, sessionType(session))
            assertEquals(LocalDate.of(2026, 10, 5), session.date)
            if (type != SessionType.CANCELLED) {
                assertTrue(formatSession(session, true).endsWith("Petrov, Ivanov, ivanov"))
            }
        }
        for (date in listOf("0001-01-01", "9999-12-31", "2024-02-29")) {
            assertIs<ParseResult.Success>(parseSessionLine(1, row(date = date)))
        }
        assertFailsWith<IllegalArgumentException> { parseSessionLine(0, row()) }
    }

    @Test
    fun separatorFieldCountAndBlankFieldsHaveHighestPriority() {
        for (line in listOf("", " ", "2026-10-05;LECTURE;Topic;Room;Ivanov")) {
            assertEquals(RejectionReason.InvalidSeparator, reason(line))
        }
        assertEquals(RejectionReason.FieldCount(6), reason(row() + "|"))
        assertEquals(RejectionReason.FieldCount(4), reason("bad|SEMINAR|Topic|"))
        assertEquals(RejectionReason.EmptyField(Field.DATE), reason("|SEMINAR||Room|"))
        assertEquals(RejectionReason.EmptyField(Field.TEACHERS), reason(row(date = "bad", teachers = "")))
        for (field in Field.entries) {
            val fields = row().split('|').toMutableList()
            fields[field.ordinal] = " \t "
            assertEquals(RejectionReason.EmptyField(field), reason(fields.joinToString("|")))
        }
    }

    @Test
    fun invalidDatesPrecedeUnknownTypesAndDomainChecks() {
        for (date in listOf("0000-01-01", "10000-01-01", "2026-02-29", "2026-13-45", "2026-1-05", " 2026-10-05")) {
            assertEquals(RejectionReason.InvalidDate(date), reason(row(date = date, type = "SEMINAR")))
        }
        for (type in listOf("SEMINAR", "exam", "EXAM ", "\u0000")) {
            assertEquals(RejectionReason.UnknownType(type), reason(row(type = type, topic = " Topic")))
        }
    }

    @Test
    fun examCountsNonblankNonPlaceholderElementsBeforeOtherDomainChecks() {
        for ((teachers, count) in listOf("Ivanov" to 1, "-" to 0, ",," to 0, "Ivanov,, ,-" to 1)) {
            assertEquals(
                RejectionReason.TooFewTeachers(count),
                reason(row(type = "EXAM", topic = "Topic\t", teachers = teachers)),
            )
        }
        assertEquals(RejectionReason.DuplicateTeacher("Ivanov"), reason(row(type = "EXAM", teachers = "Ivanov,Ivanov")))
        assertEquals(RejectionReason.InvalidTeacher(""), reason(row(type = "EXAM", teachers = "Ivanov,,Petrov")))
    }

    @Test
    fun domainChecksFollowRequiredPriorityAndFieldOrder() {
        assertEquals(RejectionReason.ControlCharacter(Field.LOCATION), reason(row(topic = " Topic", location = "Room\t", teachers = "Ivanov\u0000")))
        assertEquals(RejectionReason.ControlCharacter(Field.TOPIC), reason(row(topic = "Topic\t", location = "Room\u0000")))
        assertEquals(RejectionReason.SurroundingWhitespace(Field.TOPIC), reason(row(topic = " Topic", location = "-", teachers = "-")))
        assertEquals(RejectionReason.SurroundingWhitespace(Field.LOCATION), reason(row(location = " Room", teachers = " Petrov")))
        assertEquals(RejectionReason.SurroundingWhitespace(Field.TEACHERS), reason(row(teachers = "Ivanov, Petrov")))
        assertEquals(RejectionReason.InvalidCancellationField(Field.LOCATION), reason(row(type = "CANCELLED", teachers = "Ivanov")))
        assertEquals(RejectionReason.InvalidCancellationField(Field.TEACHERS), reason(row(type = "CANCELLED", location = "-")))
        assertEquals(RejectionReason.InvalidLocation, reason(row(location = "-", teachers = "Ivanov,,Ivanov")))
        assertEquals(RejectionReason.InvalidTeacher(""), reason(row(teachers = "Ivanov,Ivanov,,-")))
        assertEquals(RejectionReason.InvalidTeacher("-"), reason(row(teachers = "Ivanov,-,")))
        assertEquals(RejectionReason.DuplicateTeacher("Petrov"), reason(row(teachers = "Petrov,Ivanov,Petrov,Ivanov")))
    }

    @Test
    fun formatsAllReasonsSeparatelyFromParsing() {
        val cases = listOf(
            RejectionReason.InvalidSeparator to "invalid field separator",
            RejectionReason.FieldCount(6) to "got 6 fields, expected 5",
            RejectionReason.EmptyField(Field.DATE) to "empty required field \"date\"",
            RejectionReason.InvalidDate("2026-13-45") to "invalid date \"2026-13-45\"",
            RejectionReason.UnknownType("SEMINAR") to "unknown session type \"SEMINAR\"",
            RejectionReason.TooFewTeachers(1) to "type \"EXAM\" requires at least 2 teachers, got 1",
            RejectionReason.ControlCharacter(Field.TOPIC) to "control character in field \"topic\"",
            RejectionReason.SurroundingWhitespace(Field.TEACHERS) to "surrounding whitespace in field \"teachers\"",
            RejectionReason.InvalidCancellationField(Field.LOCATION) to "type \"CANCELLED\" requires '-' in field \"location\"",
            RejectionReason.InvalidLocation to "active session requires a location other than '-'",
            RejectionReason.InvalidTeacher("-") to "invalid teacher name \"-\"",
            RejectionReason.DuplicateTeacher("Ivanov") to "duplicate teacher name \"Ivanov\"",
        )
        for ((reason, message) in cases) {
            assertEquals("line 42: $message", formatRejection(ParseResult.Rejected(42, reason)))
        }
    }

    @Test
    fun escapesQuotesBackslashesControlsAndEveryNonAsciiUtf16Unit() {
        val raw = "A\\\"\n\t\u0000\u007f\u0085Я😀"
        val escaped = "A\\\\\\\"\\u000A\\u0009\\u0000\\u007F\\u0085\\u042F\\uD83D\\uDE00"
        val message = formatRejection(ParseResult.Rejected(3, RejectionReason.UnknownType(raw)))
        assertEquals("line 3: unknown session type \"$escaped\"", message)
        assertTrue(message.all { it.code in 0x20..0x7e })
    }
}
