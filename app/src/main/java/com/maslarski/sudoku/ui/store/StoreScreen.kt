package com.maslarski.sudoku.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.BillingAvailability
import com.maslarski.sudoku.domain.model.BillingEvent
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.ProductId
import com.maslarski.sudoku.domain.model.StoreProduct
import com.maslarski.sudoku.domain.repository.BillingRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoreUiState(
    val availability: BillingAvailability = BillingAvailability.CONNECTING,
    val products: List<StoreProduct> = emptyList(),
    val lives: Lives? = null,
)

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val billing: BillingRepository,
    livesRepository: LivesRepository,
) : ViewModel() {

    val uiState: StateFlow<StoreUiState> = combine(
        billing.availability,
        billing.products,
        livesRepository.lives,
    ) { availability, products, lives -> StoreUiState(availability, products, lives) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreUiState())

    val events: Flow<BillingEvent> = billing.events

    fun buy(productId: ProductId) = billing.launchPurchase(productId)

    fun restore() = viewModelScope.launch { billing.restorePurchases() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onBack: () -> Unit,
    viewModel: StoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(resources) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BillingEvent.PurchaseApplied -> resources.getString(R.string.store_purchase_success)
                is BillingEvent.PurchasePending -> resources.getString(R.string.store_purchase_pending)
                BillingEvent.UserCancelled -> null
                is BillingEvent.Error -> resources.getString(R.string.store_error_generic)
            }
            if (message != null) snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.store_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.store_subtitle), style = MaterialTheme.typography.bodyLarge)

            val lives = state.lives
            if (lives != null) {
                Text(
                    if (lives.unlimited) stringResource(R.string.store_unlimited_active)
                    else pluralStringResource(R.plurals.store_current_lives, lives.count, lives.count),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            when (state.availability) {
                BillingAvailability.CONNECTING -> Text(stringResource(R.string.store_connecting))
                BillingAvailability.UNAVAILABLE -> Text(
                    stringResource(R.string.store_error_unavailable),
                    color = MaterialTheme.colorScheme.error,
                )
                BillingAvailability.AVAILABLE -> Unit
            }

            ProductId.entries.forEach { id ->
                val product = state.products.firstOrNull { it.productId == id }
                val owned = id == ProductId.UNLIMITED_LIVES && lives?.unlimited == true
                ProductCard(
                    productId = id,
                    product = product,
                    owned = owned,
                    enabled = product != null && state.availability == BillingAvailability.AVAILABLE && !owned,
                    onBuy = { viewModel.buy(id) },
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::restore, enabled = state.availability == BillingAvailability.AVAILABLE) {
                Text(stringResource(R.string.store_restore))
            }
        }
    }
}

@Composable
private fun ProductCard(
    productId: ProductId,
    product: StoreProduct?,
    owned: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
) {
    val (title, description) = when (productId) {
        ProductId.FIVE_LIVES -> R.string.store_five_lives_title to R.string.store_five_lives_description
        ProductId.UNLIMITED_LIVES -> R.string.store_unlimited_title to R.string.store_unlimited_description
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onBuy, enabled = enabled) {
                Text(
                    when {
                        owned -> stringResource(R.string.store_owned)
                        product != null -> product.formattedPrice.ifBlank { stringResource(R.string.store_buy) }
                        else -> stringResource(R.string.store_unavailable)
                    },
                )
            }
        }
    }
}
