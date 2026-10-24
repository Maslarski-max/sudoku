package com.maslarski.sudoku.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctionsException
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.TournamentCompletionPayload
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentEntry
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.model.toTournamentCompletionPayload
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.usecase.flushCompletionQueue
import com.maslarski.sudoku.domain.usecase.CallableCompletionFailure
import com.maslarski.sudoku.domain.usecase.submitCompletionWithQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTournamentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val pendingQueue: TournamentPendingQueue,
) : TournamentRepository {

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    override suspend fun submitCompletion(state: GameState): Result<Unit> {
        val payload = json.encodeToString(state.toTournamentCompletionPayload())
        val uid = signedInUidOrNull()
        return submitCompletionWithQueue(pendingQueue, payload, uid) { encoded ->
            submitCallable(json.decodeFromString<TournamentCompletionPayload>(encoded))
        }
    }

    override suspend fun flushPending(): Int {
        if (pendingQueue.entries().isEmpty()) return 0
        val uid = withTimeoutOrNull(NETWORK_TIMEOUT_MILLIS) { signedInUidOrNull() }
        var submittedCount = 0
        flushCompletionQueue(pendingQueue, uid) { encoded, _ ->
            submitCallable(json.decodeFromString<TournamentCompletionPayload>(encoded))
            submittedCount++
        }
        return submittedCount
    }

    override suspend fun load(
        board: TournamentBoard,
        period: String,
        limit: Int,
        serverOnly: Boolean,
    ): Result<TournamentSnapshot> = runCatching {
        require(period.matches(if (board == TournamentBoard.ARENA) ARENA_PERIOD else CHAMPIONSHIP_PERIOD))
        val uid = authRepository.currentUserId ?: authRepository.ensureSignedIn()
        val scores = scores(board, period)
        val query = scores.orderBy(FIELD_POINTS, Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1, MAX_LIMIT).toLong())
        val querySnapshot = if (serverOnly) {
            query.get(Source.SERVER).awaitNetwork()
        } else {
            query.get().awaitNetwork()
        }
        val entries = querySnapshot.documents.mapIndexedNotNull { index, document ->
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
        val player = listedPlayer ?: run {
            val playerDocument = scores.document(uid)
            val document = if (serverOnly) {
                playerDocument.get(Source.SERVER).awaitNetwork()
            } else {
                playerDocument.get().awaitNetwork()
            }
            document.takeIf { it.exists() }?.let {
                TournamentEntry(
                    rank = null,
                    uid = uid,
                    displayName = it.getString(FIELD_DISPLAY_NAME).orEmpty(),
                    points = it.getLong(FIELD_POINTS)?.toInt() ?: 0,
                    games = it.getLong(FIELD_GAMES)?.toInt() ?: 0,
                    isCurrentUser = true,
                )
            }
        }
        TournamentSnapshot(board, period, entries, player)
    }

    private suspend fun signedInUidOrNull(): String? =
        try {
            authRepository.currentUserId ?: authRepository.ensureSignedIn()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

    private suspend fun submitCallable(payload: TournamentCompletionPayload) {
        try {
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                functions.getHttpsCallable(CALLABLE_NAME)
                    .call(payload.asCallableData())
                    .await()
            }
        } catch (error: FirebaseFunctionsException) {
            throw CallableCompletionFailure(error.code.name, error)
        }
    }

    private fun TournamentCompletionPayload.asCallableData(): Map<String, Any> = buildMap {
        put("kind", kind)
        variant?.let { put("variant", it) }
        put("gridSize", gridSize)
        put("difficulty", difficulty)
        dailyDate?.let { put("dailyDate", it) }
        put("givens", givens)
        put("solutionCells", solutionCells)
        put("comboCells", comboCells)
        outsideClues?.let {
            put(
                "outsideClues",
                mapOf(
                    "rowsFromLeft" to it.rowsFromLeft,
                    "rowsFromRight" to it.rowsFromRight,
                    "columnsFromTop" to it.columnsFromTop,
                    "columnsFromBottom" to it.columnsFromBottom,
                ),
            )
        }
        put("elapsedMillis", elapsedMillis)
        put("moves", moves)
        put("mistakes", mistakes)
        put("hintsUsed", hintsUsed)
    }

    private fun scores(board: TournamentBoard, period: String) =
        firestore.collection(board.collection).document(period).collection(COLLECTION_SCORES)

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitNetwork(): T =
        withTimeout(NETWORK_TIMEOUT_MILLIS) { await() }

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 15_000L
        const val MAX_LIMIT = 50
        const val CALLABLE_NAME = "submitCompletion"
        const val COLLECTION_SCORES = "scores"
        const val FIELD_UID = "uid"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_POINTS = "points"
        const val FIELD_GAMES = "games"
        val ARENA_PERIOD = Regex("^\\d{4}-\\d{2}$")
        val CHAMPIONSHIP_PERIOD = Regex("^\\d{4}$")
    }
}
