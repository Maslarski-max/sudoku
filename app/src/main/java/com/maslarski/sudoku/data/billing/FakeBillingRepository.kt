package com.maslarski.sudoku.data.billing

import com.maslarski.sudoku.di.ApplicationScope
import com.maslarski.sudoku.domain.model.BillingAvailability
import com.maslarski.sudoku.domain.model.BillingEvent
import com.maslarski.sudoku.domain.model.ProductId
import com.maslarski.sudoku.domain.model.StoreProduct
import com.maslarski.sudoku.domain.repository.BillingRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only stand-in for Play Billing (enabled with `-PfakeBilling=true`), for emulators without
 * Google Play: products resolve immediately with placeholder prices and "purchases" are granted locally.
 */
@Singleton
class FakeBillingRepository @Inject constructor(
    private val livesRepository: LivesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : BillingRepository {

    override val availability: StateFlow<BillingAvailability> = MutableStateFlow(BillingAvailability.AVAILABLE)

    override val products: StateFlow<List<StoreProduct>> = MutableStateFlow(
        listOf(
            StoreProduct(ProductId.FIVE_LIVES, "5 Lives", "Adds five lives instantly.", "$0.99"),
            StoreProduct(
                ProductId.UNLIMITED_LIVES,
                "Unlimited Lives",
                "Removes the lives system forever and unlocks 3 premium themes: Cyberpunk Neon, Retro Arcade and Zen Forest. One-time purchase.",
                "$4.99",
            ),
        ),
    )

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events: Flow<BillingEvent> = _events

    override fun launchPurchase(productId: ProductId) {
        scope.launch {
            when (productId) {
                ProductId.FIVE_LIVES -> livesRepository.addLives(com.maslarski.sudoku.domain.model.Lives.LIVES_PER_PURCHASE)
                ProductId.UNLIMITED_LIVES -> livesRepository.setUnlimited(true)
            }
            _events.emit(BillingEvent.PurchaseApplied(productId))
        }
    }

    override suspend fun restorePurchases() = Unit
}
