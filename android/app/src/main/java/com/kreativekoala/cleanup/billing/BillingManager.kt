package com.kreativekoala.cleanup.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.kreativekoala.cleanup.data.model.ProductIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Port of iOS EntitlementManager's StoreKit 2 logic using Google Play Billing Library 7.x.
 *
 * Handles connection to Google Play, product queries, purchases, and subscription validation.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var purchaseCallback: ((Boolean) -> Unit)? = null

    private val allProductIds = ProductIds.proProductIds + ProductIds.archiveProductIds

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    queryProducts()
                    queryExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                // Retry connection
                startConnection()
            }
        })
    }

    private fun queryProducts() {
        val productList = allProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = productDetailsList
            }
        }
    }

    // MARK: - Product Helpers

    val proProducts: List<ProductDetails>
        get() = _products.value.filter { it.productId in ProductIds.proProductIds }

    val archiveProducts: List<ProductDetails>
        get() = _products.value.filter { it.productId in ProductIds.archiveProductIds }

    fun getMonthlyProduct(): ProductDetails? =
        _products.value.find { it.productId == ProductIds.PRO_MONTHLY }

    fun getYearlyProduct(): ProductDetails? =
        _products.value.find { it.productId == ProductIds.PRO_YEARLY }

    // MARK: - Purchase

    fun launchPurchase(
        activity: Activity,
        productDetails: ProductDetails,
        onResult: (Boolean) -> Unit
    ) {
        _purchaseInProgress.value = true
        _lastError.value = null
        purchaseCallback = onResult

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            _purchaseInProgress.value = false
            _lastError.value = "No offer available for this product"
            onResult(false)
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        _purchaseInProgress.value = false

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                    }
                }
                purchaseCallback?.invoke(true)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseCallback?.invoke(false)
            }
            else -> {
                _lastError.value = "Purchase failed: ${result.debugMessage}"
                purchaseCallback?.invoke(false)
            }
        }
        purchaseCallback = null
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _lastError.value = "Failed to acknowledge purchase"
            }
        }
    }

    // MARK: - Query Existing Purchases

    fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private var purchaseProcessCallback: ((Set<String>) -> Unit)? = null

    fun setOnPurchasesProcessed(callback: (Set<String>) -> Unit) {
        purchaseProcessCallback = callback
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val activeProductIds = mutableSetOf<String>()

        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                activeProductIds.addAll(purchase.products)
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
        }

        purchaseProcessCallback?.invoke(activeProductIds)
    }

    fun destroy() {
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}
