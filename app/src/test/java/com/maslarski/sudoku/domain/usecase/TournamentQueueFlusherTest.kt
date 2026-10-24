package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeoutException

class TournamentQueueFlusherTest {
    private val payload = """{"kind":"CLASSIC","givens":[0,1]}"""

    @Test
    fun `missing authentication queues the full payload`() = runTest {
        val queue = FakePendingQueue()
        var called = false
        val result = submitCompletionWithQueue(queue, payload, null) {
            called = true
        }

        assertTrue(result.isSuccess)
        assertFalse(called)
        assertEquals(listOf(payload), queue.pending)
    }

    @Test
    fun `retryable callable codes keep payload queued`() = runTest {
        val retryable = listOf("UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL", "UNKNOWN")
        retryable.forEach { code ->
            assertTrue(code, isRetryableCompletionFailure(CallableCompletionFailure(code)))
        }
    }

    @Test
    fun `permanent callable codes are dropped`() = runTest {
        val permanent = listOf(
            "INVALID_ARGUMENT",
            "FAILED_PRECONDITION",
            "ALREADY_EXISTS",
            "RESOURCE_EXHAUSTED",
            "PERMISSION_DENIED",
        )
        permanent.forEach { code ->
            assertFalse(code, isRetryableCompletionFailure(CallableCompletionFailure(code)))
        }
    }

    @Test
    fun `timeout is retried and remains queued`() = runTest {
        val queue = FakePendingQueue()
        val result = submitCompletionWithQueue(queue, payload, "player") {
            withTimeout(1) { delay(2) }
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf(payload), queue.pending)
        assertTrue(isRetryableCompletionFailure(TimeoutException()))
    }

    @Test
    fun `flush removes success and permanent failures but retains transient failures`() = runTest {
        val queue = FakePendingQueue(mutableListOf("success", "retry", "drop"))
        val result = flushCompletionQueue(queue, "player") { item, _ ->
            when (item) {
                "retry" -> throw TimeoutException()
                "drop" -> throw CallableCompletionFailure("FAILED_PRECONDITION")
            }
        }

        assertEquals(QueueFlushResult.RETRY, result)
        assertEquals(listOf("retry"), queue.pending)
    }

    @Test
    fun `flush without authentication leaves payloads queued`() = runTest {
        val queue = FakePendingQueue(mutableListOf(payload))
        val result = flushCompletionQueue(queue, null) { _, _ -> error("must not be called") }

        assertEquals(QueueFlushResult.RETRY, result)
        assertEquals(listOf(payload), queue.pending)
    }
}

private class FakePendingQueue(
    val pending: MutableList<String> = mutableListOf(),
) : TournamentPendingQueue {
    override suspend fun entries(): List<String> = pending.toList()
    override suspend fun add(payload: String) {
        if (payload !in pending) pending += payload
    }
    override suspend fun remove(payload: String) {
        pending.remove(payload)
    }
}
