package com.maslarski.sudoku.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentEntry
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.time.EpochClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TournamentsUiState(
    val board: TournamentBoard = TournamentBoard.ARENA,
    val loading: Boolean = true,
    val arenaError: Boolean = false,
    val championshipError: Boolean = false,
    val arena: TournamentSnapshot? = null,
    val championship: TournamentSnapshot? = null,
    val previousArena: TournamentSnapshot? = null,
    val previousChampionship: TournamentSnapshot? = null,
) {
    val error: Boolean
        get() = if (board == TournamentBoard.ARENA) arenaError else championshipError
}

@HiltViewModel
class TournamentsViewModel @Inject constructor(
    private val repository: TournamentRepository,
    private val clock: EpochClock,
) : ViewModel() {

    private val state = MutableStateFlow(TournamentsUiState())
    val uiState: StateFlow<TournamentsUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun select(board: TournamentBoard) = state.update { it.copy(board = board) }

    fun refresh() {
        state.update { it.copy(loading = true, arenaError = false, championshipError = false) }
        viewModelScope.launch {
            runCatching { repository.flushPending() }
            val month = YearMonth.from(DailyChallenge.dateAt(clock.now()))
            val year = month.year
            val (arena, championship, previousArena, previousChampionship) = coroutineScope {
                val currentArena = async { repository.load(TournamentBoard.ARENA, month.toString()) }
                val currentChampionship = async { repository.load(TournamentBoard.CHAMPIONSHIP, year.toString()) }
                val priorArena = async { repository.load(TournamentBoard.ARENA, month.minusMonths(1).toString()) }
                val priorChampionship = async { repository.load(TournamentBoard.CHAMPIONSHIP, (year - 1).toString()) }
                listOf(currentArena.await(), currentChampionship.await(), priorArena.await(), priorChampionship.await())
            }
            state.update {
                it.copy(
                    loading = false,
                    arenaError = arena.isFailure,
                    championshipError = championship.isFailure,
                    arena = arena.getOrNull(),
                    championship = championship.getOrNull(),
                    previousArena = previousArena.getOrNull(),
                    previousChampionship = previousChampionship.getOrNull(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentsScreen(
    onBack: () -> Unit,
    viewModel: TournamentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = if (state.board == TournamentBoard.ARENA) state.arena else state.championship
    val previous = if (state.board == TournamentBoard.ARENA) state.previousArena else state.previousChampionship

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tournaments_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.tournaments_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.board == TournamentBoard.ARENA,
                    onClick = { viewModel.select(TournamentBoard.ARENA) },
                    label = { Text(stringResource(R.string.tournaments_arena)) },
                )
                FilterChip(
                    selected = state.board == TournamentBoard.CHAMPIONSHIP,
                    onClick = { viewModel.select(TournamentBoard.CHAMPIONSHIP) },
                    label = { Text(stringResource(R.string.tournaments_championship)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                state.loading && snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error && snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.tournaments_error))
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.leaderboard_retry)) }
                    }
                }
                snapshot != null -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    item {
                        Text(
                            if (state.board == TournamentBoard.ARENA) {
                                stringResource(R.string.tournaments_arena_period, snapshot.period)
                            } else {
                                stringResource(R.string.tournaments_championship_period, snapshot.period)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (snapshot.entries.isEmpty()) {
                        item { Text(stringResource(R.string.tournaments_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(snapshot.entries, key = { it.uid }) { TournamentRow(it) }
                    }
                    snapshot.playerEntry?.takeIf { it.rank == null }?.let { player ->
                        item { Text(stringResource(R.string.tournaments_your_row), style = MaterialTheme.typography.titleSmall) }
                        item { TournamentRow(player) }
                    }
                    if (previous != null) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.board == TournamentBoard.ARENA) {
                                    stringResource(R.string.tournaments_previous_month)
                                } else {
                                    stringResource(R.string.tournaments_previous_year)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(previous.entries.take(3), key = { "podium_${it.uid}" }) { TournamentRow(it) }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tournaments_empty))
                }
            }
        }
    }
}

@Composable
private fun TournamentRow(entry: TournamentEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isCurrentUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.rank?.let { "#$it" } ?: stringResource(R.string.tournaments_you), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (entry.isCurrentUser) stringResource(R.string.tournaments_you) else entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.tournaments_games, entry.games),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.tournaments_points, entry.points), style = MaterialTheme.typography.titleMedium)
        }
    }
}
