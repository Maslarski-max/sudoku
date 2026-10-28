package com.maslarski.sudoku.ui.leaderboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.Trophy
import com.maslarski.sudoku.domain.model.TrophyTier
import com.maslarski.sudoku.domain.repository.DailyChallengeRepository
import com.maslarski.sudoku.domain.repository.TrophyRepository
import com.maslarski.sudoku.domain.time.EpochClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrophyRoomUiState(
    val trophies: List<Trophy> = emptyList(),
    val stampedDates: Set<String> = emptySet(),
    val month: YearMonth = YearMonth.of(2000, 1),
)

@HiltViewModel
class TrophyRoomViewModel @Inject constructor(
    trophyRepository: TrophyRepository,
    dailyChallengeRepository: DailyChallengeRepository,
    clock: EpochClock,
) : ViewModel() {
    private val currentMonth = flow {
        while (true) {
            emit(YearMonth.from(DailyChallenge.dateAt(clock.now())))
            delay(60_000L)
        }
    }

    val uiState: StateFlow<TrophyRoomUiState> = combine(
        trophyRepository.trophies,
        dailyChallengeRepository.stampedDates,
        currentMonth,
    ) { trophies, stamps, month ->
        TrophyRoomUiState(trophies, stamps, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrophyRoomUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyRoomScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    viewModel: TrophyRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val premiumUnlocked = state.trophies.firstOrNull { it.premiumOnly }?.unlocked == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trophy_room_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!premiumUnlocked) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.trophy_premium_banner),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Button(onClick = onOpenStore) { Text(stringResource(R.string.trophy_premium_unlock)) }
                        }
                    }
                }
            }
            item { MonthCalendar(state.month, state.stampedDates) }
            items(state.trophies.chunked(2), key = { row -> row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { trophy ->
                        TrophyBadge(trophy, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(month: YearMonth, stamps: Set<String>) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                R.string.trophy_calendar_title,
                month.month.getDisplayName(TextStyle.FULL, locale),
                month.year,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val blanks = month.atDay(1).dayOfWeek.value - 1
        val cells = (List(blanks) { 0 } + (1..month.lengthOfMonth()).toList()).chunked(7)
        cells.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { 0 }).forEach { day ->
                    val date = if (day == 0) null else month.atDay(day).toString()
                    val stamped = date != null && date in stamps
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                            .background(
                                if (stamped) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != 0) {
                            Text(
                                "$day",
                                color = if (stamped) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrophyBadge(trophy: Trophy, modifier: Modifier = Modifier) {
    val base = when {
        !trophy.unlocked -> Color(0xFF727780)
        trophy.tier == TrophyTier.GOLD -> Color(0xFFFFC247)
        trophy.tier == TrophyTier.SILVER -> Color(0xFFB7C5D8)
        trophy.tier == TrophyTier.BRONZE -> Color(0xFFC98550)
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.43f
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color.Black.copy(alpha = 0.25f), radius, center.copy(y = center.y + radius * 0.12f))
                drawCircle(
                    Brush.radialGradient(
                        listOf(base.copy(alpha = 1f), base.copy(alpha = 0.85f), base.copy(alpha = 0.55f)),
                        center = center.copy(x = center.x - radius * 0.22f, y = center.y - radius * 0.24f),
                        radius = radius * 1.5f,
                    ),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.85f), base, Color.Black.copy(alpha = 0.35f)),
                        start = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                        end = androidx.compose.ui.geometry.Offset(center.x + radius, center.y + radius),
                    ),
                    radius = radius * 0.9f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.9f), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                        end = androidx.compose.ui.geometry.Offset(center.x + radius, center.y),
                    ),
                    startAngle = 205f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius * 0.83f, center.y - radius * 0.83f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.66f, radius * 1.66f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            if (!trophy.unlocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = trophy.title,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(trophy.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            trophy.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
