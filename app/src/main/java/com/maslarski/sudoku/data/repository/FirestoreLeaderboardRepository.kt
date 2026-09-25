package com.maslarski.sudoku.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.maslarski.sudoku.data.local.LeaderboardCacheEntity
import com.maslarski.sudoku.data.local.LeaderboardDao
import com.maslarski.sudoku.data.local.PendingScoreEntity
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.LeaderboardEntry
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore layout: `leaderboards/{gridSize}_{DIFFICULTY}/scores/{uid}` — one best score per player
 * per category, ranked by `points` (desc) then `timeMillis` then `moves`. Results are mirrored into Room so the UI has
 * instant, offline-capable data; scores that fail to upload are queued and retried on the next refresh.
 */
@Singleton
class FirestoreLeaderboardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val dao: LeaderboardDao,
) : LeaderboardRepository {

    override fun observeTop(category: LeaderboardCategory, limit: Int): Flow<List<LeaderboardEntry>> =
        dao.observeTop(category.id, limit).map { rows ->
            val uid = authRepository.currentUserId
            rows.mapIndexed { i, row -> LeaderboardEntry(rank = i + 1, score = row.toScore(), isCurrentUser = row.uid == uid) }
        }

    override suspend fun refresh(category: LeaderboardCategory, limit: Int): Result<Unit> = runCatching {
        authRepository.ensureSignedIn()
        flushPending()
        val snapshot = scores(category)
            .orderBy(FIELD_POINTS, Query.Direction.DESCENDING)
            .orderBy(FIELD_TIME, Query.Direction.ASCENDING)
            .orderBy(FIELD_MOVES, Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .await()
        val rows = snapshot.documents.mapNotNull { doc ->
            val uid = doc.getString(FIELD_UID) ?: return@mapNotNull null
            LeaderboardCacheEntity(
                category = category.id,
                uid = uid,
                displayName = doc.getString(FIELD_NAME) ?: "",
                gridSize = doc.getLong(FIELD_GRID)?.toInt() ?: category.gridSize.size,
                difficulty = doc.getString(FIELD_DIFFICULTY) ?: category.difficulty.name,
                timeMillis = doc.getLong(FIELD_TIME) ?: return@mapNotNull null,
                moves = doc.getLong(FIELD_MOVES)?.toInt() ?: 0,
                mistakes = doc.getLong(FIELD_MISTAKES)?.toInt() ?: 0,
                points = doc.getLong(FIELD_POINTS)?.toInt() ?: 0,
                completedAtEpochMillis = doc.getTimestamp(FIELD_COMPLETED)?.toDate()?.time ?: 0L,
            )
        }
        dao.replace(category.id, rows)
    }

    override suspend fun personalBest(category: LeaderboardCategory): Score? {
        val uid = authRepository.currentUserId ?: return null
        dao.get(category.id, uid)?.let { return it.toScore() }
        return runCatching {
            val doc = scores(category).document(uid).get().await()
            if (!doc.exists()) return null
            Score(
                uid = uid,
                displayName = doc.getString(FIELD_NAME) ?: "",
                gridSize = category.gridSize,
                difficulty = category.difficulty,
                timeMillis = doc.getLong(FIELD_TIME) ?: return null,
                moves = doc.getLong(FIELD_MOVES)?.toInt() ?: 0,
                mistakes = doc.getLong(FIELD_MISTAKES)?.toInt() ?: 0,
                points = doc.getLong(FIELD_POINTS)?.toInt() ?: 0,
                completedAtEpochMillis = doc.getTimestamp(FIELD_COMPLETED)?.toDate()?.time ?: 0L,
            )
        }.getOrNull()
    }

    override suspend fun submit(score: Score): Result<Boolean> = runCatching {
        val category = score.category
        val localBest = dao.get(category.id, score.uid)?.toScore()
        if (!score.isBetterThan(localBest)) return@runCatching false

        // Optimistically cache so the UI reflects the new best immediately.
        dao.upsertAll(listOf(score.toCache()))

        val docRef = scores(category).document(score.uid)
        val uploaded = runCatching {
            val remote = docRef.get().await()
            val remoteBest = remote.takeIf { it.exists() }?.let { doc ->
                score.copy(
                    points = doc.getLong(FIELD_POINTS)?.toInt() ?: 0,
                    timeMillis = doc.getLong(FIELD_TIME) ?: Long.MAX_VALUE,
                    moves = doc.getLong(FIELD_MOVES)?.toInt() ?: Int.MAX_VALUE,
                    mistakes = doc.getLong(FIELD_MISTAKES)?.toInt() ?: 0,
                    completedAtEpochMillis = doc.getTimestamp(FIELD_COMPLETED)?.toDate()?.time ?: 0L,
                )
            }
            if (!score.isBetterThan(remoteBest)) {
                // Remote already holds a better result (e.g. from another device); adopt it locally.
                remoteBest?.let { dao.upsertAll(listOf(it.toCache())) }
                return@runCatching false
            }
            docRef.set(score.toDocument()).await()
            true
        }.getOrElse { error ->
            // Rules rejected it (not an improvement server-side): nothing to retry. Otherwise queue for later.
            if (error.isPermissionDenied()) return@runCatching false
            dao.upsertPending(score.toPending())
            throw error
        }
        if (uploaded) dao.deletePending(category.id)
        uploaded
    }

    private suspend fun flushPending() {
        for (pending in dao.pending()) {
            val score = pending.toScore()
            val docRef = scores(score.category).document(score.uid)
            runCatching { docRef.set(score.toDocument()).await() }
                .onSuccess { dao.deletePending(pending.category) }
                .onFailure { if (it.isPermissionDenied()) dao.deletePending(pending.category) }
        }
    }

    private fun Throwable.isPermissionDenied(): Boolean =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private fun scores(category: LeaderboardCategory) =
        firestore.collection(COLLECTION_LEADERBOARDS).document(category.id).collection(COLLECTION_SCORES)

    private fun Score.toDocument(): Map<String, Any> = mapOf(
        FIELD_UID to uid,
        FIELD_NAME to displayName,
        FIELD_GRID to gridSize.size,
        FIELD_DIFFICULTY to difficulty.name,
        FIELD_TIME to timeMillis,
        FIELD_MOVES to moves,
        FIELD_MISTAKES to mistakes,
        FIELD_POINTS to points,
        FIELD_COMPLETED to FieldValue.serverTimestamp(),
    )

    private fun Score.toCache() = LeaderboardCacheEntity(
        category = category.id, uid = uid, displayName = displayName, gridSize = gridSize.size,
        difficulty = difficulty.name, timeMillis = timeMillis, moves = moves, mistakes = mistakes, points = points,
        completedAtEpochMillis = completedAtEpochMillis,
    )

    private fun Score.toPending() = PendingScoreEntity(
        category = category.id, uid = uid, displayName = displayName, gridSize = gridSize.size,
        difficulty = difficulty.name, timeMillis = timeMillis, moves = moves, mistakes = mistakes, points = points,
        completedAtEpochMillis = completedAtEpochMillis,
    )

    private fun LeaderboardCacheEntity.toScore() = Score(
        uid = uid, displayName = displayName, gridSize = GridSize.fromSize(gridSize),
        difficulty = Difficulty.valueOf(difficulty), timeMillis = timeMillis, moves = moves,
        mistakes = mistakes, points = points, completedAtEpochMillis = completedAtEpochMillis,
    )

    private fun PendingScoreEntity.toScore() = Score(
        uid = uid, displayName = displayName, gridSize = GridSize.fromSize(gridSize),
        difficulty = Difficulty.valueOf(difficulty), timeMillis = timeMillis, moves = moves,
        mistakes = mistakes, points = points, completedAtEpochMillis = completedAtEpochMillis,
    )

    private companion object {
        const val COLLECTION_LEADERBOARDS = "leaderboards"
        const val COLLECTION_SCORES = "scores"
        const val FIELD_UID = "uid"
        const val FIELD_NAME = "displayName"
        const val FIELD_GRID = "gridSize"
        const val FIELD_DIFFICULTY = "difficulty"
        const val FIELD_TIME = "timeMillis"
        const val FIELD_MOVES = "moves"
        const val FIELD_MISTAKES = "mistakes"
        const val FIELD_POINTS = "points"
        const val FIELD_COMPLETED = "completedAt"
    }
}
