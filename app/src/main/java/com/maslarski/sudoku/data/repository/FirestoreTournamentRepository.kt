package com.maslarski.sudoku.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.maslarski.sudoku.domain.model.PendingTournamentScore
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentEntry
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.time.EpochClock
import com.maslarski.sudoku.domain.usecase.PendingUploadResult
import com.maslarski.sudoku.domain.usecase.flushTournamentQueue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTournamentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val pendingQueue: TournamentPendingQueue,
    private val clock: EpochClock,
) : TournamentRepository {

    override suspend fun record(
        board: TournamentBoard,
        completedAtEpochMillis: Long,
        points: Int,
    ): Result<Unit> = runCatching {
        val period = board.periodAt(completedAtEpochMillis)
        val entry = PendingTournamentScore(board, period, points)
        if (board.currentPeriod(clock.now()) != period) return@runCatching
        val uid = runCatching {
            authRepository.currentUserId ?: authRepository.ensureSignedIn()
        }.getOrNull()
        if (uid == null) {
            pendingQueue.add(entry)
            return@runCatching
        }
        val upload = runCatching { write(entry, uid) }
        val error = upload.exceptionOrNull() ?: return@runCatching
        if (error.isPermissionDenied()) return@runCatching
        if (board.currentPeriod(clock.now()) == period) pendingQueue.add(entry)
        throw error
    }

    override suspend fun flushPending() {
        withTimeoutOrNull(NETWORK_TIMEOUT_MILLIS) {
            val entries = pendingQueue.entries()
            if (entries.isEmpty()) return@withTimeoutOrNull
            val uid = runCatching {
                authRepository.currentUserId ?: authRepository.ensureSignedIn()
            }.getOrNull()
            flushTournamentQueue(pendingQueue, clock.now(), uid) { entry, signedInUid ->
                runCatching { write(entry, signedInUid) }
                    .fold(
                        onSuccess = { PendingUploadResult.UPLOADED },
                        onFailure = { error ->
                            if (error.isPermissionDenied()) PendingUploadResult.DROP else PendingUploadResult.RETRY
                        },
                    )
            }
        }
    }

    override suspend fun load(
        board: TournamentBoard,
        period: String,
        limit: Int,
    ): Result<TournamentSnapshot> = runCatching {
        require(period.matches(if (board == TournamentBoard.ARENA) ARENA_PERIOD else CHAMPIONSHIP_PERIOD))
        val uid = authRepository.currentUserId ?: authRepository.ensureSignedIn()
        val scores = scores(board, period)
        val query = scores.orderBy(FIELD_POINTS, Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1, MAX_LIMIT).toLong())
            .get()
            .awaitNetwork()
        val entries = query.documents.mapIndexedNotNull { index, document ->
            val docUid = document.getString(FIELD_UID) ?: return@mapIndexedNotNull null
            TournamentEntry(
                rank = index + 1,
                uid = docUid,
                displayName = document.getString(FIELD_DISPLAY_NAME).orEmpty(),
                points = document.getLong(FIELD_POINTS)?.toInt() ?: return@mapIndexedNotNull null,
                games = document.getLong(FIELD_GAMES)?.toInt() ?: 0,
                isCurrentUser = docUid == uid,
            )
        }
        val listedPlayer = entries.firstOrNull { it.uid == uid }
        val player = listedPlayer ?: scores.document(uid).get().awaitNetwork().takeIf { it.exists() }?.let { document ->
            TournamentEntry(
                rank = null,
                uid = uid,
                displayName = document.getString(FIELD_DISPLAY_NAME).orEmpty(),
                points = document.getLong(FIELD_POINTS)?.toInt() ?: 0,
                games = document.getLong(FIELD_GAMES)?.toInt() ?: 0,
                isCurrentUser = true,
            )
        }
        TournamentSnapshot(board, period, entries, player)
    }

    private suspend fun write(entry: PendingTournamentScore, uid: String) {
        scores(entry.board, entry.period).document(uid).set(
            mapOf(
                FIELD_UID to uid,
                FIELD_DISPLAY_NAME to Score.displayNameFor(uid),
                FIELD_POINTS to FieldValue.increment(entry.points.toLong()),
                FIELD_GAMES to FieldValue.increment(1),
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).awaitNetwork()
    }

    private fun scores(board: TournamentBoard, period: String) =
        firestore.collection(board.collection).document(period).collection(COLLECTION_SCORES)

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitNetwork(): T =
        withTimeout(NETWORK_TIMEOUT_MILLIS) { await() }

    private fun Throwable.isPermissionDenied(): Boolean =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 15_000L
        const val MAX_LIMIT = 50
        const val COLLECTION_SCORES = "scores"
        const val FIELD_UID = "uid"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_POINTS = "points"
        const val FIELD_GAMES = "games"
        const val FIELD_UPDATED_AT = "updatedAt"
        val ARENA_PERIOD = Regex("^\\d{4}-\\d{2}$")
        val CHAMPIONSHIP_PERIOD = Regex("^\\d{4}$")
    }
}
