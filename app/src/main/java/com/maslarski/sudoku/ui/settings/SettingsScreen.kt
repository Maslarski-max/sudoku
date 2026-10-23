package com.maslarski.sudoku.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.sudoku.BuildConfig
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.GridSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareText = stringResource(
        R.string.settings_share_text,
        "https://play.google.com/store/apps/details?id=${context.packageName}",
    )
    val shareTitle = stringResource(R.string.settings_share_title)
    var showHowToPlay by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_share)) },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(share, shareTitle))
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_how_to_play)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                modifier = Modifier.clickable { showHowToPlay = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sound)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                trailingContent = {
                    Switch(checked = settings.soundEnabled, onCheckedChange = viewModel::setSoundEnabled)
                },
                modifier = Modifier.clickable { viewModel.setSoundEnabled(!settings.soundEnabled) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_vibration)) },
                leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                trailingContent = {
                    Switch(checked = settings.vibrationEnabled, onCheckedChange = viewModel::setVibrationEnabled)
                },
                modifier = Modifier.clickable { viewModel.setVibrationEnabled(!settings.vibrationEnabled) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                modifier = Modifier.clickable { showPrivacyPolicy = true },
            )
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showHowToPlay) {
        AlertDialog(
            onDismissRequest = { showHowToPlay = false },
            title = { Text(stringResource(R.string.settings_how_to_play)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_rule_standard))
                    GridSize.entries.forEach { grid ->
                        Text(stringResource(R.string.settings_grid_shape, grid.size, grid.boxRows, grid.boxCols))
                    }
                    Text(stringResource(R.string.settings_rule_enter))
                    Text(stringResource(R.string.settings_rule_notes))
                    Text(stringResource(R.string.settings_rule_candidates))
                    Text(stringResource(R.string.settings_rule_hint))
                    Text(stringResource(R.string.settings_rule_free_hints))
                    Text(stringResource(R.string.settings_rule_mistakes))
                    Text(stringResource(R.string.settings_rule_lives))
                    Text(stringResource(R.string.settings_rule_countdown))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHowToPlay = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (showPrivacyPolicy) {
        Dialog(
            onDismissRequest = { showPrivacyPolicy = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.settings_privacy_policy)) },
                        actions = {
                            IconButton(onClick = { showPrivacyPolicy = false }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.settings_close),
                                )
                            }
                        },
                    )
                    Text(
                        text = stringResource(R.string.privacy_policy_text),
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
