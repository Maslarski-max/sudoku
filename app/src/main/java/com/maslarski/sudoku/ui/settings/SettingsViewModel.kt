package com.maslarski.sudoku.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.domain.model.AppSettings
import com.maslarski.sudoku.domain.model.AppTheme
import com.maslarski.sudoku.domain.model.DisplayNameError
import com.maslarski.sudoku.domain.model.DisplayNameException
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.ProfileRepository
import com.maslarski.sudoku.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NameEditState(
    val saving: Boolean = false,
    val error: DisplayNameError? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    livesRepository: LivesRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())
    val unlimited: StateFlow<Boolean> =
        livesRepository.lives.map { it.unlimited }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)
    val displayName: StateFlow<String?> =
        profileRepository.displayName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)
    private val mutableNameEdit = MutableStateFlow(NameEditState())
    val nameEdit: StateFlow<NameEditState> = mutableNameEdit.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch { profileRepository.refresh() }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setDarkMode(darkMode: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(darkMode) }
    }

    fun saveName(raw: String) {
        if (mutableNameEdit.value.saving) return
        mutableNameEdit.value = NameEditState(saving = true)
        viewModelScope.launch {
            val result = profileRepository.setDisplayName(raw)
            mutableNameEdit.value = result.fold(
                onSuccess = { NameEditState(saved = true) },
                onFailure = { error ->
                    NameEditState(
                        error = (error as? DisplayNameException)?.error ?: DisplayNameError.NETWORK,
                    )
                },
            )
        }
    }

    fun resetNameEdit() {
        mutableNameEdit.value = NameEditState()
    }
}
