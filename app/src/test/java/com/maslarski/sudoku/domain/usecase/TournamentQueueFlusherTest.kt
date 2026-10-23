package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.model.PendingTournamentScore
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentQueueFlusherTest {

    @Test
    fun `timed out issued record is reported successful without requeueing`() = runTest {
        val entry = PendingTournamentScore(TournamentBoard.ARENA, "2025-03", 500)
        val queue = FakePendingQueue(mutableListOf())
        var writeCount = 0

        val result = recordTournamentScore(queue, entry, "player") { _, _ ->
            writeCount++
            withTimeout(1) { delay(2) }
        }

        assertTrue(result.isSuccess)
        assertEquals(1, writeCount)
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun `flush uploads current periods and drops ended periods`() = runTest {
        val now = Instant.parse("2025-03-01T00:10:00Z").toEpochMilli()
        val queue = FakePendingQueue(
            mutableListOf(
                PendingTournamentScore(TournamentBoard.ARENA, YearMonth.of(2025, 3).toString(), 500),
                PendingTournamentScore(TournamentBoard.ARENA, YearMonth.of(2025, 2).toString(), 400),
                PendingTournamentScore(TournamentBoard.CHAMPIONSHIP, "2025", 700),
                PendingTournamentScore(TournamentBoard.CHAMPIONSHIP, "2024", 300),
            ),
        )
        val uploaded = mutableListOf<PendingTournamentScore>()

        val result = flushTournamentQueue(queue, now, "player") { entry, _, _ ->
            uploaded += entry
            PendingUploadResult.UPLOADED
        }

        assertEquals(QueueFlushResult.FLUSHED, result)
        assertEquals(2, uploaded.size)
        assertEquals(setOf("ARENA:2025-03", "CHAMPIONSHIP:2025"), uploaded.map { "${it.board}:${it.period}" }.toSet())
        assertEquals(emptyList<PendingTournamentScore>(), queue.pending)
    }

    @Test
    fun `timed out issued write is handed off and is not requeued`() = runTest {
        val now = Instant.parse("2025-03-01T00:10:00Z").toEpochMilli()
        val entry = PendingTournamentScore(TournamentBoard.ARENA, YearMonth.of(2025, 3).toString(), 500)
        val queue = FakePendingQueue(mutableListOf(entry))
        var uploadCount = 0

        val result = flushTournamentQueue(queue, now, "player") { _, _, onIssued ->
            uploadCount++
            onIssued()
            assertTrue(queue.pending.isEmpty())
            PendingUploadResult.UPLOADED
        }

        assertEquals(QueueFlushResult.FLUSHED, result)
        assertEquals(1, uploadCount)
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun `missing uid leaves active entries queued for a later flush`() = runTest {
        val now = Instant.parse("2025-03-01T00:10:00Z").toEpochMilli()
        val entry = PendingTournamentScore(TournamentBoard.ARENA, YearMonth.of(2025, 3).toString(), 500)
        val queue = FakePendingQueue(mutableListOf(entry))
        var uploadCount = 0

        val result = flushTournamentQueue(queue, now, null) { _, _, _ ->
            uploadCount++
            PendingUploadResult.UPLOADED
        }

        assertEquals(QueueFlushResult.RETRY, result)
        assertEquals(0, uploadCount)
        assertEquals(listOf(entry), queue.pending)
    }
}

private class FakePendingQueue(
    val pending: MutableList<PendingTournamentScore>,
) : TournamentPendingQueue {
    override suspend fun entries() = pending.toList()
    override suspend fun add(entry: PendingTournamentScore) {
        pending += entry
    }
    override suspend fun remove(entry: PendingTournamentScore) {
        pending.remove(entry)
    }
}
