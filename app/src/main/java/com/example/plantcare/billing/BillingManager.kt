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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
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

        /**
         * Bug #10 fix (b): how long a previously server-verified Pro state
         * stays trusted while the verifyPurchase Cloud Function is
         * unreachable. Generous (14 days) so normal connectivity gaps —
         * travel, roaming, a CF cold-start blip — never cost a paying user
         * their Pro. Not exploitable: the grace window only ever AFFIRMS a
         * state that was already server-verified, and a forger who blocks
         * the CF never earns that first verification.
         */
        private const val VERIFICATION_GRACE_MS = 14L * 24 * 60 * 60 * 1000

        /**
         * Bug #10 fix (a): verifyPurchase `reason` values that mean "the
         * server could not actually determine entitlement" (its own
         * infrastructure failed) rather than "Play says this purchase is
         * invalid". These must be treated as transient — treating them as
         * a hard rejection would revoke Pro for every paying customer the
         * moment the CF is deployed without its PLAY_SA_KEY secret set.
         */
        private val INDETERMINATE_REASONS = setOf("server_misconfigured", "play_api_error")

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
        // 2026-05-18 audit-pass-3 fix (B1): the previous suspendCoroutine
        // implementation had two failure modes:
        //   1. If `onBillingSetupFinished` somehow fired twice (e.g. SDK
        //      auto-reconnect during setup), `cont.resume(ok)` would
        //      throw `IllegalStateException: Already resumed`.
        //   2. If `onBillingServiceDisconnected` fired without setup ever
        //      completing (Play Store killed mid-handshake), `cont` was
        //      never resumed → the connect() coroutine hangs forever,
        //      blocking `BillingManager.getInstance(...).connectAsync()`
        //      and starving every paywall subscriber of `isPro`.
        // Guards: AtomicBoolean prevents double-resume; the disconnect
        // path now resolves false if setup hadn't already completed, so
        // the caller cleanly observes the failure instead of hanging.
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (resumed.compareAndSet(false, true)) {
                        cont.resume(ok)
                    }
                    if (ok) {
                        // Refresh Pro state from Play on reconnect.
                        refreshPurchases()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Resolve the connect() suspension as failure if setup
                    // never reached us — otherwise the local cached Pro
                    // flag refresh is the only side-effect (existing behaviour).
                    if (resumed.compareAndSet(false, true)) {
                        cont.resume(false)
                    }
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
        // Bug #10: route through server-side verification instead of
        // trusting the local Billing SDK response. See
        // grantProWithVerification for the full rationale + fallback
        // behavior.
        grantProWithVerification(allPurchases)
        allPurchases
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            val hasPro = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (hasPro) {
                purchases.filter { !it.isAcknowledged }.forEach { acknowledgePurchase(it) }
            }
            // Bug #10: server-side verification. Acknowledge runs in
            // parallel (it's idempotent on the Play side); the
            // verification call below decides the Pro state. We use
            // GlobalScope because PurchasesUpdatedListener is invoked
            // off the main thread by the Billing SDK and we want to
            // stay off main. The BillingManager singleton outlives the
            // process so leaking the coroutine here is bounded.
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                grantProWithVerification(purchases)
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User cancelled — no change needed
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            // 2026-05-18 audit-pass-3 fix (B4): user tried to buy
            // something they already own (re-install, account swap,
            // bought-on-another-device). Pre-fix this fell through to a
            // silent no-op and the user thought the purchase failed.
            // Now: kick refreshPurchases so the existing entitlement is
            // picked up + Pro gets re-granted via the normal verification
            // path. The user sees Pro activate within seconds.
            refreshPurchases()
        } else {
            // 2026-05-18 audit-pass-3 fix (B4): every other response code
            // (BILLING_UNAVAILABLE, SERVICE_DISCONNECTED, DEVELOPER_ERROR,
            // ERROR, NETWORK_ERROR, SERVICE_TIMEOUT, SERVICE_UNAVAILABLE,
            // FEATURE_NOT_SUPPORTED, ITEM_NOT_OWNED, ITEM_UNAVAILABLE)
            // used to be silently swallowed → support ticket black-hole
            // when users complained "purchase didn't work". Now logged
            // with the response code so we can see the distribution in
            // Crashlytics and decide which deserve user-facing toasts.
            com.example.plantcare.CrashReporter.log(
                RuntimeException("onPurchasesUpdated: unhandled responseCode=${result.responseCode} debugMessage=${result.debugMessage}")
            )
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        acknowledgePurchaseWithRetry(purchase, attempt = 0)
    }

    /**
     * B3: Google Play auto-refunds any non-acknowledged purchase after
     * 3 days. Pre-fix the manager swallowed the result and relied on
     * the next `connect()` to discover the missed acknowledgement —
     * but `connect()` only ever runs at app start, so a user who
     * purchased Pro and then closed the app immediately was on a
     * clock with no retry until next launch. Now we retry up to
     * 3 times with exponential backoff right inside the same
     * coroutine/process, then fall back to the connect-time refresh
     * for the cold-start case.
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun acknowledgePurchaseWithRetry(purchase: Purchase, attempt: Int) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) return@acknowledgePurchase
            if (attempt >= 2) return@acknowledgePurchase  // 3 attempts total — connect()-time refresh handles the rest
            // Backoff: 1s, 4s. Use the application-scoped IO dispatcher;
            // the BillingManager itself is a singleton so a leaked
            // coroutine here doesn't outlive the process.
            GlobalScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(if (attempt == 0) 1_000L else 4_000L)
                acknowledgePurchaseWithRetry(purchase, attempt + 1)
            }
        }
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
                // Bug #10: server-side verification on the connect-time
                // refresh. Catches forgeries that slipped through on a
                // previous launch where CF was unreachable.
                val all = subsPurchases + inappPurchases
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                GlobalScope.launch(Dispatchers.IO) {
                    grantProWithVerification(all)
                }
            }
        }
    }

    private fun grantOrRevokePro(isPro: Boolean) {
        ProStatusManager.setPro(context, isPro)
        _isPro.value = isPro
    }

    /**
     * Bug #10 fix (2026-05-14, server-side Pro verification):
     *
     * Replaces the trust-the-client `grantOrRevokePro(true)` path. For
     * each PURCHASED purchase we call the `verifyPurchase` Cloud
     * Function which cross-checks the purchaseToken against Google
     * Play's records via the Play Developer API. The CF writes the
     * verified state directly to Firestore (`users/{uid}/billing/proStatus`)
     * and returns the result.
     *
     * Failure modes:
     *  - CF rejects the purchase (Play API says "unknown token" or
     *    "cancelled" or "expired") → revoke Pro locally + cloud (CF
     *    already wrote false to Firestore).
     *  - CF call itself fails (network down, callable not deployed,
     *    secret missing) → fall back to OPTIMISTIC GRANT. The local
     *    Billing SDK said "purchased", we don't want to deny a
     *    legitimate user with a flaky connection. The next
     *    refreshPurchases / restorePurchases tick will re-verify and
     *    revoke if the result is forged.
     *
     * Threading: runs on the calling coroutine's dispatcher. All three
     * callers (refreshPurchases, restorePurchases, onPurchasesUpdated)
     * already wrap in IO; the suspendCoroutine on the callable
     * returns to the same context.
     */
    private suspend fun grantProWithVerification(purchases: List<Purchase>) {
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            // Nothing purchased → revoke. Direct local write is fine for
            // revoke (no security risk in clearing isPro).
            grantOrRevokePro(false)
            return
        }

        // Walk each purchase. ANY successful verification grants Pro;
        // explicit rejection of ALL revokes; transient failure preserves
        // the optimistic-grant fallback.
        var anyVerified = false
        var anyExplicitlyRejected = false
        var anyTransientFailure = false

        for (purchase in purchased) {
            val firstSku = purchase.products.firstOrNull() ?: continue
            val productType = productTypeFor(firstSku)
            val result = runCatching {
                verifyOnePurchaseWithCloud(firstSku, purchase.purchaseToken, productType)
            }
            if (result.isFailure) {
                com.example.plantcare.CrashReporter.log(result.exceptionOrNull() ?: Exception("verify failed"))
                anyTransientFailure = true
                continue
            }
            val verified = result.getOrNull()
            when (verified) {
                true -> anyVerified = true
                false -> anyExplicitlyRejected = true
                null -> anyTransientFailure = true
            }
        }

        when {
            anyVerified -> {
                // Genuine server confirmation. Stamp the verification
                // time so the bounded optimistic fallback below has a
                // basis to trust on a future transient outage.
                ProStatusManager.markVerified(context)
                grantOrRevokePro(true)
            }
            anyExplicitlyRejected && !anyTransientFailure -> {
                // Every purchase was rejected by Play's own verdict
                // (expired / cancelled / unknown token) → genuine
                // forgery or a lapsed subscription.
                grantOrRevokePro(false)
            }
            else -> {
                // Transient / indeterminate: the CF was unreachable or
                // could not determine entitlement. Bug #10 fix (b):
                // bound the optimism. We may only AFFIRM a Pro state that
                // was previously server-verified — never CREATE one. An
                // attacker who forges a local "PURCHASED" purchase and
                // blocks the verifyPurchase endpoint never earns a
                // successful verification, so lastVerifiedMs stays 0 and
                // they get nothing. A paying user with a flaky connection
                // keeps Pro for the grace window; the legit-but-first-
                // run-offline case self-heals on the next reachable
                // connect() / restorePurchases().
                val now = System.currentTimeMillis()
                val lastVerified = ProStatusManager.lastVerifiedMs(context)
                when {
                    lastVerified == 0L ->
                        // Never once server-verified on this install → no
                        // provisional Pro. This line is what actually
                        // closes the "block the CF endpoint" forgery.
                        grantOrRevokePro(false)
                    now - lastVerified <= VERIFICATION_GRACE_MS ->
                        // Previously verified, CF transiently unreachable,
                        // still inside the grace window → keep Pro.
                        grantOrRevokePro(true)
                    else ->
                        // Previously verified but the grace window lapsed
                        // without a single successful re-verification.
                        // Revoke; "Restore Purchases" recovers it
                        // instantly once connectivity is back, and the CF
                        // re-confirms a genuine sub on the next reachable
                        // call.
                        grantOrRevokePro(false)
                }
            }
        }
    }

    /**
     * Bug #10 helper: invokes the `verifyPurchase` Cloud Function for a
     * single (sku, token, type) tuple. Returns:
     *   - true: Play API confirmed the purchase is currently entitled.
     *   - false: Play API explicitly rejected (cancelled / expired /
     *     unknown token).
     *   - null: callable threw (network, auth, deploy gap). Caller
     *     treats null as "couldn't determine, don't change state".
     *
     * Wraps the callable in suspendCoroutine because the AndroidX
     * functions library uses Tasks API, not coroutines natively.
     */
    private suspend fun verifyOnePurchaseWithCloud(
        productId: String,
        purchaseToken: String,
        productType: String,
    ): Boolean? = suspendCoroutine { cont ->
        val data = hashMapOf(
            "productId" to productId,
            "purchaseToken" to purchaseToken,
            "productType" to productType,
        )
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("verifyPurchase")
            .call(data)
            .addOnSuccessListener { httpsResult ->
                @Suppress("UNCHECKED_CAST")
                val payload = httpsResult.getData() as? Map<String, Any?>
                if (payload == null) {
                    cont.resume(null)
                    return@addOnSuccessListener
                }
                when (payload["verified"] as? Boolean) {
                    true -> cont.resume(true)
                    // Field absent / malformed → indeterminate, not a
                    // rejection.
                    null -> cont.resume(null)
                    false -> {
                        // Bug #10 fix (a): only a `reason` that reflects
                        // Play's OWN verdict counts as a real rejection.
                        // server_misconfigured / play_api_error mean the
                        // CF couldn't check at all — treat as transient
                        // so a CF deployed without its secret doesn't
                        // revoke every paying customer.
                        val reason = payload["reason"] as? String
                        if (reason != null && reason !in INDETERMINATE_REASONS) {
                            cont.resume(false)
                        } else {
                            cont.resume(null)
                        }
                    }
                }
            }
            .addOnFailureListener { ex ->
                // Distinguish "callable returned a documented failure"
                // (which means we DID reach the CF and it ran) from
                // "couldn't reach CF at all". For the MVP both collapse
                // to null — caller falls back to optimistic grant.
                com.example.plantcare.CrashReporter.log(ex)
                cont.resume(null)
            }
    }

    /** Bug #10: SKU → CF productType mapping. SKU_LIFETIME is a
     *  one-time INAPP product; everything else is a SUBS. */
    private fun productTypeFor(sku: String): String = when (sku) {
        SKU_LIFETIME -> "inapp"
        else -> "subs"
    }

    /**
     * ZZ1: Re-read the cached Pro flag from ProStatusManager and push
     * it into the StateFlow. Used by the cloud-restore path in
     * MainActivity, which writes prefs directly via
     * `ProStatusManager.restoreFromCloud` to avoid a Firestore loop —
     * but that bypass also means subscribers of `isPro` never see the
     * updated value, so the AdManager observer keeps showing the
     * banner even after a Pro account is restored from cloud.
     */
    fun refreshFromLocal() {
        _isPro.value = ProStatusManager.isPro(context)
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
