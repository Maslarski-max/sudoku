package com.maslarski.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.sudoku.data.billing.PlayBillingRepository
import com.maslarski.sudoku.domain.model.AppTheme
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.SettingsRepository
import com.maslarski.sudoku.ui.navigation.SudokuNavHost
import com.maslarski.sudoku.ui.theme.SudokuTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var billing: PlayBillingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var livesRepository: LivesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeSettings by remember {
                combine(settingsRepository.settings, livesRepository.lives) { settings, lives ->
                    settings.theme.effective(lives.unlimited) to settings.darkMode
                }
            }.collectAsStateWithLifecycle(AppTheme.DEFAULT to true)
            val appTheme = themeSettings.first
            val darkMode = themeSettings.second
            val darkBars = appTheme.isPremium || darkMode
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkBars
                    isAppearanceLightNavigationBars = !darkBars
                }
            }
            SudokuTheme(appTheme = appTheme, darkMode = darkMode) {
                SudokuNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        billing.attachActivity(this)
    }

    override fun onStop() {
        billing.detachActivity(this)
        super.onStop()
    }
}
