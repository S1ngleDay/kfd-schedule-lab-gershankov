package schedule

import java.time.LocalDate

sealed interface RescheduleResult {
    data class Success(val session: Session) : RescheduleResult
    data object CancelledSession : RescheduleResult
    data class InvalidDate(val date: LocalDate) : RescheduleResult
}

fun Session.reschedule(newDate: LocalDate): RescheduleResult {
    if (this is Cancelled) {
        return RescheduleResult.CancelledSession
    }
    if (newDate.year !in 1..9999) {
        return RescheduleResult.InvalidDate(newDate)
    }

    val moved = when (this) {
        is Lecture -> Lecture(newDate, topic, location, teachers)
        is Practice -> Practice(newDate, topic, location, teachers)
        is Exam -> Exam(newDate, topic, location, teachers)
        is Cancelled -> return RescheduleResult.CancelledSession
    }
    return RescheduleResult.Success(moved)
}
