package com.maslarski.sudoku.domain.model

enum class ProductId(val id: String, val consumable: Boolean) {
    FIVE_LIVES("sudoku_lives_5", consumable = true),
    UNLIMITED_LIVES("sudoku_unlimited", consumable = false);

    companion object {
        fun fromId(id: String): ProductId? = entries.firstOrNull { it.id == id }
    }
}

data class StoreProduct(
    val productId: ProductId,
    val title: String,
    val description: String,
    val formattedPrice: String,
)

sealed interface BillingEvent {
    data class PurchaseApplied(val productId: ProductId) : BillingEvent
    data class PurchasePending(val productId: ProductId) : BillingEvent
    data object UserCancelled : BillingEvent
    data class Error(val message: String?) : BillingEvent
}

enum class BillingAvailability { CONNECTING, AVAILABLE, UNAVAILABLE }
