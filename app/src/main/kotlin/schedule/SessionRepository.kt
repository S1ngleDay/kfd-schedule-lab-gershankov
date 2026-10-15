package schedule

interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

class InMemorySessionRepository : SessionRepository {
    private val sessions = mutableListOf<Session>()

    override fun add(session: Session) {
        sessions.add(session)
    }

    override fun addAll(sessions: List<Session>) {
        this.sessions.addAll(sessions)
    }

    override fun all(): List<Session> = immutableSnapshot(sessions)
}

data class AuditEvent(val operation: String, val count: Int)

class AuditedSessionRepository(
    private val delegate: SessionRepository,
) : SessionRepository by delegate {
    private val events = mutableListOf<AuditEvent>()

    val auditLog: List<AuditEvent>
        get() = immutableSnapshot(events)

    var addedCount: Long = 0
        private set

    override fun add(session: Session) {
        delegate.add(session)
        record("add", 1)
    }

    override fun addAll(sessions: List<Session>) {
        val count = sessions.size
        delegate.addAll(sessions)
        record("addAll", count)
    }

    private fun record(operation: String, count: Int) {
        events.add(AuditEvent(operation, count))
        addedCount += count
    }
}
