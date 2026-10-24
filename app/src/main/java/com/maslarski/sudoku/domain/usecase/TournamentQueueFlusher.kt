package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeoutException

enum class QueueFlushResult {
    FLUSHED,
    RETRY,
}

class CallableCompletionFailure(val code: String, cause: Throwable? = null) :
    Exception("Completion callable failed with $code.", cause)

fun isRetryableCompletionFailure(error: Throwable): Boolean =
    error is TimeoutCancellationException ||
        error is TimeoutException ||
        (error is CallableCompletionFailure && error.code in RETRYABLE_CODES)

suspend fun submitCompletionWithQueue(
    queue: TournamentPendingQueue,
    payload: String,
    uid: String?,
    submit: suspend (String) -> Unit,
): Result<Unit> {
    if (uid == null) return runCatching { queue.add(payload) }
    return try {
        submit(payload)
        Result.success(Unit)
    } catch (error: TimeoutCancellationException) {
        runCatching { queue.add(payload) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (isRetryableCompletionFailure(error)) runCatching { queue.add(payload) }
        else Result.failure(error)
    }
}

suspend fun flushCompletionQueue(
    queue: TournamentPendingQueue,
    uid: String?,
    submit: suspend (String, String) -> Unit,
): QueueFlushResult {
    val payloads = queue.entries()
    if (uid == null) return QueueFlushResult.RETRY
    var shouldRetry = false
    for (payload in payloads) {
        try {
            submit(payload, uid)
            withContext(NonCancellable) { queue.remove(payload) }
        } catch (error: TimeoutCancellationException) {
            shouldRetry = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isRetryableCompletionFailure(error)) {
                shouldRetry = true
            } else {
                withContext(NonCancellable) { queue.remove(payload) }
            }
        }
    }
    return if (shouldRetry) QueueFlushResult.RETRY else QueueFlushResult.FLUSHED
}

private val RETRYABLE_CODES = setOf(
    "UNAVAILABLE",
    "DEADLINE_EXCEEDED",
    "INTERNAL",
    "UNKNOWN",
)
