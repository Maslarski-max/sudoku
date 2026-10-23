package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.model.PendingTournamentScore
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

enum class PendingUploadResult {
    UPLOADED,
    DROP,
}

enum class QueueFlushResult {
    FLUSHED,
    RETRY,
}

suspend fun recordTournamentScore(
    queue: TournamentPendingQueue,
    entry: PendingTournamentScore,
    uid: String?,
    write: suspend (PendingTournamentScore, String) -> Unit,
): Result<Unit> {
    if (uid == null) return runCatching { queue.add(entry) }
    val result = runCatching { write(entry, uid) }
    return if (result.exceptionOrNull() is TimeoutCancellationException) Result.success(Unit) else result
}

suspend fun flushTournamentQueue(
    queue: TournamentPendingQueue,
    nowEpochMillis: Long,
    uid: String?,
    upload: suspend (PendingTournamentScore, String, suspend () -> Unit) -> PendingUploadResult,
): QueueFlushResult {
    val entries = queue.entries()
    val active = entries.filter { entry ->
        val current = entry.board.currentPeriod(nowEpochMillis) == entry.period
        if (!current) queue.remove(entry)
        current
    }
    if (uid == null) return QueueFlushResult.RETRY
    active.forEach { entry ->
        var removed = false
        val removeEntry: suspend () -> Unit = {
            withContext(NonCancellable) { queue.remove(entry) }
            removed = true
        }
        try {
            upload(entry, uid, removeEntry)
        } finally {
            if (!removed) removeEntry()
        }
    }
    return QueueFlushResult.FLUSHED
}
