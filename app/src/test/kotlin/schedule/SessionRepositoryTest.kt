package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionRepositoryTest {
    private val first = Cancelled(LocalDate.of(2026, 10, 15), "First")
    private val second = Cancelled(LocalDate.of(2026, 10, 16), "Second")

    @Test
    fun repositoryKeepsOrderDuplicatesAndIndependentSnapshots() {
        val repository = InMemorySessionRepository()
        val emptySnapshot = repository.all()
        repository.add(first)
        val snapshot = repository.all()
        val batch = mutableListOf<Session>(second, first)
        repository.addAll(batch)
        batch.clear()
        repository.addAll(emptyList())
        assertEquals(emptyList(), emptySnapshot)
        assertEquals(listOf(first), snapshot)
        assertEquals(listOf(first, second, first), repository.all())
        val exposed = repository.all() as MutableList<Session>
        assertFailsWith<UnsupportedOperationException> { exposed.clear() }
        assertFailsWith<UnsupportedOperationException> { exposed[0] = second }
        assertEquals(listOf(first, second, first), repository.all())
    }

    @Test
    fun auditRecordsOneEventPerSuccessfulCallIncludingEmptyBatch() {
        val delegate = InMemorySessionRepository()
        val repository = AuditedSessionRepository(delegate)
        repository.add(first)
        val logSnapshot = repository.auditLog
        repository.addAll(listOf(second, first, second))
        repository.addAll(emptyList())
        assertEquals(delegate.all(), repository.all())
        assertEquals(listOf(first, second, first, second), repository.all())
        assertEquals(4L, repository.addedCount)
        assertEquals(listOf(AuditEvent("add", 1)), logSnapshot)
        assertEquals(
            listOf(AuditEvent("add", 1), AuditEvent("addAll", 3), AuditEvent("addAll", 0)),
            repository.auditLog,
        )
        assertFailsWith<UnsupportedOperationException> {
            (repository.auditLog as MutableList<AuditEvent>).clear()
        }
        assertEquals(3, repository.auditLog.size)
    }

    @Test
    fun internalAddCallsOfTheDelegateDoNotCreateExtraEvents() {
        val delegate = object : SessionRepository {
            private val storage = InMemorySessionRepository()
            override fun add(session: Session) = storage.add(session)
            override fun addAll(sessions: List<Session>) = sessions.forEach { add(it) }
            override fun all(): List<Session> = storage.all()
        }
        val repository = AuditedSessionRepository(delegate)
        repository.addAll(listOf(first, second, first))
        assertEquals(3, repository.all().size)
        assertEquals(3L, repository.addedCount)
        assertEquals(listOf(AuditEvent("addAll", 3)), repository.auditLog)
    }

    @Test
    fun failedCallsDoNotChangeAuditLogOrCounter() {
        val delegate = object : SessionRepository {
            override fun add(session: Session): Unit = error("Storage failure")
            override fun addAll(sessions: List<Session>): Unit = error("Storage failure")
            override fun all(): List<Session> = emptyList()
        }
        val repository = AuditedSessionRepository(delegate)
        assertFailsWith<IllegalStateException> { repository.add(first) }
        assertFailsWith<IllegalStateException> { repository.addAll(listOf(first, second)) }
        assertFailsWith<IllegalStateException> { repository.addAll(emptyList()) }
        assertEquals(0L, repository.addedCount)
        assertEquals(emptyList(), repository.auditLog)
    }
}
