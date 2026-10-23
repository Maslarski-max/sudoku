package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.model.Trophy
import com.maslarski.sudoku.domain.model.TrophyCategory
import com.maslarski.sudoku.domain.model.TrophyTier
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.domain.repository.DailyChallengeRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.repository.TrophyRepository
import com.maslarski.sudoku.domain.time.EpochClock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTrophyRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val tournamentRepository: TournamentRepository,
    private val livesRepository: LivesRepository,
    private val clock: EpochClock,
) : TrophyRepository {

    private val earnedData = dataStore.data.map { preferences ->
        EarnedData(
            milestones = preferences[MILESTONES].orEmpty(),
            tournamentAwards = preferences[TOURNAMENT_AWARDS].orEmpty(),
        )
    }

    private val currentMonth = flow {
        while (true) {
            emit(YearMonth.from(DailyChallenge.dateAt(clock.now())))
            delay(60_000L)
        }
    }

    override val trophies: Flow<List<Trophy>> = combine(
        dailyChallengeRepository.stampedDates,
        earnedData,
        livesRepository.lives,
        currentMonth,
    ) { stamps, earned, lives, month ->
        computeTrophies(stamps, earned, lives.unlimited, month)
    }

    override suspend fun recordCompletion(state: GameState) {
        state.dailyDate?.let { dailyChallengeRepository.recordStamp(it) }
        val earned = buildSet {
            if (state.gridSize == GridSize.EIGHTEEN && state.difficulty == Difficulty.HARD) add(MILESTONE_18_HARD)
            if (state.variant == Variant.FLOWER && state.difficulty == Difficulty.HARD) add(MILESTONE_FLOWER_HARD)
            if (state.variant == Variant.SAMURAI) add(MILESTONE_SAMURAI)
            if (state.variant == Variant.OUTSIDE) add(MILESTONE_OUTSIDE)
            if (state.iconMode) add(MILESTONE_HOLIDAY)
        }
        if (earned.isNotEmpty()) {
            dataStore.edit { preferences ->
                preferences[MILESTONES] = preferences[MILESTONES].orEmpty() + earned
            }
        }
    }

    override suspend fun refreshPeriodAwards() {
        val nowMonth = YearMonth.from(DailyChallenge.dateAt(clock.now()))
        val prior = dataStore.data.first()
        val checkedMonth = prior[CHECKED_ARENA_MONTH]
        if (checkedMonth != nowMonth.toString()) {
            val period = nowMonth.minusMonths(1).toString()
            val result = tournamentRepository.load(TournamentBoard.ARENA, period)
            if (result.isSuccess) {
                result.getOrNull()?.let { persistPlacement(TournamentBoard.ARENA, it) }
                dataStore.edit { it[CHECKED_ARENA_MONTH] = nowMonth.toString() }
            }
        }
        if (nowMonth.monthValue == 1) {
            val year = nowMonth.year.toString()
            val checkedYear = dataStore.data.first()[CHECKED_CHAMPIONSHIP_YEAR]
            if (checkedYear != year) {
                val period = (nowMonth.year - 1).toString()
                val result = tournamentRepository.load(TournamentBoard.CHAMPIONSHIP, period)
                if (result.isSuccess) {
                    result.getOrNull()?.let { persistPlacement(TournamentBoard.CHAMPIONSHIP, it) }
                    dataStore.edit { it[CHECKED_CHAMPIONSHIP_YEAR] = year }
                }
            }
        }
    }

    private suspend fun persistPlacement(board: TournamentBoard, snapshot: TournamentSnapshot) {
        val player = snapshot.playerEntry ?: snapshot.entries.firstOrNull { it.isCurrentUser }
        val rank = player?.rank ?: return
        if (rank !in 1..3) return
        val encoded = "${board.name}|${snapshot.period}|$rank"
        dataStore.edit { preferences ->
            preferences[TOURNAMENT_AWARDS] = preferences[TOURNAMENT_AWARDS].orEmpty() + encoded
        }
    }

    private fun computeTrophies(
        stamps: Set<String>,
        earned: EarnedData,
        unlimited: Boolean,
        currentMonth: YearMonth,
    ): List<Trophy> = buildList {
        add(
            Trophy(
                id = "daily_first_stamp",
                title = "First Stamp",
                description = "Complete a Daily Challenge.",
                category = TrophyCategory.DAILY,
                tier = TrophyTier.BRONZE,
                unlocked = stamps.isNotEmpty(),
            ),
        )
        add(
            Trophy(
                id = "daily_week_streak",
                title = "Week Streak",
                description = "Complete Daily Challenges on seven consecutive UTC days.",
                category = TrophyCategory.DAILY,
                tier = TrophyTier.SILVER,
                unlocked = DailyChallenge.hasWeekStreak(stamps),
            ),
        )
        val months = (stamps.mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() } + currentMonth)
            .toSortedSet()
        months.forEach { month ->
            val monthName = month.atDay(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val complete = DailyChallenge.isMonthComplete(month, stamps)
            add(
                Trophy(
                    id = "daily_master_$month",
                    title = "$monthName ${month.year} Master",
                    description = "Stamp every day in $monthName ${month.year}.",
                    category = TrophyCategory.DAILY,
                    tier = TrophyTier.GOLD,
                    unlocked = complete,
                ),
            )
        }
        earned.tournamentAwards.mapNotNull(::decodeAward).forEach { (board, period, rank) ->
            val month = runCatching { YearMonth.parse(period) }.getOrNull()
            val title = when (board) {
                TournamentBoard.ARENA -> {
                    val placement = when (rank) {
                        1 -> "Gold"
                        2 -> "Silver"
                        else -> "Bronze"
                    }
                    "Monthly $placement — ${month?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) ?: period}"
                }
                TournamentBoard.CHAMPIONSHIP -> if (rank == 1) "$period Champion" else "$period Podium"
            }
            add(
                Trophy(
                    id = "tournament_${board.name.lowercase()}_${period}_$rank",
                    title = title,
                    description = "Place ${rankOrdinal(rank)} in the ${board.name.lowercase().replaceFirstChar(Char::uppercase)}.",
                    category = TrophyCategory.TOURNAMENT,
                    tier = when (rank) {
                        1 -> TrophyTier.GOLD
                        2 -> TrophyTier.SILVER
                        else -> TrophyTier.BRONZE
                    },
                    unlocked = true,
                ),
            )
        }
        addMilestone(MILESTONE_18_HARD, "18×18 Hard", "Complete a Hard 18×18 puzzle.", earned)
        addMilestone(MILESTONE_FLOWER_HARD, "Flower Hard", "Complete a Hard Flower puzzle.", earned)
        addMilestone(MILESTONE_SAMURAI, "Samurai finisher", "Complete a Samurai puzzle.", earned)
        addMilestone(MILESTONE_OUTSIDE, "Outside finisher", "Complete an Outside puzzle.", earned)
        addMilestone(MILESTONE_HOLIDAY, "Holiday icons finisher", "Complete a puzzle in Holiday icon mode.", earned)
        add(
            Trophy(
                id = "premium_unlimited_patron",
                title = "Unlimited Patron",
                description = "Unlock Unlimited Lives.",
                category = TrophyCategory.PREMIUM,
                tier = TrophyTier.GOLD,
                unlocked = unlimited,
                premiumOnly = true,
            ),
        )
    }

    private fun MutableList<Trophy>.addMilestone(id: String, title: String, description: String, data: EarnedData) {
        add(
            Trophy(
                id = id,
                title = title,
                description = description,
                category = TrophyCategory.MILESTONE,
                tier = TrophyTier.ACCENT,
                unlocked = id in data.milestones,
            ),
        )
    }

    private fun decodeAward(value: String): Triple<TournamentBoard, String, Int>? {
        val parts = value.split('|')
        if (parts.size != 3) return null
        return runCatching { Triple(TournamentBoard.valueOf(parts[0]), parts[1], parts[2].toInt()) }.getOrNull()
    }

    private fun rankOrdinal(rank: Int): String = when (rank) {
        1 -> "first"
        2 -> "second"
        else -> "third"
    }

    private data class EarnedData(
        val milestones: Set<String>,
        val tournamentAwards: Set<String>,
    )

    private companion object {
        const val MILESTONE_18_HARD = "milestone_18_hard"
        const val MILESTONE_FLOWER_HARD = "milestone_flower_hard"
        const val MILESTONE_SAMURAI = "milestone_samurai"
        const val MILESTONE_OUTSIDE = "milestone_outside"
        const val MILESTONE_HOLIDAY = "milestone_holiday"
        val MILESTONES = stringSetPreferencesKey("trophy_milestones")
        val TOURNAMENT_AWARDS = stringSetPreferencesKey("trophy_tournament_awards")
        val CHECKED_ARENA_MONTH = stringPreferencesKey("trophy_checked_arena_month")
        val CHECKED_CHAMPIONSHIP_YEAR = stringPreferencesKey("trophy_checked_championship_year")
    }
}
