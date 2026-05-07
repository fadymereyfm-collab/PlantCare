package com.example.plantcare.billing

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.example.plantcare.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Non-aggressive paywall dialog shown when the user tries to exceed the free plant limit.
 * Shows Monthly, Yearly and Lifetime options with a clear Close button.
 * Includes "Restore Purchases" for users who already subscribed.
 *
 * Display rule: do NOT show in first 7 days after install (enforced by caller).
 */
class PaywallDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "paywall_dialog"

        fun newInstance(contextMessage: String = ""): PaywallDialogFragment {
            val f = PaywallDialogFragment()
            f.arguments = Bundle().apply { putString(ARG_MESSAGE, contextMessage) }
            return f
        }

        private const val ARG_MESSAGE = "context_message"
    }

    private lateinit var billing: BillingManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_paywall, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        billing = BillingManager.getInstance(requireContext())

        // Context message (e.g. "Du brauchst mehr Platz für deine grüne Familie!")
        val contextMsg = arguments?.getString(ARG_MESSAGE) ?: ""
        val txtContext = view.findViewById<TextView>(R.id.txtPaywallContext)
        if (contextMsg.isNotBlank()) {
            txtContext.text = contextMsg
            txtContext.visibility = View.VISIBLE
        } else {
            txtContext.visibility = View.GONE
        }

        // Close button — always clearly visible
        view.findViewById<ImageButton>(R.id.btnPaywallClose).setOnClickListener {
            dismissAllowingStateLoss()
        }

        // Restore purchases
        view.findViewById<Button>(R.id.btnRestorePurchases).setOnClickListener {
            lifecycleScope.launch {
                try {
                    billing.restorePurchases()
                    // #6 fix: dialog could be dismissed mid-suspension;
                    // cancellation must propagate, and toast contexts
                    // must null-check rather than requireContext (which
                    // throws if the fragment detached during the await).
                    val ctx1 = context ?: return@launch
                    Toast.makeText(ctx1, R.string.paywall_restore_done, Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (e: Exception) {
                    com.example.plantcare.CrashReporter.log(e)
                    val ctx2 = context ?: return@launch
                    Toast.makeText(ctx2, R.string.paywall_restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Load product details and wire up purchase buttons
        lifecycleScope.launch {
            val products = billing.queryProducts()
            bindProducts(view, products)
        }

        // ZZ4: auto-dismiss when the purchase actually completes.
        // Pre-fix the user had to tap Close manually after seeing
        // Google Play's success sheet, which on top of the paywall
        // looks like the purchase didn't take. Drop the value
        // initially captured (false → no-op) so we only react to
        // the next change to true.
        var seenInitial = false
        lifecycleScope.launch {
            billing.isPro.collectLatest { pro ->
                if (!seenInitial) { seenInitial = true; return@collectLatest }
                if (pro) dismissAllowingStateLoss()
            }
        }
    }

    private fun bindProducts(view: View, products: List<ProductDetails>) {
        val monthly  = products.find { it.productId == BillingManager.SKU_MONTHLY }
        val yearly   = products.find { it.productId == BillingManager.SKU_YEARLY }
        val lifetime = products.find { it.productId == BillingManager.SKU_LIFETIME }

        bindButton(view, R.id.btnMonthly, R.id.txtMonthlyPrice, monthly,
            getString(R.string.paywall_monthly_label))

        bindButton(view, R.id.btnYearly, R.id.txtYearlyPrice, yearly,
            getString(R.string.paywall_yearly_label))

        bindButton(view, R.id.btnLifetime, R.id.txtLifetimePrice, lifetime,
            getString(R.string.paywall_lifetime_label))
    }

    private fun bindButton(
        root: View,
        btnId: Int,
        priceId: Int,
        product: ProductDetails?,
        fallbackLabel: String
    ) {
        val btn = root.findViewById<Button>(btnId)
        val priceTxt = root.findViewById<TextView>(priceId)

        if (product == null) {
            btn.isEnabled = false
            priceTxt.text = fallbackLabel
            return
        }

        val priceLabel = product.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
            ?: product.oneTimePurchaseOfferDetails?.formattedPrice
            ?: fallbackLabel

        priceTxt.text = priceLabel
        btn.isEnabled = true
        btn.setOnClickListener {
            activity?.let { act -> billing.launchPurchase(act, product) }
        }
    }

    override fun getTheme(): Int = R.style.PlantCare_Dialog
}
