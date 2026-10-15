package schedule

fun formatRejection(rejected: ParseResult.Rejected): String {
    val message = when (val reason = rejected.reason) {
        RejectionReason.InvalidSeparator -> "invalid field separator"
        is RejectionReason.FieldCount -> "got ${reason.actual} fields, expected 5"
        is RejectionReason.EmptyField -> "empty required field ${quoted(reason.field.label)}"
        is RejectionReason.InvalidDate -> "invalid date ${quoted(reason.value)}"
        is RejectionReason.UnknownType -> "unknown session type ${quoted(reason.value)}"
        is RejectionReason.TooFewTeachers -> "type \"EXAM\" requires at least 2 teachers, got ${reason.actual}"
        is RejectionReason.ControlCharacter -> "control character in field ${quoted(reason.field.label)}"
        is RejectionReason.SurroundingWhitespace -> "surrounding whitespace in field ${quoted(reason.field.label)}"
        is RejectionReason.InvalidCancellationField -> "type \"CANCELLED\" requires '-' in field ${quoted(reason.field.label)}"
        RejectionReason.InvalidLocation -> "active session requires a location other than '-'"
        is RejectionReason.InvalidTeacher -> "invalid teacher name ${quoted(reason.value)}"
        is RejectionReason.DuplicateTeacher -> "duplicate teacher name ${quoted(reason.value)}"
    }
    return "line ${rejected.lineNumber}: $message"
}

private fun quoted(value: String): String {
    val result = StringBuilder()
    result.append('"')
    for (character in value) {
        when {
            character == '\\' -> result.append("\\\\")
            character == '"' -> result.append("\\\"")
            character.code < 32 || character.code > 126 -> {
                val hex = character.code.toString(16).uppercase().padStart(4, '0')
                result.append("\\u")
                result.append(hex)
            }
            else -> result.append(character)
        }
    }
    result.append('"')
    return result.toString()
}

fun sessionType(session: Session): SessionType = when (session) {
    is Lecture -> SessionType.LECTURE
    is Practice -> SessionType.PRACTICE
    is Exam -> SessionType.EXAM
    is Cancelled -> SessionType.CANCELLED
}

fun formatSession(session: Session, includeTeachers: Boolean): String {
    val location = when (session) {
        is Lecture -> session.location
        is Practice -> session.location
        is Exam -> session.location
        is Cancelled -> "-"
    }
    val base = "${session.date} | ${sessionType(session)} | ${session.topic} | $location"
    if (!includeTeachers) return base
    val teachers = when (session) {
        is Lecture -> session.teachers.joinToString(", ")
        is Practice -> session.teachers.joinToString(", ")
        is Exam -> session.teachers.joinToString(", ")
        is Cancelled -> "-"
    }
    return "$base | $teachers"
}
