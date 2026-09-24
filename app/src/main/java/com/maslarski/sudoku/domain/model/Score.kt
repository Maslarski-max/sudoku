package com.maslarski.sudoku.domain.model

data class Score(
    val uid: String,
    val displayName: String,
    val gridSize: GridSize,
    val difficulty: Difficulty,
    val timeMillis: Long,
    val moves: Int,
    val mistakes: Int,
    val completedAtEpochMillis: Long,
) {
    val category: LeaderboardCategory get() = LeaderboardCategory(gridSize, difficulty)

    /** Lower is better: primary key completion time, secondary key move count. */
    fun isBetterThan(other: Score?): Boolean =
        other == null || timeMillis < other.timeMillis || (timeMillis == other.timeMillis && moves < other.moves)
}

data class LeaderboardCategory(val gridSize: GridSize, val difficulty: Difficulty) {
    /** Firestore document id, e.g. `12_MEDIUM`. Must match `firestore.rules`. */
    val id: String get() = "${gridSize.size}_${difficulty.name}"

    companion object {
        val all: List<LeaderboardCategory> =
            GridSize.entries.flatMap { g -> Difficulty.entries.map { d -> LeaderboardCategory(g, d) } }
    }
}

data class LeaderboardEntry(val rank: Int, val score: Score, val isCurrentUser: Boolean)
