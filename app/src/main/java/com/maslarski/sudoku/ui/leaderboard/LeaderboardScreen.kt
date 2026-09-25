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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.LeaderboardEntry
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import com.maslarski.sudoku.ui.components.difficultyLabel
import com.maslarski.sudoku.ui.components.formatDuration
import com.maslarski.sudoku.ui.components.gridSizeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUiState(
    val category: LeaderboardCategory,
    val entries: List<LeaderboardEntry> = emptyList(),
    val personalBest: Score? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LeaderboardRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val category = MutableStateFlow(
        LeaderboardCategory(
            gridSize = GridSize.entries.firstOrNull { it.size == savedStateHandle.get<Int>("gridSize") } ?: GridSize.NINE,
            difficulty = savedStateHandle.get<String>("difficulty")?.let { d -> Difficulty.entries.firstOrNull { it.name == d } }
                ?: Difficulty.EASY,
        ),
    )
    private val status = MutableStateFlow(Triple<Boolean, Boolean, Score?>(true, false, null))

    val uiState: StateFlow<LeaderboardUiState> = combine(
        category,
        category.flatMapLatest { repository.observeTop(it) },
        status,
    ) { cat, entries, (loading, error, best) ->
        LeaderboardUiState(cat, entries, best, loading, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUiState(category.value))

    init {
        refresh()
    }

    fun select(gridSize: GridSize? = null, difficulty: Difficulty? = null) {
        category.update { it.copy(gridSize = gridSize ?: it.gridSize, difficulty = difficulty ?: it.difficulty) }
        refresh()
    }

    fun refresh() {
        val cat = category.value
        status.update { it.copy(first = true, second = false) }
        viewModelScope.launch {
            val result = runCatching {
                authRepository.ensureSignedIn()
                repository.refresh(cat).getOrThrow()
            }
            val best = runCatching { repository.personalBest(cat) }.getOrNull()
            if (category.value == cat) status.value = Triple(false, result.isFailure, best)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.leaderboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GridSize.entries.forEachIndexed { i, g ->
                    SegmentedButton(
                        selected = state.category.gridSize == g,
                        onClick = { viewModel.select(gridSize = g) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = GridSize.entries.size),
                        label = { Text(gridSizeLabel(g)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = state.category.difficulty == d,
                        onClick = { viewModel.select(difficulty = d) },
                        label = { Text(difficultyLabel(d)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val best = state.personalBest
            Text(
                if (best != null) stringResource(R.string.leaderboard_your_best, best.points, formatDuration(best.timeMillis), best.moves)
                else stringResource(R.string.leaderboard_no_best),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.loading && state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.leaderboard_loading))
                    }
                }
                state.error && state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.leaderboard_error))
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.leaderboard_retry)) }
                    }
                }
                state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.leaderboard_empty))
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.entries, key = { it.score.uid }) { entry -> EntryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LeaderboardEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isCurrentUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.leaderboard_rank, entry.rank), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (entry.isCurrentUser) stringResource(R.string.leaderboard_you) else entry.score.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.leaderboard_entry, entry.score.points, formatDuration(entry.score.timeMillis), entry.score.moves),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
