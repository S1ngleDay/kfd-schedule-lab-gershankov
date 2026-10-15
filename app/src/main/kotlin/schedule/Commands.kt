package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

fun runPeriod(command: Command.Period): Int {
    var selected = 0L
    var hasErrors = false

    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseSessionLine(number, line)) {
            is ParseResult.Success -> {
                val session = result.session
                if (session.date >= command.from && session.date <= command.to) {
                    println(formatSession(session, includeTeachers = false))
                    selected++
                }
            }
            is ParseResult.Rejected -> hasErrors = true
        }
    }

    println("Total: $selected")
    return if (hasErrors) 1 else 0
}

fun runType(command: Command.ByType): Int {
    var selected = 0L
    var hasErrors = false

    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseSessionLine(number, line)) {
            is ParseResult.Success -> {
                val session = result.session
                if (sessionType(session) == command.type) {
                    println(formatSession(session, includeTeachers = true))
                    selected++
                }
            }
            is ParseResult.Rejected -> hasErrors = true
        }
    }

    println("Total: $selected")
    return if (hasErrors) 1 else 0
}

fun runErrors(command: Command.Errors): Int {
    var total = 0L
    var rejected = 0L
    forEachScheduleLine(command.file) { number, line ->
        total++
        when (val result = parseSessionLine(number, line)) {
            is ParseResult.Success -> Unit
            is ParseResult.Rejected -> {
                rejected++
                println(formatRejection(result))
            }
        }
    }
    println("Rejected: $rejected of $total")
    return if (rejected == 0L) 0 else 1
}
