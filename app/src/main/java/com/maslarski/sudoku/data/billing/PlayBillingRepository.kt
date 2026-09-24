package com.maslarski.sudoku.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryPurchasesAsync
import com.maslarski.sudoku.domain.model.BillingAvailability
import com.maslarski.sudoku.domain.model.BillingEvent
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.ProductId
import com.maslarski.sudoku.domain.model.StoreProduct
import com.maslarski.sudoku.domain.repository.BillingRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing Library 9 integration.
 *
 * - `sudoku_lives_5`  — consumable; each purchase is consumed and grants [Lives.LIVES_PER_PURCHASE].
 * - `sudoku_unlimited` — non-consumable; acknowledged once and unlocks [LivesRepository.setUnlimited].
 *
 * Purchases are processed idempotently: consumables are only granted when consumption succeeds, and
 * the entitlement for the non-consumable is re-derived from `queryPurchasesAsync` on every connection,
 * which also covers purchases completed outside the app or on another device.
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val livesRepository: LivesRepository,
    private val scope: CoroutineScope,
) : BillingRepository, PurchasesUpdatedListener {

    private val _availability = MutableStateFlow(BillingAvailability.CONNECTING)
    override val availability: StateFlow<BillingAvailability> = _availability.asStateFlow()

    private val _products = MutableStateFlow<List<StoreProduct>>(emptyList())
    override val products: StateFlow<List<StoreProduct>> = _products.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events: Flow<BillingEvent> = _events.asSharedFlow()

    private val productDetails = mutableMapOf<ProductId, ProductDetails>()
    private var activityRef: WeakReference<Activity>? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        connect()
    }

    /** The billing flow needs a foreground Activity; the current one is registered by MainActivity. */
    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity(activity: Activity) {
        if (activityRef?.get() === activity) activityRef = null
    }

    private fun connect() {
        _availability.value = BillingAvailability.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _availability.value = BillingAvailability.AVAILABLE
                    scope.launch {
                        loadProducts()
                        restorePurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    _availability.value = BillingAvailability.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() retries in the background; surface the state meanwhile.
                _availability.value = BillingAvailability.CONNECTING
            }
        })
    }

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProductId.entries.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it.id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        // PBL 8+/9 listener signature: (BillingResult, QueryProductDetailsResult) where the result also carries
        // products Play could not fetch (e.g. not configured in Play Console yet) with a per-product status code.
        val (billingResult, result) = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { br, res -> if (cont.isActive) cont.resume(br to res) }
        }
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetailsAsync failed: ${billingResult.debugMessage}")
            return
        }
        result.unfetchedProductList.forEach { u -> Log.w(TAG, "Unfetched product ${u.productId}: ${statusName(u)}") }
        val details = result.productDetailsList
        productDetails.clear()
        val storeProducts = details.mapNotNull { pd ->
            val id = ProductId.fromId(pd.productId) ?: return@mapNotNull null
            productDetails[id] = pd
            StoreProduct(
                productId = id,
                title = pd.name,
                description = pd.description,
                formattedPrice = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
            )
        }
        _products.value = storeProducts.sortedBy { it.productId.ordinal }
    }

    override fun launchPurchase(productId: ProductId) {
        val activity = activityRef?.get()
        val details = productDetails[productId]
        if (activity == null || details == null || !client.isReady) {
            _events.tryEmit(BillingEvent.Error(null))
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.tryEmit(BillingEvent.Error(result.debugMessage))
        }
    }

    override suspend fun restorePurchases() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val owned = result.purchasesList
        // Entitlement for the non-consumable is authoritative from Play; revoke locally if refunded.
        val hasUnlimited = owned.any { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.contains(ProductId.UNLIMITED_LIVES.id)
        }
        livesRepository.setUnlimited(hasUnlimited)
        owned.forEach { handlePurchase(it, notify = false) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { p -> scope.launch { handlePurchase(p, notify = true) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(BillingEvent.UserCancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restorePurchases() }
            else -> _events.tryEmit(BillingEvent.Error(result.debugMessage))
        }
    }

    private suspend fun handlePurchase(purchase: Purchase, notify: Boolean) {
        val ids = purchase.products.mapNotNull { ProductId.fromId(it) }
        if (ids.isEmpty()) return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                if (notify) ids.forEach { _events.tryEmit(BillingEvent.PurchasePending(it)) }
                return
            }
            Purchase.PurchaseState.PURCHASED -> Unit
            else -> return
        }

        for (id in ids) {
            if (id.consumable) {
                // Consuming is what grants the item: Play refuses to consume twice, so lives are never double-granted.
                val consume = client.consumePurchase(
                    ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                )
                if (consume.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    livesRepository.addLives(Lives.LIVES_PER_PURCHASE * purchase.quantity.coerceAtLeast(1))
                    if (notify) _events.tryEmit(BillingEvent.PurchaseApplied(id))
                } else if (notify) {
                    _events.tryEmit(BillingEvent.Error(consume.billingResult.debugMessage))
                }
            } else {
                livesRepository.setUnlimited(true)
                if (!purchase.isAcknowledged) {
                    val ack = client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                    )
                    if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "acknowledge failed: ${ack.debugMessage}")
                    }
                }
                if (notify) _events.tryEmit(BillingEvent.PurchaseApplied(id))
            }
        }
    }

    private fun statusName(product: UnfetchedProduct): String = when (product.statusCode) {
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "INVALID_PRODUCT_ID_FORMAT"
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "PRODUCT_NOT_FOUND"
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "NO_ELIGIBLE_OFFER"
        else -> "UNKNOWN"
    }

    private companion object {
        const val TAG = "PlayBilling"
    }
}
