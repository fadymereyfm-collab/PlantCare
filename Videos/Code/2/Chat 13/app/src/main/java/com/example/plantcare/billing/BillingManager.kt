package com.example.plantcare.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manages Google Play Billing for PlantCare Pro subscriptions.
 *
 * SKUs:
 *  - monthly_pro  : €2.99/month subscription (7-day free trial)
 *  - yearly_pro   : €19.99/year subscription (7-day free trial)
 *  - lifetime_pro : €39.99 one-time purchase
 *
 * Usage: call connect() once (e.g. in Application.onCreate or MainActivity.onStart),
 * then observe isPro to gate Pro features.
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val SKU_MONTHLY = "monthly_pro"
        const val SKU_YEARLY = "yearly_pro"
        const val SKU_LIFETIME = "lifetime_pro"

        @Volatile
        private var INSTANCE: BillingManager? = null

        @JvmStatic
        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _isPro = MutableStateFlow(ProStatusManager.isPro(context))
    val isPro: StateFlow<Boolean> = _isPro

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    /** Connect to Google Play Billing service. Call once at app start. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    cont.resume(ok)
                    if (ok) {
                        // Refresh Pro state from Play on reconnect
                        refreshPurchases()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _isPro.value = ProStatusManager.isPro(context)
                }
            })
        }
    }

    /** Query product details for display in paywall. */
    suspend fun queryProducts(): List<ProductDetails> = withContext(Dispatchers.IO) {
        val subs = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_YEARLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ))
            .build()

        val inapp = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()

        val subsResult = billingClient.queryProductDetails(subs)
        val inappResult = billingClient.queryProductDetails(inapp)

        val all = (subsResult.productDetailsList.orEmpty() + inappResult.productDetailsList.orEmpty())
        _products.value = all
        all
    }

    /**
     * Launch the purchase flow for a given product.
     * For subscriptions, pass the desired offer token from productDetails.subscriptionOfferDetails.
     */
    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        val productList = if (offerToken != null) {
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productList)
            .build()

        billingClient.launchBillingFlow(activity, params)
    }

    /** Restore purchases — call this from "Restore Purchases" button in paywall. */
    suspend fun restorePurchases() = withContext(Dispatchers.IO) {
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val subsPurchases = billingClient.queryPurchasesAsync(subsParams).purchasesList
        val inappPurchases = billingClient.queryPurchasesAsync(inappParams).purchasesList

        val allPurchases = subsPurchases + inappPurchases
        val hasPro = allPurchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        grantOrRevokePro(hasPro)
        allPurchases
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            val hasPro = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (hasPro) {
                purchases.filter { !it.isAcknowledged }.forEach { acknowledgePurchase(it) }
            }
            grantOrRevokePro(hasPro)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User cancelled — no change needed
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { _ -> /* ignore result — retry on next connect */ }
    }

    private fun refreshPurchases() {
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(subsParams) { _, subsPurchases ->
            val inappParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(inappParams) { _, inappPurchases ->
                val hasPro = (subsPurchases + inappPurchases)
                    .any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                grantOrRevokePro(hasPro)
            }
        }
    }

    private fun grantOrRevokePro(isPro: Boolean) {
        ProStatusManager.setPro(context, isPro)
        _isPro.value = isPro
    }

    /** Java-friendly fire-and-forget wrappers (called from App.java / Activity onResume). */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun connectAsync() {
        GlobalScope.launch(Dispatchers.IO) { connect() }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun restorePurchasesAsync() {
        GlobalScope.launch(Dispatchers.IO) { runCatching { restorePurchases() } }
    }
}
