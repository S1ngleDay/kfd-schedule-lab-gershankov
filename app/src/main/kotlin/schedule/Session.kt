package schedule

import java.time.LocalDate
import java.util.Collections

sealed interface Session {
    val date: LocalDate
    val topic: String
}

class Lecture(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = immutableSnapshot(teachers)

    init {
        validateSession(date, topic)
        validateActiveSession(location, this.teachers, 1)
    }
}

class Practice(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = immutableSnapshot(teachers)

    init {
        validateSession(date, topic)
        validateActiveSession(location, this.teachers, 1)
    }
}

class Exam(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = immutableSnapshot(teachers)

    init {
        validateSession(date, topic)
        validateActiveSession(location, this.teachers, 2)
    }
}

class Cancelled(
    override val date: LocalDate,
    override val topic: String,
) : Session {
    init {
        validateSession(date, topic)
    }
}

private fun validateSession(date: LocalDate, topic: String) {
    require(date.year in 1..9999) { "Year must be in 1..9999" }
    validateText(topic, "Topic")
}

private fun validateActiveSession(location: String, teachers: List<String>, minimumTeachers: Int) {
    validateText(location, "Location")
    require(location != "-") { "Location must not be '-'" }
    require(teachers.size >= minimumTeachers) { "At least $minimumTeachers teachers required" }
    for (teacher in teachers) {
        validateText(teacher, "Teacher name")
        require(teacher != "-") { "Teacher name must not be '-'" }
    }
    require(teachers.distinct().size == teachers.size) { "Teacher names must be distinct" }
}

private fun validateText(value: String, field: String) {
    require(value.isNotBlank()) { "$field must not be blank" }
    require(value == value.trim()) { "$field must not have surrounding whitespace" }
    require(value.none { it.isISOControl() }) { "$field must not contain control characters" }
}

internal fun <T> immutableSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
