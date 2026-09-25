package com.maslarski.sudoku.domain.model

data class Score(
    val uid: String,
    val displayName: String,
    val gridSize: GridSize,
    val difficulty: Difficulty,
    val timeMillis: Long,
    val moves: Int,
    val mistakes: Int,
    val points: Int,
    val completedAtEpochMillis: Long,
) {
    val category: LeaderboardCategory get() = LeaderboardCategory(gridSize, difficulty)

    /** Ranking: highest points, then fastest time, then fewest moves. Mirrors `firestore.rules`. */
    fun isBetterThan(other: Score?): Boolean = other == null || compareTo(other) < 0

    fun compareTo(other: Score): Int = compareValuesBy(
        this, other,
        { -it.points }, { it.timeMillis }, { it.moves },
    )
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
