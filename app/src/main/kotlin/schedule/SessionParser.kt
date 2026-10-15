package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class Field(val label: String) {
    DATE("date"), TYPE("type"), TOPIC("topic"), LOCATION("location"), TEACHERS("teachers"),
}

sealed interface RejectionReason {
    data object InvalidSeparator : RejectionReason
    data class FieldCount(val actual: Int) : RejectionReason
    data class EmptyField(val field: Field) : RejectionReason
    data class InvalidDate(val value: String) : RejectionReason
    data class UnknownType(val value: String) : RejectionReason
    data class TooFewTeachers(val actual: Int) : RejectionReason
    data class ControlCharacter(val field: Field) : RejectionReason
    data class SurroundingWhitespace(val field: Field) : RejectionReason
    data class InvalidCancellationField(val field: Field) : RejectionReason
    data object InvalidLocation : RejectionReason
    data class InvalidTeacher(val value: String) : RejectionReason
    data class DuplicateTeacher(val value: String) : RejectionReason
}

sealed interface ParseResult {
    data class Success(val session: Session) : ParseResult
    data class Rejected(val lineNumber: Long, val reason: RejectionReason) : ParseResult
}

private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

fun parseSessionLine(number: Long, line: String): ParseResult {
    require(number >= 1) { "Line number must be positive" }
    fun reject(reason: RejectionReason) = ParseResult.Rejected(number, reason)

    if ('|' !in line) {
        return reject(RejectionReason.InvalidSeparator)
    }
    val fields = line.split('|')
    if (fields.size != 5) {
        return reject(RejectionReason.FieldCount(fields.size))
    }
    for (field in Field.entries) {
        if (fields[field.ordinal].isBlank()) {
            return reject(RejectionReason.EmptyField(field))
        }
    }

    val rawDate = fields[0]
    val rawType = fields[1]
    val topic = fields[2]
    val location = fields[3]
    val teachers = fields[4].split(',')

    if (!datePattern.matches(rawDate)) {
        return reject(RejectionReason.InvalidDate(rawDate))
    }
    val date = try {
        LocalDate.parse(rawDate)
    } catch (_: DateTimeParseException) {
        return reject(RejectionReason.InvalidDate(rawDate))
    }
    if (date.year !in 1..9999) {
        return reject(RejectionReason.InvalidDate(rawDate))
    }
    val type = SessionType.entries.firstOrNull { it.name == rawType }
    if (type == null) {
        return reject(RejectionReason.UnknownType(rawType))
    }
    if (type == SessionType.EXAM) {
        var count = 0
        for (teacher in teachers) {
            if (teacher.isNotBlank() && teacher != "-") {
                count++
            }
        }
        if (count < 2) {
            return reject(RejectionReason.TooFewTeachers(count))
        }
    }

    for (field in Field.entries) {
        if (fields[field.ordinal].any { it.isISOControl() }) {
            return reject(RejectionReason.ControlCharacter(field))
        }
    }
    for (field in Field.entries) {
        val value = fields[field.ordinal]
        if (value != value.trim()) {
            return reject(RejectionReason.SurroundingWhitespace(field))
        }
        if (field == Field.TEACHERS) {
            for (teacher in teachers) {
                if (teacher != teacher.trim()) {
                    return reject(RejectionReason.SurroundingWhitespace(field))
                }
            }
        }
    }
    if (type == SessionType.CANCELLED) {
        for (field in listOf(Field.LOCATION, Field.TEACHERS)) {
            if (fields[field.ordinal] != "-") {
                return reject(RejectionReason.InvalidCancellationField(field))
            }
        }
    } else {
        if (location == "-") {
            return reject(RejectionReason.InvalidLocation)
        }
        for (teacher in teachers) {
            if (teacher.isBlank() || teacher == "-") {
                return reject(RejectionReason.InvalidTeacher(teacher))
            }
        }
        val seen = mutableSetOf<String>()
        for (teacher in teachers) {
            if (!seen.add(teacher)) {
                return reject(RejectionReason.DuplicateTeacher(teacher))
            }
        }
    }

    val session = when (type) {
        SessionType.LECTURE -> Lecture(date, topic, location, teachers)
        SessionType.PRACTICE -> Practice(date, topic, location, teachers)
        SessionType.EXAM -> Exam(date, topic, location, teachers)
        SessionType.CANCELLED -> Cancelled(date, topic)
    }
    return ParseResult.Success(session)
}
