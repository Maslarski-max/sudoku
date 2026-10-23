package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.model.PendingTournamentScore
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue

enum class PendingUploadResult {
    UPLOADED,
    RETRY,
    DROP,
}

suspend fun flushTournamentQueue(
    queue: TournamentPendingQueue,
    nowEpochMillis: Long,
    uid: String?,
    upload: suspend (PendingTournamentScore, String) -> PendingUploadResult,
) {
    val entries = queue.entries()
    val active = entries.filter { entry ->
        val current = entry.board.currentPeriod(nowEpochMillis) == entry.period
        if (!current) queue.remove(entry)
        current
    }
    if (uid == null) return
    active.forEach { entry ->
        when (upload(entry, uid)) {
            PendingUploadResult.UPLOADED, PendingUploadResult.DROP -> queue.remove(entry)
            PendingUploadResult.RETRY -> Unit
        }
    }
}
